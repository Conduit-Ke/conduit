package com.sms.edgenode.service

import android.content.Context
import android.os.Build
import androidx.work.*
import com.sms.edgenode.data.api.AuthService
import com.sms.edgenode.data.api.DeviceService
import com.sms.edgenode.data.db.EdgeNodeDatabase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class SelfHealingWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), KoinComponent {

    private val database: EdgeNodeDatabase by inject()
    private val authService: AuthService by inject()
    private val deviceService: DeviceService by inject()

    private val nodeId by lazy {
        android.provider.Settings.Secure.getString(
            applicationContext.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_node"
    }

    override suspend fun doWork(): Result {
        android.util.Log.i("SelfHealingWorker", "Starting self-healing cycle")

        return try {
            performHealing()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SelfHealingWorker", "Self-healing failed: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun performHealing() {
        val now = System.currentTimeMillis()

        // 1. Database Cleanup (Keep 30 days)
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)
        database.smsDao().deleteOldMessages(thirtyDaysAgo)
        database.auditLogDao().deleteOldLogs(thirtyDaysAgo)

        // 2. Lease Recovery (Release leases for this node that might be stuck)
        database.smsDao().releaseLeases(nodeId)

        // 3. Stale Message Recovery
        database.smsDao().requeueHeld()
        database.smsDao().recoverStuckReceived(now - 300_000) // 5 minutes

        // 4. Retry Rescue (Messages that failed recently due to transient errors)
        val failureThreshold = now - (2L * 60 * 60 * 1000)
        val requeued = database.smsDao().requeueRecentFailures(failureThreshold)
        if (requeued > 0) {
            android.util.Log.i("SelfHealingWorker", "Requeued $requeued failed/expired messages")
        }

        // 5. Promote Submitted (Radio didn't report back within 1 minute)
        database.smsDao().promoteSubmitted(now - 60_000L, now)

        // 6. Registration Integrity Check
        val orgId = authService.getOrganizationId()
        if (orgId != -1L) {
            if (deviceService.isRegistered()) {
                val verifyResult = deviceService.verify()
                if (verifyResult.isFailure) {
                    android.util.Log.w("SelfHealingWorker", "Session invalid, re-registering...")
                    val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                    deviceService.register(deviceName, orgId)
                }
            } else {
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                deviceService.register(deviceName, orgId)
            }
        }

        // 7. Ensure RoutingService is running if it should be
        if (authService.getAccessToken() != null) {
            RoutingService.start(applicationContext)
        }

        android.util.Log.i("SelfHealingWorker", "Self-healing cycle completed successfully")
    }

    companion object {
        private const val WORK_NAME = "edge_node_self_healing"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SelfHealingWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
        
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<SelfHealingWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
