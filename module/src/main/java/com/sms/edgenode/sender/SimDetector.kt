package com.sms.edgenode.sender

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

object SimDetector {
    fun getActiveSimSlots(context: Context): List<Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return listOf(0)
        }

        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val activeSubscriptions = subscriptionManager?.activeSubscriptionInfoList ?: return listOf(0)
            
            activeSubscriptions.map { it.simSlotIndex }.filter { it >= 0 }
        } catch (e: SecurityException) {
            listOf(0)
        }
    }

    fun getSimSlotForSubscription(context: Context, subscriptionId: Int): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return 0
        }

        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            subscriptionManager?.getActiveSubscriptionInfo(subscriptionId)?.simSlotIndex ?: 0
        } catch (e: SecurityException) {
            0
        }
    }

    fun getDefaultSmsSimSlot(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return 0
        }

        return try {
            val defaultSmsSubId = SubscriptionManager.getDefaultSmsSubscriptionId()
            getSimSlotForSubscription(context, defaultSmsSubId)
        } catch (e: Exception) {
            0
        }
    }
}
