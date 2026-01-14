package com.sms.edgenode.observability

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Device health monitor for:
 * - Connectivity status
 * - Airplane mode detection
 * - SIM status monitoring
 * - Battery level tracking
 * - Heartbeat with device stats
 */
class DeviceHealthMonitor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default)

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 60_000L // 1 minute
    }

    /**
     * Check if device has network connectivity.
     */
    fun hasConnectivity(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    /**
     * Check if airplane mode is enabled.
     */
    fun isAirplaneModeOn(): Boolean {
        return android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0
    }

    /**
     * Get status of all available SIM cards.
     */
    fun getSimStatuses(): List<SimStatus> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return emptyList()
        }

        return try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            
            subManager.activeSubscriptionInfoList?.map { info ->
                SimStatus(
                    slot = info.simSlotIndex,
                    carrierId = info.carrierId,
                    carrierName = info.carrierName?.toString() ?: "Unknown",
                    mcc = info.mccString ?: "",
                    mnc = info.mncString ?: "",
                    signalStrength = getSignalStrength(info.subscriptionId),
                    networkType = getNetworkTypeName(telephony.dataNetworkType)
                )
            } ?: emptyList()
        } catch (e: SecurityException) {
            android.util.Log.e("DeviceHealthMonitor", "Permission denied for SIM status: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get current battery level and charging status.
     */
    fun getBatteryStatus(): BatteryStatus {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        
        val percentage = if (scale > 0) (level * 100 / scale) else 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                         status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryStatus(percentage, isCharging)
    }

    /**
     * Collect full device health snapshot for heartbeat.
     */
    fun collectHealthSnapshot(): HealthSnapshot {
        return HealthSnapshot(
            timestamp = System.currentTimeMillis(),
            hasConnectivity = hasConnectivity(),
            airplaneModeOn = isAirplaneModeOn(),
            battery = getBatteryStatus(),
            simStatuses = getSimStatuses(),
            memoryUsage = getMemoryUsage(),
            uptimeMs = android.os.SystemClock.elapsedRealtime()
        )
    }

    /**
     * Start periodic heartbeat reporting.
     */
    fun startHeartbeat(onHeartbeat: suspend (HealthSnapshot) -> Unit) {
        scope.launch {
            while (isActive) {
                try {
                    val snapshot = collectHealthSnapshot()
                    onHeartbeat(snapshot)
                } catch (e: Exception) {
                    android.util.Log.e("DeviceHealthMonitor", "Heartbeat error: ${e.message}")
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun getSignalStrength(subscriptionId: Int): Int {
        // Signal strength requires API 28+ for proper access
        return -1 // Placeholder, actual implementation requires SignalStrength listener
    }

    private fun getNetworkTypeName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Unknown"
        else -> "Other ($type)"
    }

    private fun getMemoryUsage(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        return MemoryInfo(
            usedMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt(),
            totalMb = (runtime.totalMemory() / (1024 * 1024)).toInt(),
            maxMb = (runtime.maxMemory() / (1024 * 1024)).toInt()
        )
    }

    // Data classes
    data class SimStatus(
        val slot: Int,
        val carrierId: Int,
        val carrierName: String,
        val mcc: String,
        val mnc: String,
        val signalStrength: Int,
        val networkType: String
    )

    data class BatteryStatus(
        val percentage: Int,
        val isCharging: Boolean
    )

    data class MemoryInfo(
        val usedMb: Int,
        val totalMb: Int,
        val maxMb: Int
    )

    data class HealthSnapshot(
        val timestamp: Long,
        val hasConnectivity: Boolean,
        val airplaneModeOn: Boolean,
        val battery: BatteryStatus,
        val simStatuses: List<SimStatus>,
        val memoryUsage: MemoryInfo,
        val uptimeMs: Long
    )
}
