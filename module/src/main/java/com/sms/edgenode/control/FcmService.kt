package com.sms.edgenode.control

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sms.edgenode.EdgeNodeApp
import com.sms.edgenode.data.db.SmsEntity
import com.sms.edgenode.data.db.EdgeNodeDatabase
import com.sms.edgenode.data.db.RoutingRuleEntity
import com.sms.edgenode.data.db.SmsStatus
import com.sms.edgenode.security.SignatureVerifier
import com.sms.edgenode.security.PayloadEncryption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.sms.edgenode.service.SelfHealingWorker

class FcmService : FirebaseMessagingService() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val database: com.sms.edgenode.data.db.EdgeNodeDatabase by inject()
    private val authService: com.sms.edgenode.data.api.AuthService by inject()
    private val deviceService: com.sms.edgenode.data.api.DeviceService by inject()
    private val ussdManager: com.sms.edgenode.sender.UssdManager by inject()
    private val auditLogManager: com.sms.edgenode.data.db.AuditLogManager by inject()

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: return
        val rawPayload = data["payload"]
        if (rawPayload == null) {
            android.util.Log.e("FcmService", "Missing 'payload' key in FCM data")
            return
        }

        val payloadJson = try {
            org.json.JSONObject(rawPayload)
        } catch (e: Exception) {
            android.util.Log.e("FcmService", "Failed to parse payload JSON: ${e.message}")
            return
        }

        val signature = payloadJson.optString("sig", null)
        val timestamp = payloadJson.optString("ts", "")
        val encryptedPayload = payloadJson.optString("enc_payload", null)
        val idOrCommand = payloadJson.optString("server_message_id", payloadJson.optString("command", ""))
        
        android.util.Log.d("FcmService", "Received message: type=$type, id=$idOrCommand, encrypted=${encryptedPayload != null}")


        // 1. Verify Layered Security if signature present
        if (signature != null && encryptedPayload != null) {
            val secret = authService.getControlSecurityKey()
            if (secret == null) {
                android.util.Log.e("FcmService", "Security verification failed: Control secret is NULL")
                return
            }
            
            // HMAC over Envelope (Type + ID/Command + Timestamp + Ciphertext)
            val signaturePayload = type + idOrCommand + timestamp + encryptedPayload
            
            if (!SignatureVerifier.verify(signaturePayload, signature, secret)) {
                android.util.Log.e("FcmService", "Security verification failed: Invalid HMAC signature for $type")
                scope.launch { logSecurityEvent("Invalid layered signature for $type") }
                return
            }

            android.util.Log.i("FcmService", "Security verification passed for $type")

            // Decrypt payload
            val decrypted = PayloadEncryption.decrypt(encryptedPayload)
            if (decrypted == null) {
                android.util.Log.e("FcmService", "Decryption failed for $type. Check alignment of encryption format/keys.")
                return
            }
            
            android.util.Log.d("FcmService", "Decryption successful: $decrypted")
            val innerData = org.json.JSONObject(decrypted)
            
            // Map decrypted keys back to a mutable map for existing handlers
            val fullData = data.toMutableMap()
            
            // Inject extracted metadata so handlers can access server_message_id, command, ts, etc.
            fullData["server_message_id"] = idOrCommand
            fullData["command"] = idOrCommand
            fullData["timestamp"] = timestamp
            
            innerData.keys().forEach { key ->
                fullData[key] = innerData.optString(key)
            }

            when (type) {
                "SEND_SMS" -> handleSendSms(fullData)
                "CONTROL_COMMAND" -> handleControlCommand(fullData)
                "UPDATE_RULES" -> handleRuleUpdate(fullData)
                "CONFIG_UPDATE" -> handleConfigUpdate(fullData)
            }
        } else {
            android.util.Log.w("FcmService", "Message skipped: signature=${signature != null}, payload=${encryptedPayload != null}")
        }
    }

    private fun handleConfigUpdate(data: Map<String, String>) {
        android.util.Log.i("FcmService", "Received CONFIG_UPDATE signal")
        scope.launch {
            // Trigger self-healing which will re-verify registration and pull updates
            SelfHealingWorker.runOnce(applicationContext)
            
            // Log the event
            auditLogManager.logEvent("CONFIG_UPDATE_SIGNAL", null, "Triggered via FCM")
        }
    }

    override fun onNewToken(token: String) {
        scope.launch {
            deviceService.syncFcmToken(token)
            android.util.Log.i("FcmService", "FCM Token refreshed and synced: $token")
        }
    }

    private fun handleRuleUpdate(data: Map<String, String>) {
        scope.launch {
            val ruleId = data["ruleId"] ?: return@launch
            val version = data["version"]?.toIntOrNull() ?: return@launch
            val ruleJson = data["ruleJson"] ?: return@launch
            val signature = data["signature"] ?: return@launch

            val secret = authService.getControlSecurityKey()
            if (secret == null || !SignatureVerifier.verify(ruleJson, signature, secret)) {
                logSecurityEvent("Invalid or missing rule signature")
                com.sms.edgenode.observability.CrashReporter.logError("Invalid rule signature for rule $ruleId")
                return@launch
            }

            database.routingRuleDao().deactivateAll()
            database.routingRuleDao().insert(
                RoutingRuleEntity(
                    id = ruleId,
                    version = version,
                    ruleJson = ruleJson,
                    signature = signature,
                    activatedAt = System.currentTimeMillis(),
                    isActive = true
                )
            )
            database.smsDao().requeueHeld()

            com.sms.edgenode.observability.CrashReporter.logEvent("RULE_UPDATED", mapOf("ruleId" to ruleId, "version" to version.toString()))
            database.auditLogDao().insert(
                com.sms.edgenode.data.db.AuditLogEntity(
                    timestamp = System.currentTimeMillis(),
                    eventType = "RULE_UPDATED",
                    smsId = null,
                    details = "Rule: $ruleId v$version"
                )
            )
        }
    }

    private fun handleControlCommand(data: Map<String, String>) {
        scope.launch {
            val command = data["command"] ?: return@launch
            val signature = data["signature"]
            val payload = data["payload"]
            
            // Verify command signature if provided
            if (signature != null && payload != null) {
                val secret = authService.getControlSecurityKey()
                if (secret == null || !SignatureVerifier.verify(payload, signature, secret)) {
                    logSecurityEvent("Invalid or missing command signature for: $command")
                    return@launch
                }
            }
            
            when (command) {
                "SET_BACKPRESSURE" -> {
                    val ratio = data["ratio"]?.toDoubleOrNull() ?: 1.0
                    handleBackpressure(ratio)
                }
                "KILL_SWITCH" -> {
                    val scope = data["scope"] ?: "GLOBAL" // GLOBAL, SIM, RULE, SENDER
                    val targetId = data["target_id"]
                    handleKillSwitch(scope, targetId)
                }
                "RESUME" -> {
                    handleResume()
                }
                "TOPUP" -> {
                    val voucher = data["voucher"] ?: ""
                    val format = authService.getUssdTopupCode()
                    val ussdCode = format.replace("CARD", voucher)
                    android.util.Log.i("FcmService", "Executing Remote Top-up: $ussdCode")
                    ussdManager.sendUssd(ussdCode, authService.getPreferredSimSlot())
                }
                "PURCHASE_BUNDLE" -> {
                    val code = authService.getUssdBundlePurchaseCode()
                    android.util.Log.i("FcmService", "Executing Remote Bundle Purchase: $code")
                    ussdManager.sendUssd(code, authService.getPreferredSimSlot())
                }
            }
            
            logSecurityEvent("Control command executed: $command")
        }
    }

    private suspend fun handleBackpressure(ratio: Double) {
        // Store backpressure ratio in preferences for RateLimiter to use
        val prefs = applicationContext.getSharedPreferences("control_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putFloat("backpressure_ratio", ratio.toFloat()).apply()
        android.util.Log.w("FcmService", "Backpressure set to $ratio")
        
        auditLogManager.logEvent("BACKPRESSURE_SET", null, "Ratio: $ratio")
    }

    private suspend fun handleKillSwitch(scope: String, targetId: String?) {
        val prefs = applicationContext.getSharedPreferences("control_prefs", android.content.Context.MODE_PRIVATE)
        
        when (scope) {
            "GLOBAL" -> {
                prefs.edit().putBoolean("global_kill_switch", true).apply()
                android.util.Log.e("FcmService", "GLOBAL KILL SWITCH ACTIVATED")
                com.sms.edgenode.service.RoutingService.stop(applicationContext)
            }
            "SIM" -> {
                val disabledSims = prefs.getStringSet("disabled_sims", mutableSetOf()) ?: mutableSetOf()
                targetId?.let { disabledSims.add(it) }
                prefs.edit().putStringSet("disabled_sims", disabledSims).apply()
                android.util.Log.w("FcmService", "SIM $targetId disabled via kill switch")
            }
            "RULE" -> {
                val disabledRules = prefs.getStringSet("disabled_rules", mutableSetOf()) ?: mutableSetOf()
                targetId?.let { disabledRules.add(it) }
                prefs.edit().putStringSet("disabled_rules", disabledRules).apply()
                android.util.Log.w("FcmService", "Rule $targetId disabled via kill switch")
            }
            "SENDER" -> {
                val blockedSenders = prefs.getStringSet("blocked_senders", mutableSetOf()) ?: mutableSetOf()
                targetId?.let { blockedSenders.add(it) }
                prefs.edit().putStringSet("blocked_senders", blockedSenders).apply()
                android.util.Log.w("FcmService", "Sender $targetId blocked via kill switch")
            }
        }
        
        auditLogManager.logEvent("KILL_SWITCH", null, "Scope: $scope, Target: $targetId")
    }

    private suspend fun handleResume() {
        val prefs = applicationContext.getSharedPreferences("control_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("global_kill_switch", false)
            .putFloat("backpressure_ratio", 1.0f)
            .apply()
        android.util.Log.i("FcmService", "Service resumed via FCM command")
        
        auditLogManager.logEvent("SERVICE_RESUMED", null, "Via FCM command")
    }

    /**
     * Handle direct SMS injection via FCM Push-to-Queue.
     */
    private fun handleSendSms(data: Map<String, String>) {
        scope.launch {
            val destination = data["destination"]
            val message = data["message"]
            
            if (destination == null || message == null) {
                android.util.Log.e("FcmService", "SMS Injection failed: Missing destination ($destination) or message ($message). Keys present: ${data.keys}")
                return@launch
            }
            
            android.util.Log.i("FcmService", "Injecting SMS: to $destination (Length: ${message.length})")
            
            val serverMessageId = data["server_message_id"] ?: java.util.UUID.randomUUID().toString()
            val priority = data["priority"]?.toIntOrNull() ?: 0
            val ttlMs = data["ttl_ms"]?.toLongOrNull()
            
            // Content fingerprint for deduplication
            val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
                .digest("$destination|$message".toByteArray())
                .joinToString("") { "%02x".format(it) }
            
            val simSlot = data["sim_slot"]?.toIntOrNull() ?: authService.getPreferredSimSlot()
            
            val entity = SmsEntity(
                serverMessageId = serverMessageId,
                contentFingerprint = fingerprint,
                sender = "FCM_INJECTION",
                body = message,
                receivedAt = System.currentTimeMillis(),
                simSlot = simSlot,
                status = SmsStatus.QUEUED,
                forwardTarget = destination,
                priority = priority,
                ttlMs = ttlMs
            )
            
            database.smsDao().insert(entity)
            auditLogManager.logEvent("SMS_INJECTED", null, "Dest: $destination, MsgId: $serverMessageId")
            
            // Trigger service to process immediately
            com.sms.edgenode.service.QueueSignal.poke()
            com.sms.edgenode.service.RoutingService.start(applicationContext)
        }
    }

    /**
     * Handle encrypted FCM payloads for sensitive control data.
     */
    private fun handleEncryptedPayload(data: Map<String, String>) {
        scope.launch {
            val encryptedPayload = data["encrypted_payload"] ?: return@launch
            val timestamp = data["timestamp"]?.toLongOrNull() ?: return@launch
            
            // Check timestamp window (5 minute max)
            val now = System.currentTimeMillis()
            if (kotlin.math.abs(now - timestamp) > 5 * 60 * 1000) {
                logSecurityEvent("Encrypted payload timestamp expired: $timestamp vs $now")
                return@launch
            }
            
            try {
                val decrypted = PayloadEncryption.decrypt(encryptedPayload)
                if (decrypted != null) {
                    // Parse decrypted JSON and re-route
                    val innerData = org.json.JSONObject(decrypted)
                    val innerType = innerData.optString("type")
                    val innerMap = mutableMapOf<String, String>()
                    innerData.keys().forEach { key ->
                        innerMap[key] = innerData.optString(key)
                    }
                    
                    when (innerType) {
                        "UPDATE_RULES" -> handleRuleUpdate(innerMap)
                        "CONTROL_COMMAND" -> handleControlCommand(innerMap)
                        "SEND_SMS" -> handleSendSms(innerMap)
                    }
                }
            } catch (e: Exception) {
                logSecurityEvent("Failed to decrypt payload: ${e.message}")
            }
        }
    }

    private suspend fun logSecurityEvent(message: String) {
        auditLogManager.logEvent("SECURITY_EVENT", null, message)
    }
}
