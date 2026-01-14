package com.sms.edgenode.sender

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages SIM health scoring and quarantine logic.
 * Tracks success/failure rates and applies tiered penalties.
 */
class SimHealthManager(private val context: Context) {
    private val mutex = Mutex()
    private val simHealth = mutableMapOf<Int, SimHealthState>()

    companion object {
        private const val SUCCESS_THRESHOLD = 0.80 // 80% success rate
        private const val CONSECUTIVE_FAILURE_THRESHOLD = 3
        private val QUARANTINE_TIERS = listOf(
            10L * 60 * 1000,  // 10 minutes
            30L * 60 * 1000,  // 30 minutes
            2L * 60 * 60 * 1000, // 2 hours
            24L * 60 * 60 * 1000 // 24 hours
        )
    }

    data class SimHealthState(
        var status: SimStatus = SimStatus.HEALTHY,
        var successCount: Int = 0,
        var failureCount: Int = 0,
        var consecutiveFailures: Int = 0,
        var lastFailureAt: Long = 0,
        var quarantineLevel: Int = 0,
        var quarantineUntil: Long = 0
    )

    enum class SimStatus {
        HEALTHY,
        DEGRADED,
        QUARANTINED
    }

    suspend fun getStatus(simSlot: Int): SimStatus = mutex.withLock {
        val state = simHealth.getOrPut(simSlot) { SimHealthState() }
        
        // Check if quarantine has expired
        if (state.status == SimStatus.QUARANTINED && System.currentTimeMillis() >= state.quarantineUntil) {
            state.status = SimStatus.DEGRADED // Move to degraded for re-testing
            android.util.Log.i("SimHealthManager", "SIM $simSlot quarantine expired, now DEGRADED")
        }
        
        return@withLock state.status
    }

    suspend fun recordSuccess(simSlot: Int) = mutex.withLock {
        val state = simHealth.getOrPut(simSlot) { SimHealthState() }
        state.successCount++
        state.consecutiveFailures = 0
        
        // If was degraded and now has 5 consecutive successes, promote back to healthy
        if (state.status == SimStatus.DEGRADED && state.successCount >= 5) {
            state.status = SimStatus.HEALTHY
            state.quarantineLevel = 0
            android.util.Log.i("SimHealthManager", "SIM $simSlot promoted to HEALTHY")
        }
    }

    suspend fun recordFailure(simSlot: Int) = mutex.withLock {
        val state = simHealth.getOrPut(simSlot) { SimHealthState() }
        val now = System.currentTimeMillis()
        
        state.failureCount++
        state.consecutiveFailures++
        state.lastFailureAt = now
        state.successCount = 0 // Reset success streak

        // Check for quarantine trigger
        if (state.consecutiveFailures >= CONSECUTIVE_FAILURE_THRESHOLD) {
            val tier = minOf(state.quarantineLevel, QUARANTINE_TIERS.size - 1)
            val duration = QUARANTINE_TIERS[tier]
            
            state.status = SimStatus.QUARANTINED
            state.quarantineUntil = now + duration
            state.quarantineLevel++
            
            android.util.Log.w("SimHealthManager", "SIM $simSlot QUARANTINED for ${duration / 60000} minutes (Tier $tier)")
        } else if (getSuccessRate(state) < SUCCESS_THRESHOLD) {
            state.status = SimStatus.DEGRADED
            android.util.Log.w("SimHealthManager", "SIM $simSlot DEGRADED due to low success rate")
        } else {
            // State unchanged
        }
    }

    private fun getSuccessRate(state: SimHealthState): Double {
        val total = state.successCount + state.failureCount
        return if (total > 0) state.successCount.toDouble() / total else 1.0
    }

    suspend fun getBestSim(preferredSlot: Int): Int = mutex.withLock {
        
        // If preferred SIM is healthy, use it
        val preferredState = simHealth[preferredSlot]
        if (preferredState == null || preferredState.status == SimStatus.HEALTHY) {
            return@withLock preferredSlot
        }
        
        // Otherwise, find first non-quarantined SIM
        for ((slot, state) in simHealth) {
            if (state.status != SimStatus.QUARANTINED && slot != preferredSlot) {
                android.util.Log.i("SimHealthManager", "Failing over from SIM $preferredSlot to SIM $slot")
                return@withLock slot
            }
        }
        
        // No alternatives, return preferred even if degraded
        return@withLock preferredSlot
    }

    fun getCarrierInfo(simSlot: Int): CarrierInfo? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP_MR1) return null
        
        return try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subInfo = subManager.activeSubscriptionInfoList?.find { it.simSlotIndex == simSlot }
            
            subInfo?.let {
                val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                CarrierInfo(
                    mcc = it.mccString ?: "",
                    mnc = it.mncString ?: "",
                    carrierName = it.carrierName?.toString() ?: "Unknown",
                    networkType = telephony.dataNetworkType
                )
            }
        } catch (e: SecurityException) {
            null
        }
    }

    data class CarrierInfo(
        val mcc: String,
        val mnc: String,
        val carrierName: String,
        val networkType: Int
    ) {
        fun getNetworkTypeName(): String = when (networkType) {
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            else -> "Unknown ($networkType)"
        }
    }
}
