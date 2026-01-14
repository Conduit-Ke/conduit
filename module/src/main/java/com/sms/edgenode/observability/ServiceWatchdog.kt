package com.sms.edgenode.observability

import android.app.ActivityManager
import android.content.Context
import androidx.work.*
import com.sms.edgenode.service.RoutingService
import java.util.concurrent.TimeUnit

class ServiceWatchdog(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!isServiceRunning()) {
            RoutingService.start(applicationContext)
            logWatchdogEvent("Service restarted by watchdog")
        }
        return Result.success()
    }

    private fun isServiceRunning(): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == RoutingService::class.java.name
        }
    }

    private suspend fun logWatchdogEvent(message: String) {
        val app = applicationContext as com.sms.edgenode.EdgeNodeApp
        app.database.auditLogDao().insert(
            com.sms.edgenode.data.db.AuditLogEntity(
                timestamp = System.currentTimeMillis(),
                eventType = "WATCHDOG",
                smsId = null,
                details = message
            )
        )
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceWatchdog>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "service_watchdog",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
