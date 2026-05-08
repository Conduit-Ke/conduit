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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.sms.edgenode.data.db.EdgeNodeDatabase
import com.sms.edgenode.data.api.SmsApiService

class SmsStatusReceiver : BroadcastReceiver(), KoinComponent {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val database: EdgeNodeDatabase by inject()
    private val smsApiService: SmsApiService by inject()
    private val notificationHelper: com.sms.edgenode.observability.NotificationHelper by inject()
    private val authService: com.sms.edgenode.data.api.AuthService by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val smsId = intent.getLongExtra("smsId", -1L)
        android.util.Log.d("SmsStatusReceiver", "onReceive: action=${intent.action}, smsId=$smsId, resultCode=$resultCode")
        
        if (smsId == -1L) return

        when (intent.action) {
            SmsManager.ACTION_SMS_SENT -> handleSentStatus(context, smsId, intent, database, smsApiService)
            SmsManager.ACTION_SMS_DELIVERED -> handleDeliveredStatus(context, smsId, database, smsApiService)
        }
    }

    private fun handleSentStatus(
        context: android.content.Context,
        smsId: Long, 
        intent: android.content.Intent,
        database: com.sms.edgenode.data.db.EdgeNodeDatabase,
        smsApiService: com.sms.edgenode.data.api.SmsApiService
    ) {
        val success = resultCode == android.app.Activity.RESULT_OK
        
        // Extract RIL error code if provided by radio (Samsung specific)
        @Suppress("DEPRECATION")
        val errorCode = intent.getIntExtra("errorCode", -1).let {
            if (it == -1) intent.getIntExtra("error_code", -1) else it
        }

        // Define retryable error codes (IMS/Credit issues)
        val isRetryableError = when (resultCode) {
            android.telephony.SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> true
            17, 32895, 32837 -> true // Samsung RIL/IMS errors
            android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE -> true
            else -> false
        }

        android.util.Log.d("SmsStatusReceiver", "Processing SENT status: smsId=$smsId, success=$success, resultCode=$resultCode, errorCode=$errorCode, retryable=$isRetryableError")

        scope.launch {
            val sms = database.smsDao().getById(smsId) ?: run {
                android.util.Log.e("SmsStatusReceiver", "SMS $smsId not found in DB")
                return@launch
            }

            val maxRetries = 5
            val shouldRetry = !success && isRetryableError && sms.attempts < maxRetries
            
            val status = when {
                success -> SmsStatus.SENT
                shouldRetry -> SmsStatus.QUEUED
                else -> SmsStatus.FAILED
            }

            val failureReason = if (!success) getFailureReason(resultCode, errorCode) else null
            
            // Calculate backoff if retrying (5 mins, 10 mins, 15 mins...)
            val backoffMs = if (shouldRetry) (sms.attempts + 1) * 300_000L else null
            val nextRetry = if (backoffMs != null) System.currentTimeMillis() + backoffMs else null

            android.util.Log.i("SmsStatusReceiver", "SMS $smsId -> $status (Attempts: ${sms.attempts + 1}/$maxRetries, NextRetry: $nextRetry, Reason: $failureReason)")

            // 1. Update DB Status
            database.smsDao().updateStatus(
                id = smsId,
                status = status,
                attempts = sms.attempts + 1,
                timestamp = System.currentTimeMillis(),
                nextRetry = nextRetry,
                reason = failureReason
            )

            // 2. Resolve Carrier and USSD codes
            if (shouldRetry) {
                val subscriptionManager = context.getSystemService(android.content.Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
                val info = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                        subscriptionManager.activeSubscriptionInfoList?.find { it.simSlotIndex == sms.simSlot }
                    } else null
                } catch (e: Exception) { null }

                val carrierName = info?.displayName?.toString() ?: "SIM ${sms.simSlot}"
                val operator = info?.let { "${it.mccString}${it.mncString}" } ?: ""

                // Map operator to USSD codes (Safaricom, Airtel Kenya, etc.)
                val (balanceUssd, topupUssd) = when (operator) {
                    "63902" -> "*144#" to "*544#" // Safaricom
                    "63910", "63911" -> "*131#" to "*544#" // Airtel KE
                    else -> null to null
                }

                notificationHelper.showSimAlert(
                    slot = sms.simSlot,
                    carrier = carrierName,
                    reason = failureReason ?: "Radio Error",
                    balanceUssd = balanceUssd,
                    topupUssd = topupUssd
                )
                
                // 3. Proactive USSD Check if enabled
                if (authService.isUssdAutoCheckEnabled()) {
                    android.util.Log.i("SmsStatusReceiver", "Triggering proactive USSD balance check for $carrierName")
                }
            }

            // 4. Batch status report ensuring test script sees the error
            val apiStatus = if (!success && isRetryableError) SmsStatus.FAILED else status
            database.pendingStatusDao().insert(
                com.sms.edgenode.data.db.PendingStatusEntity(
                    smsId = smsId,
                    serverMessageId = sms.serverMessageId,
                    phoneNumber = sms.forwardTarget ?: sms.sender,
                    status = apiStatus,
                    error = failureReason,
                    timestamp = System.currentTimeMillis(),
                    simSlot = sms.simSlot,
                    carrier = null,
                    message = sms.body,
                    correlationId = sms.correlationId
                )
            )
            
            com.sms.edgenode.service.QueueSignal.poke()
        }
    }

    private fun handleDeliveredStatus(
        context: android.content.Context,
        smsId: Long, 
        database: com.sms.edgenode.data.db.EdgeNodeDatabase,
        smsApiService: com.sms.edgenode.data.api.SmsApiService
    ) {
        android.util.Log.i("SmsStatusReceiver", "SMS $smsId delivered")
        
        scope.launch {
            val now = System.currentTimeMillis()
            database.smsDao().markDelivered(smsId, now)
            
            val sms = database.smsDao().getById(smsId) ?: return@launch
            database.pendingStatusDao().insert(
                com.sms.edgenode.data.db.PendingStatusEntity(
                    smsId = smsId,
                    serverMessageId = sms.serverMessageId,
                    phoneNumber = sms.forwardTarget ?: sms.sender,
                    status = SmsStatus.DELIVERED,
                    error = null,
                    timestamp = now,
                    simSlot = sms.simSlot,
                    carrier = null,
                    message = sms.body,
                    correlationId = sms.correlationId
                )
            )
            
            com.sms.edgenode.service.QueueSignal.poke()
        }
    }

    private fun getFailureReason(resultCode: Int, errorCode: Int): String {
        val baseReason = when (resultCode) {
            AndroidSmsManager.RESULT_ERROR_GENERIC_FAILURE -> "GENERIC_FAILURE (1)"
            AndroidSmsManager.RESULT_ERROR_RADIO_OFF -> "RADIO_OFF (2)"
            AndroidSmsManager.RESULT_ERROR_NULL_PDU -> "NULL_PDU (3)"
            AndroidSmsManager.RESULT_ERROR_NO_SERVICE -> "NO_SERVICE (4)"
            AndroidSmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "LIMIT_EXCEEDED (5)"
            AndroidSmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "FDN_CHECK_FAILURE (6)"
            17 -> "NETWORK_UNAVAILABLE (17)"
            18 -> "NETWORK_REJECTED (18)"
            32895 -> "IMS_FAILURE (32895)" // Samsung specific
            32837 -> "IMS_REGISTRATION_ERROR (32837)"
            else -> "RADIO_ERROR ($resultCode)"
        }
        
        return if (errorCode != -1) "$baseReason [RIL: $errorCode]" else baseReason
    }
}
