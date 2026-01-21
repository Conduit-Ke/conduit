package com.sms.edgenode.sender

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manager for handling USSD requests (Balance checks, Top-ups).
 * Requires Android 8.0 (API 26) or higher.
 */
class UssdManager(private val context: Context) {

    /**
     * Sends a USSD request and returns the response string.
     */
    suspend fun sendUssd(code: String, slot: Int): Result<String> = suspendCancellableCoroutine { continuation ->
        val tm = getTelephonyManagerForSlot(slot)
        if (tm == null) {
            continuation.resume(Result.failure(Exception("SIM card not found in slot $slot")))
            return@suspendCancellableCoroutine
        }

        try {
            tm.sendUssdRequest(code, object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    telephonyManager: TelephonyManager?,
                    request: String?,
                    response: CharSequence?
                ) {
                    android.util.Log.d("UssdManager", "USSD Response: $response")
                    continuation.resume(Result.success(response?.toString() ?: "Empty response"))
                }

                override fun onReceiveUssdResponseFailed(
                    telephonyManager: TelephonyManager?,
                    request: String?,
                    failureCode: Int
                ) {
                    val error = when (failureCode) {
                        TelephonyManager.USSD_RETURN_FAILURE -> "General Failure (Check code format or signal)"
                        TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "Service Unavailable"
                        else -> "Carrier Error (code: $failureCode)"
                    }
                    android.util.Log.e("UssdManager", "USSD Failed for '$request': $error")
                    continuation.resume(Result.failure(Exception(error)))
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            continuation.resume(Result.failure(e))
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    private fun getTelephonyManagerForSlot(slot: Int): TelephonyManager? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        
        return try {
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList ?: return null
            val subInfo = activeSubscriptions.find { it.simSlotIndex == slot } ?: return null
            tm.createForSubscriptionId(subInfo.subscriptionId)
        } catch (e: Exception) {
            null
        }
    }
}
