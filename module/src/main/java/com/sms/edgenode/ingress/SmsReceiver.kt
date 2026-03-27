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
import com.sms.edgenode.data.api.AuthService
import com.sms.edgenode.data.api.DeviceService
import com.sms.edgenode.data.db.EdgeNodeDatabase
import com.sms.edgenode.sender.UssdManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.MessageDigest

class SmsReceiver : BroadcastReceiver(), KoinComponent {
    private val db: EdgeNodeDatabase by inject()
    private val authService: AuthService by inject()
    private val deviceService: DeviceService by inject()
    private val ussdManager: UssdManager by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            // Concatenate multi-part messages
            val sender = messages[0].originatingAddress ?: "UNKNOWN"
            val body = messages.joinToString("") { it.messageBody ?: "" }
            val timestamp = messages[0].timestampMillis
            
            persistSms(context, sender, body, timestamp)
        }

        // Only start the service if user hasn't explicitly disabled it
        if (!authService.isServiceDisabledByUser()) {
            RoutingService.start(context)
        }
    }

    private suspend fun persistSms(context: Context, sender: String, body: String, timestamp: Long) {
        val hash = computeHash(sender, body, timestamp)
        val simSlot = com.sms.edgenode.sender.SimDetector.getDefaultSmsSimSlot(context)
        
        val isCarrier = isCarrierMessage(sender, body)
        
        val entity = com.sms.edgenode.data.db.SmsEntity(
            contentFingerprint = hash,
            serverMessageId = java.util.UUID.randomUUID().toString(),
            sender = sender,
            body = body,
            receivedAt = System.currentTimeMillis(),
            simSlot = simSlot,
            status = if (isCarrier) com.sms.edgenode.data.db.SmsStatus.SYSTEM else com.sms.edgenode.data.db.SmsStatus.RECEIVED
        )

        val entityId = db.smsDao().insert(entity)
        android.util.Log.i("SmsReceiver", "Persisted SMS $entityId | Sender: $sender | Status: ${entity.status} | Body: ${body.take(20)}...")
        
        db.auditLogDao().insert(
            com.sms.edgenode.data.db.AuditLogEntity(
                timestamp = System.currentTimeMillis(),
                eventType = if (isCarrier) "SYSTEM_METRIC" else "SMS_RECEIVED",
                smsId = entityId,
                details = if (isCarrier) "Carrier Update (SIM $simSlot): $body" else "From: $sender"
            )
        )
        
        if (isCarrier && authService.isUssdAutoCheckEnabled()) {
            handleCarrierLogic(body, simSlot)
        }
    }

    private suspend fun handleCarrierLogic(body: String, slot: Int) {
        val lowercaseBody = body.lowercase()
        
        // 1. Parse Balance (KES) - Refined regex to avoid loan/debt messages
        val kesRegex = Regex("""(?:bal|balance|new balance|acc bal|account balance)\s*(?:is|:)?\s*(?:kes|shillings)?\s*(\d+(?:\.\d+)?)""")
        val match = kesRegex.find(lowercaseBody)
        if (match != null) {
            val balance = match.groupValues[1].toDoubleOrNull()
            if (balance != null) {
                deviceService.reportCarrierMetric("AIRTIME_BALANCE", balance.toString())
                
                // Trigger Refill if below threshold and not recently requested (20 min cool-off)
                val threshold = authService.getRefillThreshold()
                val lastRequest = authService.getLastRefillRequestTime()
                val now = System.currentTimeMillis()
                val coolOffMs = 20 * 60 * 1000L // 20 minutes
                
                if (balance < threshold && (now - lastRequest) > coolOffMs) {
                    android.util.Log.w("SmsReceiver", "Low balance detected ($balance < $threshold). Triggering refill request...")
                    
                    // Mark request time immediately to prevent race conditions
                    authService.setLastRefillRequestTime(now)
                    
                    val result = deviceService.requestRefill(threshold)
                    if (result.isFailure) {
                        val error = result.exceptionOrNull()?.message ?: "Unknown Error"
                        val isInsufficient = error.contains("402")
                        
                        // If it fails with something other than insufficient funds (e.g. timeout), 
                        // we reset the cool-off so it can try again on the next SMS
                        if (!isInsufficient) {
                            authService.setLastRefillRequestTime(0L)
                        }

                        db.auditLogDao().insert(
                            com.sms.edgenode.data.db.AuditLogEntity(
                                timestamp = System.currentTimeMillis(),
                                eventType = "REFILL_ERROR",
                                smsId = null,
                                details = if (isInsufficient) 
                                    "Refill Blocked: Organization backend balance is empty. Deposit float to resume autonomous top-ups."
                                    else "Autonomous refill failed: $error"
                            )
                        )
                    }
                } else if (balance < threshold) {
                    android.util.Log.d("SmsReceiver", "Balance $balance is below threshold $threshold, but cool-off is active. Skipping request.")
                }
            }
        }

        // 2. Detect Recharge Confirmation & Trigger Auto-Bundle
        val rechargeKeywords = listOf("received", "recharged", "topup", "recharge of", "credit of")
        if (rechargeKeywords.any { lowercaseBody.contains(it) } && authService.isAutoBundleEnabled()) {
            val bundleCode = authService.getUssdBundlePurchaseCode()
            android.util.Log.i("SmsReceiver", "Recharge detected. Waiting 3s for propagation before auto-bundle purchase: $bundleCode")
            
            // Wait for airtime to propagate at the carrier side
            kotlinx.coroutines.delay(3000)
            
            ussdManager.sendUssd(bundleCode, slot)
        }
    }

    private fun isCarrierMessage(sender: String, body: String): Boolean {
        val upperSender = sender.uppercase()
        
        // M-PESA and MPESA are transactional, not maintenance/carrier system updates
        if (upperSender.contains("MPESA") || upperSender.contains("M-PESA")) return false
        
        // Alphanumeric senders (e.g., Safaricom) are almost always carrier/system
        val isAlphanumeric = sender.any { it.isLetter() }
        // Common balance/bundle keywords
        val keywords = listOf("balance", "bundle", "shillings", "kes", "expiry", "units", "mb")
        val hasKeywords = keywords.any { body.lowercase().contains(it) }
        
        return (isAlphanumeric || hasKeywords || sender.length <= 5)
    }

    private fun computeHash(sender: String, body: String, timestamp: Long): String {
        val input = "$sender|$body|$timestamp"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
