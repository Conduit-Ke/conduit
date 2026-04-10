package com.sms.edgenode.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sms.edgenode.domain.RoutingEngine
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject

class RoutingService : Service() {
    private val database: com.sms.edgenode.data.db.EdgeNodeDatabase by inject()
    private val authService: com.sms.edgenode.data.api.AuthService by inject()
    private val deviceService: com.sms.edgenode.data.api.DeviceService by inject()
    private val smsManager: com.sms.edgenode.sender.SmsManager by inject()
    private val simHealthManager: com.sms.edgenode.sender.SimHealthManager by inject()
    private val smsApiService: com.sms.edgenode.data.api.SmsApiService by inject()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val processingJobs = mutableMapOf<Int, Job>()
    private var syncJob: Job? = null

    private val nodeId by lazy {
        android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_node"
    }

    override fun onCreate() {
        super.onCreate()
        
        if (!hasRequiredPermissions()) {
            com.sms.edgenode.observability.CrashReporter.logError("Missing permissions, service cannot start")
            stopSelf()
            return
        }

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("RoutingService", "onStartCommand triggered")
        scope.launch {
            // Perform database cleanup (keep last 30 days)
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            database.smsDao().deleteOldMessages(thirtyDaysAgo)
            database.auditLogDao().deleteOldLogs(thirtyDaysAgo)

            // Auto-register or re-activate device if we have organizationId
            val orgId = authService.getOrganizationId()
            android.util.Log.d("RoutingService", "Current organizationId: $orgId")
            
            if (orgId != -1) {
                restoreDeviceRegistration(orgId)
            } else {
                android.util.Log.w("RoutingService", "No organizationId found, skipping restoration")
            }

            // Recovery: Release any leases belonging to this node (e.g. after crash)
            database.smsDao().releaseLeases(nodeId)
            
            // Requeue HELD messages and recover stale RECEIVED messages
            database.smsDao().requeueHeld()
            database.smsDao().recoverStuckReceived(System.currentTimeMillis() - 300000)

            // Requeue recent failures (last 2 hours) to rescue messages from registration errors
            val failureThreshold = System.currentTimeMillis() - (2L * 60 * 60 * 1000)
            val requeued = database.smsDao().requeueRecentFailures(failureThreshold)
            if (requeued > 0) {
                android.util.Log.i("RoutingService", "Requeued $requeued failed/expired messages for retry")
            }
            
            // Promote stale SUBMITTED messages
            val threshold = System.currentTimeMillis() - 60_000L
            database.smsDao().promoteSubmitted(threshold, System.currentTimeMillis())
        }
        
        if (syncJob?.isActive != true) {
            syncJob = scope.launch { statusSyncLoop() }
        }

        startProcessingLoops()
        
        return START_NOT_STICKY
    }

    private fun startProcessingLoops() {
        val preferredSlot = authService.getPreferredSimSlot()
        val smartFailover = authService.isSmartFailoverEnabled()
        
        // Slot -1 means "Default/Any". 
        // We force -1 if it's currently 0 to ensure we use the system default (often free) as requested.
        val effectiveSlot = if (preferredSlot == 0) {
            android.util.Log.i("RoutingService", "Defaulting SIM 0 to -1 for Universal Routing")
            -1
        } else {
            preferredSlot
        }

        val slotsToStart = listOf(effectiveSlot)

        for (slot in slotsToStart) {
            if (processingJobs[slot]?.isActive != true) {
                android.util.Log.d("RoutingService", "Starting processQueue loop for SIM slot $slot")
                processingJobs[slot] = scope.launch { processQueue(slot) }
            }
        }

        for (slot in slotsToStart) {
            if (processingJobs[slot]?.isActive != true) {
                android.util.Log.d("RoutingService", "Starting processQueue loop for SIM slot $slot")
                processingJobs[slot] = scope.launch { processQueue(slot) }
            }
        }
    }

    private suspend fun restoreDeviceRegistration(orgId: Int) {
        var isRegisteredLocally = deviceService.isRegistered()
        var hasToken = deviceService.getDeviceToken() != null
        android.util.Log.d("RoutingService", "Restoration check: isRegisteredLocally=$isRegisteredLocally, hasToken=$hasToken")
        
        // If we have a token, verify it before proceeding
        if (isRegisteredLocally && hasToken) {
            val verifyResult = deviceService.verify()
            if (verifyResult.isFailure) {
                val error = verifyResult.exceptionOrNull()
                if (error?.message?.contains("401") == true || error?.message?.contains("token") == true) {
                    android.util.Log.w("RoutingService", "Saved token is invalid. Clearing for re-registration.")
                    deviceService.clearToken()
                    hasToken = false // Force re-registration below
                }
            }
        }

        if (!isRegisteredLocally || !hasToken) {
            android.util.Log.i("RoutingService", "Device session missing or invalid. Restoring...")
            
            // Fallback to full registration
            val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            android.util.Log.d("RoutingService", "Performing full registration for: $deviceName")
            deviceService.register(deviceName, orgId).onFailure { e ->
                android.util.Log.e("RoutingService", "Full device registration failed: ${e.message}")
            }
        } else {
            android.util.Log.d("RoutingService", "Device already registered and token verified")
        }
    }

    private suspend fun processQueue(simSlot: Int) = withContext(Dispatchers.Default) {
        val rateLimiter = com.sms.edgenode.sender.RateLimiter(database, applicationContext)
        val engine = RoutingEngine(
            database = database, 
            smsManager = smsManager, 
            rateLimiter = rateLimiter, 
            simHealthManager = simHealthManager,
            authService = authService, 
            smsApiService = smsApiService,
            nodeId = nodeId,
            overrideSimSlot = simSlot
        )
        // Override engine's SIM selection to anchor it to this loop's slot
        // Or keep it dynamic if we want the engine to still be "smart".
        // For now, let's keep it dynamic but allow the engine to know which loop it is in.

        var isRemoteManaged = false
        engine.onRemoteManagement = { code -> 
            // ... (keep existing implementation)
            android.util.Log.e("RoutingService", "Remote management action: $code")
            isRemoteManaged = true
            val message = if (code == 404) "Device deleted from dashboard." else "Device deactivated from dashboard."
            fireSecurityNotification(message)
            deviceService.clearToken()
            stopSelf()
        }

        var lastDiagAt = 0L

        while (isActive && !isRemoteManaged) {
            try {
                val processed = engine.processNext()
                if (!processed) {
                    val now = System.currentTimeMillis()
                    if (now - lastDiagAt > 30_000L) {
                        lastDiagAt = now
                        logQueueSummary()
                    }
                    
                    // Reactive wait: delay for up to 5s, but wake up immediately if signaled
                    withTimeoutOrNull(5000) {
                        com.sms.edgenode.service.QueueSignal.signal.collect {
                            // Signal received, break timeout and continue loop
                            return@collect
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // ... (keep existing error handling)
                com.sms.edgenode.observability.CrashReporter.logError("Processing error", e)
                android.util.Log.e("RoutingService", "processNext error: ${e.message}", e)
                database.auditLogDao().insert(
                    com.sms.edgenode.data.db.AuditLogEntity(
                        timestamp = System.currentTimeMillis(),
                        eventType = "PROCESSING_ERROR",
                        smsId = null,
                        details = e.message ?: "Unknown error"
                    )
                )
                delay(10000)
            }
        }
    }

    private suspend fun logQueueSummary() {
        try {
            val summary = database.smsDao().getStatusSummary()
            android.util.Log.d("RoutingService", "Queue status: $summary")
            val stuck = database.smsDao().getStuckMessages()
            if (stuck.isNotEmpty()) {
                android.util.Log.w("RoutingService", "Non-terminal messages: $stuck")
            }
        } catch (e: Exception) {
            android.util.Log.e("RoutingService", "Diag query failed: ${e.message}")
        }
    }

    private suspend fun statusSyncLoop() {
        android.util.Log.i("RoutingService", "Starting statusSyncLoop")
        while (scope.isActive) {
            try {
                syncPendingStatus(database, smsApiService)
                // Wait for more work or 2 seconds
                withTimeoutOrNull(2000) {
                    com.sms.edgenode.service.QueueSignal.signal.collect { }
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                android.util.Log.e("RoutingService", "statusSyncLoop error: ${e.message}")
                delay(10000)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel =
                NotificationChannel(CHANNEL_ID, "SMS Routing", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SMS Edge Node")
                .setContentText("Routing service active")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
    }

    private fun fireSecurityNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Alert")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(1002, notification)
    }

    override fun onDestroy() {
        android.util.Log.i("RoutingService", "Service stopping. Releasing leases for $nodeId")
        scope.launch(Dispatchers.IO) {
            database.smsDao().releaseLeases(nodeId)
            processingJobs.values.forEach { it.cancel() }
            syncJob?.cancel()
            scope.cancel()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun syncPendingStatus(database: com.sms.edgenode.data.db.EdgeNodeDatabase, smsApiService: com.sms.edgenode.data.api.SmsApiService) {
        val batch = database.pendingStatusDao().getNextBatch(50)
        if (batch.isEmpty()) return

        android.util.Log.d("RoutingService", "Syncing ${batch.size} status updates to API")
        
        val updates = batch.map {
            com.sms.edgenode.data.api.SmsApiService.StatusUpdate(
                phone_number = it.phoneNumber,
                message = it.message ?: "",
                status = it.status.name,
                error = it.error,
                request_id = it.serverMessageId,
                timestamp = it.timestamp,
                sim_slot = it.simSlot,
                carrier = it.carrier
            )
        }

        val result = smsApiService.submitStatusBatch(updates)
        if (result.isSuccess) {
            database.pendingStatusDao().deleteBatch(batch.map { it.id })
        } else {
            android.util.Log.w("RoutingService", "Batch status sync failed, will retry")
            kotlinx.coroutines.delay(5000)
        }
    }

    companion object {
        private const val CHANNEL_ID = "routing_service"
        private const val NOTIFICATION_ID = 1001

        private val REQUIRED_PERMISSIONS = mutableListOf(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.CALL_PHONE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        fun start(context: Context) {
            val allGranted = REQUIRED_PERMISSIONS.all {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                context.startForegroundService(Intent(context, RoutingService::class.java))
            } else {
                android.util.Log.e("RoutingService", "Not starting: missing permissions")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RoutingService::class.java))
        }
    }
}
