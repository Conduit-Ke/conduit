package com.sms.edgenode.ingress

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sms.edgenode.service.RoutingService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            RoutingService.start(context)
        }
    }
}
