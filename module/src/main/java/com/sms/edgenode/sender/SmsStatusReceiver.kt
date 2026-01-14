package com.sms.edgenode.sender

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager as AndroidSmsManager
import com.sms.edgenode.EdgeNodeApp
import com.sms.edgenode.data.db.SmsStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsStatusReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val smsId = intent.getLongExtra("smsId", -1L)
        if (smsId == -1L) return

        val app = context.applicationContext as EdgeNodeApp
        val database = app.database
        val smsApiService = app.smsApiService

        when (intent.action) {
            SmsManager.ACTION_SMS_SENT -> handleSentStatus(smsId, database, smsApiService)
            SmsManager.ACTION_SMS_DELIVERED -> handleDeliveredStatus(smsId, database, smsApiService)
        }
    }

    private fun handleSentStatus(
        smsId: Long, 
        database: com.sms.edgenode.data.db.EdgeNodeDatabase,
        smsApiService: com.sms.edgenode.data.api.SmsApiService
    ) {
        val success = resultCode == Activity.RESULT_OK
        val status = if (success) SmsStatus.SENT else SmsStatus.FAILED
        val failureReason = if (!success) getFailureReason(resultCode) else null

        scope.launch {
            // 1. Update DB
            database.smsDao().updateStatus(
                id = smsId,
                status = status,
                attempts = 0, // In this context we don't increment here
                timestamp = System.currentTimeMillis(),
                nextRetry = null,
                reason = failureReason
            )

            // 2. Report to Server
            val sms = database.smsDao().getById(smsId) ?: return@launch
            smsApiService.submitStatus(
                phoneNumber = sms.sender, // The 'sender' field in SmsEntity stores the destination for outgoing SMS
                message = sms.body,
                status = status,
                error = failureReason,
                requestId = sms.serverMessageId,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    private fun handleDeliveredStatus(
        smsId: Long, 
        database: com.sms.edgenode.data.db.EdgeNodeDatabase,
        smsApiService: com.sms.edgenode.data.api.SmsApiService
    ) {
        scope.launch {
            val now = System.currentTimeMillis()
            database.smsDao().markDelivered(smsId, now)
            
            // Report DELIVERED status to server
            val sms = database.smsDao().getById(smsId) ?: return@launch
            smsApiService.submitStatus(
                phoneNumber = sms.sender,
                message = sms.body,
                status = SmsStatus.DELIVERED,
                requestId = sms.serverMessageId,
                timestamp = now
            )
        }
    }

    private fun getFailureReason(resultCode: Int): String {
        return when (resultCode) {
            AndroidSmsManager.RESULT_ERROR_GENERIC_FAILURE -> "GENERIC_FAILURE"
            AndroidSmsManager.RESULT_ERROR_RADIO_OFF -> "RADIO_OFF"
            AndroidSmsManager.RESULT_ERROR_NULL_PDU -> "NULL_PDU"
            AndroidSmsManager.RESULT_ERROR_NO_SERVICE -> "NO_SERVICE"
            AndroidSmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "LIMIT_EXCEEDED"
            AndroidSmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "FDN_CHECK_FAILURE"
            else -> "UNKNOWN_ERROR ($resultCode)"
        }
    }
}
