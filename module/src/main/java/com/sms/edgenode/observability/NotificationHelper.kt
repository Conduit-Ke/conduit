package com.sms.edgenode.observability

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sms.edgenode.R

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "sim_alerts"
        const val CHANNEL_NAME = "SIM Status & Billing Alerts"
        const val SIM_ALERT_ID_OFFSET = 1000
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for SIM balance, radio errors, and connectivity"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showSimAlert(
        slot: Int, 
        carrier: String, 
        reason: String,
        balanceUssd: String? = null,
        topupUssd: String? = null
    ) {
        val title = "$carrier Balance Alert"
        val message = "SMS delivery failed: $reason. Action required on SIM $slot."
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        // Open main app when clicked
        val mainIntent = android.content.Intent(context, com.sms.edgenode.ui.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = android.app.PendingIntent.getActivity(
            context, slot, mainIntent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(mainPendingIntent)

        // Add 'Check Balance' shortcut
        balanceUssd?.let { ussd ->
            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                data = android.net.Uri.parse("tel:${ussd.replace("#", "%23")}")
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, slot * 10, intent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_call, "Check Balance", pendingIntent)
        }

        // Add 'Buy Bundles' shortcut
        topupUssd?.let { ussd ->
            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                data = android.net.Uri.parse("tel:${ussd.replace("#", "%23")}")
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, slot * 10 + 1, intent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_help, "Buy Bundles", pendingIntent)
        }

        notificationManager.notify(SIM_ALERT_ID_OFFSET + slot, builder.build())
        android.util.Log.w("NotificationHelper", "Posted interactive SIM alert for slot $slot: $reason (USSD: $balanceUssd / $topupUssd)")
    }

    fun clearSimAlert(slot: Int) {
        notificationManager.cancel(SIM_ALERT_ID_OFFSET + slot)
    }
}
