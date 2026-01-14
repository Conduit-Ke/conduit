package com.sms.edgenode.compliance

import java.util.Calendar

/**
 * Compliance profiles for carrier-safe messaging:
 * - Time-window TPS (different rates for peak/off-peak)
 * - Blackout periods (no sending during certain hours)
 * - Quiet hours enforcement
 */
class ComplianceProfile {

    companion object {
        // Default blackout periods (carrier typically sensitive 10pm-8am)
        private val DEFAULT_BLACKOUT_START = 22 // 10 PM
        private val DEFAULT_BLACKOUT_END = 8    // 8 AM

        // TPS by time of day
        private const val PEAK_TPS = 1      // Conservative during peak
        private const val OFFPEAK_TPS = 2   // Slightly higher off-peak
        private const val NIGHT_TPS = 0     // No sending during blackout
    }

    /**
     * Check if current time is within blackout period.
     */
    fun isBlackoutPeriod(
        blackoutStart: Int = DEFAULT_BLACKOUT_START,
        blackoutEnd: Int = DEFAULT_BLACKOUT_END
    ): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        
        return if (blackoutStart < blackoutEnd) {
            hour in blackoutStart until blackoutEnd
        } else {
            // Wraps around midnight (e.g., 22:00 - 08:00)
            hour >= blackoutStart || hour < blackoutEnd
        }
    }

    /**
     * Get allowed TPS based on current time of day.
     */
    fun getCurrentTps(): Int {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        
        return when {
            isBlackoutPeriod() -> NIGHT_TPS
            hour in 9..17 -> PEAK_TPS          // Business hours
            hour in 18..21 -> OFFPEAK_TPS      // Evening
            hour in 7..8 -> OFFPEAK_TPS        // Morning
            else -> NIGHT_TPS                   // Night
        }
    }

    /**
     * Check if day is a weekend (often lower limits).
     */
    fun isWeekend(): Boolean {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY
    }

    /**
     * Check if current date is a holiday (requires external calendar).
     */
    fun isHoliday(): Boolean {
        // Placeholder: Would integrate with a holiday calendar API
        return false
    }

    /**
     * Get daily quota adjusted for day type.
     */
    fun getAdjustedDailyQuota(baseQuota: Int): Int {
        return when {
            isHoliday() -> (baseQuota * 0.25).toInt() // 25% on holidays
            isWeekend() -> (baseQuota * 0.50).toInt() // 50% on weekends
            else -> baseQuota                          // Full on weekdays
        }
    }

    /**
     * Generate compliance status report.
     */
    fun getComplianceStatus(): ComplianceStatus {
        val now = Calendar.getInstance()
        
        return ComplianceStatus(
            currentHour = now.get(Calendar.HOUR_OF_DAY),
            isBlackout = isBlackoutPeriod(),
            isWeekend = isWeekend(),
            isHoliday = isHoliday(),
            allowedTps = getCurrentTps(),
            nextWindowChange = getNextWindowChangeTime()
        )
    }

    private fun getNextWindowChangeTime(): Long {
        val cal = Calendar.getInstance()
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)

        // Find next transition point
        val transitionHours = listOf(7, 9, 17, 18, 22).sorted()
        val nextHour = transitionHours.firstOrNull { it > currentHour } ?: transitionHours.first()
        
        cal.set(Calendar.HOUR_OF_DAY, nextHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        if (nextHour <= currentHour) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return cal.timeInMillis
    }

    data class ComplianceStatus(
        val currentHour: Int,
        val isBlackout: Boolean,
        val isWeekend: Boolean,
        val isHoliday: Boolean,
        val allowedTps: Int,
        val nextWindowChange: Long
    )
}
