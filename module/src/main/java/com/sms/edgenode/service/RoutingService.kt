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

class RoutingService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var processingJob: Job? = null

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
            val app = applicationContext as com.sms.edgenode.EdgeNodeApp
            
            // Perform database cleanup (keep last 30 days)
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            app.database.smsDao().deleteOldMessages(thirtyDaysAgo)
            app.database.auditLogDao().deleteOldLogs(thirtyDaysAgo)

            // Auto-register or re-activate device if we have organizationId
            val orgId = app.authService.getOrganizationId()
            android.util.Log.d("RoutingService", "Current organizationId: $orgId")
            
            if (orgId != -1) {
                restoreDeviceRegistration(app, orgId)
            } else {
                android.util.Log.w("RoutingService", "No organizationId found, skipping restoration")
            }

            // Recovery: Release any leases belonging to this node (e.g. after crash)
            app.database.smsDao().releaseLeases(nodeId)
            
            // Requeue HELD messages and recover stale RECEIVED messages
            app.database.smsDao().requeueHeld()
            app.database.smsDao().recoverStuckReceived(System.currentTimeMillis() - 300000)
        }
        
        if (processingJob?.isActive != true) {
            android.util.Log.d("RoutingService", "Starting processQueue loop")
            processingJob = scope.launch { processQueue() }
        }
        return START_STICKY
    }

    private suspend fun restoreDeviceRegistration(app: com.sms.edgenode.EdgeNodeApp, orgId: Int) {
        val isRegisteredLocally = app.deviceService.isRegistered()
        val hasToken = app.deviceService.getDeviceToken() != null
        android.util.Log.d("RoutingService", "Restoration check: isRegisteredLocally=$isRegisteredLocally, hasToken=$hasToken")
        
        if (!isRegisteredLocally || !hasToken) {
            android.util.Log.i("RoutingService", "Device session missing. Restoring...")
            
            // Try to restore session if we have a local identifier
            if (isRegisteredLocally) {
                val verifyResult = app.deviceService.verify()
                if (verifyResult.isSuccess) {
                    android.util.Log.i("RoutingService", "Device session successfully restored via verify")
                    return // Session restored, done
                } else {
                    val error = verifyResult.exceptionOrNull()
                    android.util.Log.w("RoutingService", "Session restoration via verify failed: ${error?.message}")
                    if (error?.message?.contains("403") == true) {
                        fireSecurityNotification("Organization mismatch detected. Please login again.")
                        app.authService.logout()
                        stopSelf()
                        return
                    }
                }
            }
            
            // Fallback to full registration if restoration failed or never registered
            val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            android.util.Log.d("RoutingService", "Performing full registration for: $deviceName")
            app.deviceService.register(deviceName, orgId).onFailure { e ->
                android.util.Log.e("RoutingService", "Full device registration failed: ${e.message}")
            }
        } else {
            android.util.Log.d("RoutingService", "Device already registered and token present")
        }
    }

    private suspend fun processQueue() = withContext(Dispatchers.Default) {
        val app = applicationContext as com.sms.edgenode.EdgeNodeApp
        val rateLimiter = com.sms.edgenode.sender.RateLimiter(app.database, applicationContext)
        val engine = RoutingEngine(
            database = app.database, 
            smsManager = app.smsManager, 
            rateLimiter = rateLimiter, 
            simHealthManager = app.simHealthManager,
            authService = app.authService, 
            smsApiService = app.smsApiService,
            nodeId = nodeId
        )
        var isRemoteManaged = false
        
        engine.onRemoteManagement = { code ->
            android.util.Log.e("RoutingService", "Remote management action: $code")
            isRemoteManaged = true
            val message = if (code == 404) "Device deleted from dashboard." else "Device deactivated from dashboard."
            fireSecurityNotification(message)
            app.deviceService.clear()
            stopSelf()
        }

        while (isActive && !isRemoteManaged) {
            try {
                val processed = engine.processNext()
                if (!processed) {
                    delay(5000)
                }
            } catch (e: Exception) {
                com.sms.edgenode.observability.CrashReporter.logError("Processing error", e)
                app.database.auditLogDao().insert(
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
            val app = applicationContext as com.sms.edgenode.EdgeNodeApp
            app.database.smsDao().releaseLeases(nodeId)
            processingJob?.cancel()
            scope.cancel()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
