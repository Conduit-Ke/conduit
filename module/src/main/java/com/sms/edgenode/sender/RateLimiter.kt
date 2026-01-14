package com.sms.edgenode.sender

import android.content.Context
import com.sms.edgenode.data.db.EdgeNodeDatabase
import com.sms.edgenode.data.db.RateLimitEntity
import java.util.Calendar

/**
 * Enterprise-grade rate limiter with:
 * - 1-second TPS window per SIM
 * - Daily/Monthly quotas per SIM
 * - Server-initiated backpressure support
 */
class RateLimiter(
    private val database: EdgeNodeDatabase,
    private val context: Context? = null
) {
    companion object {
        private const val TPS_WINDOW_MS = 1_000L // 1 second
        private const val DEFAULT_TPS = 1 // 1 SMS per second per SIM (conservative)
        private const val DEFAULT_DAILY_QUOTA = 5000
        private const val DEFAULT_MONTHLY_QUOTA = 100000
    }

    suspend fun checkAndUpdate(simSlot: Int): Boolean {
        val now = System.currentTimeMillis()
        
        // Check backpressure ratio
        val backpressureRatio = getBackpressureRatio()
        if (backpressureRatio <= 0) return false

        val current = database.rateLimitDao().get(simSlot)

        if (current == null) {
            database.rateLimitDao().upsert(
                RateLimitEntity(
                    simSlot = simSlot,
                    lastSendAt = now,
                    sendCount = 1,
                    windowStartAt = now,
                    dailyCount = 1,
                    monthlyCount = 1,
                    lastDailyResetAt = now,
                    lastMonthlyResetAt = now
                )
            )
            return true
        }

        // Reset daily/monthly counters if needed
        val updated = resetQuotasIfNeeded(current, now)

        // Check daily quota
        if (updated.dailyCount >= DEFAULT_DAILY_QUOTA) {
            android.util.Log.w("RateLimiter", "SIM $simSlot daily quota exceeded")
            return false
        }

        // Check monthly quota
        if (updated.monthlyCount >= DEFAULT_MONTHLY_QUOTA) {
            android.util.Log.w("RateLimiter", "SIM $simSlot monthly quota exceeded")
            return false
        }

        // Check TPS (1-second window)
        val windowAge = now - updated.windowStartAt
        val effectiveTps = (DEFAULT_TPS * backpressureRatio).toInt().coerceAtLeast(1)
        
        if (windowAge < TPS_WINDOW_MS) {
            if (updated.sendCount >= effectiveTps) {
                return false // TPS exceeded
            }
            // Within window, increment
            database.rateLimitDao().upsert(
                updated.copy(
                    lastSendAt = now,
                    sendCount = updated.sendCount + 1,
                    dailyCount = updated.dailyCount + 1,
                    monthlyCount = updated.monthlyCount + 1
                )
            )
        } else {
            // New window
            database.rateLimitDao().upsert(
                updated.copy(
                    lastSendAt = now,
                    sendCount = 1,
                    windowStartAt = now,
                    dailyCount = updated.dailyCount + 1,
                    monthlyCount = updated.monthlyCount + 1
                )
            )
        }

        return true
    }

    private fun resetQuotasIfNeeded(entity: RateLimitEntity, now: Long): RateLimitEntity {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now

        val lastDailyCal = Calendar.getInstance().apply { timeInMillis = entity.lastDailyResetAt }
        val lastMonthlyCal = Calendar.getInstance().apply { timeInMillis = entity.lastMonthlyResetAt }

        var updated = entity

        // Reset daily if day changed
        if (cal.get(Calendar.DAY_OF_YEAR) != lastDailyCal.get(Calendar.DAY_OF_YEAR) ||
            cal.get(Calendar.YEAR) != lastDailyCal.get(Calendar.YEAR)) {
            updated = updated.copy(dailyCount = 0, lastDailyResetAt = now)
        }

        // Reset monthly if month changed
        if (cal.get(Calendar.MONTH) != lastMonthlyCal.get(Calendar.MONTH) ||
            cal.get(Calendar.YEAR) != lastMonthlyCal.get(Calendar.YEAR)) {
            updated = updated.copy(monthlyCount = 0, lastMonthlyResetAt = now)
        }

        return updated
    }

    private fun getBackpressureRatio(): Double {
        val prefs = context?.getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
        return prefs?.getFloat("backpressure_ratio", 1.0f)?.toDouble() ?: 1.0
    }

    suspend fun getQuotaStatus(simSlot: Int): QuotaStatus? {
        val entity = database.rateLimitDao().get(simSlot) ?: return null
        return QuotaStatus(
            dailyUsed = entity.dailyCount,
            dailyLimit = DEFAULT_DAILY_QUOTA,
            monthlyUsed = entity.monthlyCount,
            monthlyLimit = DEFAULT_MONTHLY_QUOTA
        )
    }

    data class QuotaStatus(
        val dailyUsed: Int,
        val dailyLimit: Int,
        val monthlyUsed: Int,
        val monthlyLimit: Int
    )
}
