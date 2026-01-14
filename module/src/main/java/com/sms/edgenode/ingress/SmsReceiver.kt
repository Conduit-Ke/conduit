package com.sms.edgenode.ingress

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.sms.edgenode.service.RoutingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            messages.forEach { msg ->
                persistSms(context, msg)
            }
        }

        RoutingService.start(context)
    }

    private suspend fun persistSms(context: Context, msg: SmsMessage) {
        val db = (context.applicationContext as com.sms.edgenode.EdgeNodeApp).database
        val hash = computeHash(msg.originatingAddress ?: "", msg.messageBody ?: "", msg.timestampMillis)
        val simSlot = com.sms.edgenode.sender.SimDetector.getDefaultSmsSimSlot(context)
        
        val entity = com.sms.edgenode.data.db.SmsEntity(
            contentFingerprint = hash,
            serverMessageId = java.util.UUID.randomUUID().toString(), // Temp ID until server confirmation
            sender = msg.originatingAddress ?: "UNKNOWN",
            body = msg.messageBody ?: "",
            receivedAt = System.currentTimeMillis(),
            simSlot = simSlot,
            status = com.sms.edgenode.data.db.SmsStatus.RECEIVED
        )

        db.smsDao().insert(entity)
        db.auditLogDao().insert(
            com.sms.edgenode.data.db.AuditLogEntity(
                timestamp = System.currentTimeMillis(),
                eventType = "SMS_RECEIVED",
                smsId = null,
                details = "From: ${entity.sender}"
            )
        )
    }

    private fun computeHash(sender: String, body: String, timestamp: Long): String {
        val input = "$sender|$body|$timestamp"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
