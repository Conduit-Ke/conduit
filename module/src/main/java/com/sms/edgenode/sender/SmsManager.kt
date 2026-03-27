package com.sms.edgenode.sender

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager as AndroidSmsManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SmsManager(private val context: Context) {
    @Suppress("DEPRECATION")
    private val defaultSmsManager = AndroidSmsManager.getDefault()

    /**
     * Submits an SMS for sending.
     * Returns true if the message was accepted by the system.
     * Detailed status (SENT, DELIVERED) is handled by SmsStatusReceiver.
     */
    fun sendSms(
        destination: String, 
        message: String, 
        smsId: Long, 
        preferredSlot: Int = -1
    ): Boolean {
        if (isPremiumNumber(destination)) return false

        val manager = if (preferredSlot != -1) {
            val sm = getSmsManagerForSlot(preferredSlot)
            android.util.Log.d("SmsManager", "Using explicit manager for slot $preferredSlot (Found: ${sm != null})")
            sm ?: defaultSmsManager
        } else {
            android.util.Log.d("SmsManager", "Using default SMS manager")
            defaultSmsManager
        }

        val sentIntent = PendingIntent.getBroadcast(
            context,
            smsId.toInt(),
            Intent(ACTION_SMS_SENT).apply {
                putExtra("smsId", smsId)
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deliveryIntent = PendingIntent.getBroadcast(
            context,
            smsId.toInt(),
            Intent(ACTION_SMS_DELIVERED).apply {
                putExtra("smsId", smsId)
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        android.util.Log.i("SmsManager", "Submitting SMS $smsId to ${destination.takeLast(4)} (Length: ${message.length})")

        return try {
            manager.sendTextMessage(destination, null, message, sentIntent, deliveryIntent)
            true
        } catch (e: Exception) {
            android.util.Log.e("SmsManager", "Submission failed for $smsId: ${e.message}", e)
            false
        }
    }

    private fun getSmsManagerForSlot(slot: Int): AndroidSmsManager? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP_MR1) return null
        
        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList ?: return null
            val subInfo = activeSubscriptions.find { it.simSlotIndex == slot } ?: return null
            
            @Suppress("DEPRECATION")
            AndroidSmsManager.getSmsManagerForSubscriptionId(subInfo.subscriptionId)
        } catch (e: Exception) {
            null
        }
    }

    private fun isPremiumNumber(number: String): Boolean {
        val premiumPrefixes = listOf("900", "901", "902")
        return premiumPrefixes.any { number.startsWith(it) }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.sms.edgenode.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.sms.edgenode.SMS_DELIVERED"
    }
}
