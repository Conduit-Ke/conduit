package com.sms.edgenode.observability

import android.app.ActivityManager
import android.content.Context
import androidx.work.*
import com.sms.edgenode.service.RoutingService
import com.sms.edgenode.data.db.EdgeNodeDatabase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class ServiceWatchdog(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val database: EdgeNodeDatabase by inject()
    private val authService: com.sms.edgenode.data.api.AuthService by inject()

    override suspend fun doWork(): Result {
        // Respect user's explicit intent to disable the service
        if (authService.isServiceDisabledByUser()) {
            android.util.Log.d("ServiceWatchdog", "Service disabled by user, skipping restart")
            return Result.success()
        }

        if (!isServiceRunning()) {
            RoutingService.start(applicationContext)
            logWatchdogEvent("Service restarted by watchdog")
        }
        return Result.success()
    }

    private fun isServiceRunning(): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == RoutingService::class.java.name
        }
    }

    private suspend fun logWatchdogEvent(message: String) {
        database.auditLogDao().insert(
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
