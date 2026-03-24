package com.sms.edgenode.control

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sms.edgenode.EdgeNodeApp
import com.sms.edgenode.data.db.RoutingRuleEntity
import com.sms.edgenode.data.db.SmsEntity
import com.sms.edgenode.data.db.SmsStatus
import com.sms.edgenode.security.SignatureVerifier
import com.sms.edgenode.security.PayloadEncryption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: return
        val signature = data["signature"]
        val timestamp = data["timestamp"] ?: ""
        val encryptedPayload = data["encrypted_payload"]
        val nonce = data["nonce"]

        // 1. Verify Layered Security if signature present
        if (signature != null && encryptedPayload != null) {
            val app = applicationContext as EdgeNodeApp
            val secret = app.authService.getControlSecurityKey() ?: return
            
            // HMAC over Envelope (Type + ID/Command + Timestamp + Ciphertext)
            val idOrCommand = data["server_message_id"] ?: data["command"] ?: ""
            val signaturePayload = type + idOrCommand + timestamp + encryptedPayload
            
            if (!SignatureVerifier.verify(signaturePayload, signature, secret)) {
                scope.launch { logSecurityEvent("Invalid layered signature for $type") }
                return
            }

            // Decrypt Inner Payload
            val decrypted = PayloadEncryption.decrypt(encryptedPayload, nonce ?: "") ?: return
            val innerData = org.json.JSONObject(decrypted)
            
            // Map decrypted keys back to a mutable map for existing handlers
            val fullData = data.toMutableMap()
            innerData.keys().forEach { key ->
                fullData[key] = innerData.optString(key)
            }

            when (type) {
                "SEND_SMS" -> handleSendSms(fullData)
                "CONTROL_COMMAND" -> handleControlCommand(fullData)
                "UPDATE_RULES" -> handleRuleUpdate(fullData)
            }
        } 
    }

    override fun onNewToken(token: String) {
        scope.launch {
            val app = applicationContext as EdgeNodeApp
            app.deviceService.syncFcmToken(token)
            android.util.Log.i("FcmService", "FCM Token refreshed and synced: $token")
        }
    }

    private fun handleRuleUpdate(data: Map<String, String>) {
        scope.launch {
            val ruleId = data["ruleId"] ?: return@launch
            val version = data["version"]?.toIntOrNull() ?: return@launch
            val ruleJson = data["ruleJson"] ?: return@launch
            val signature = data["signature"] ?: return@launch

            val app = applicationContext as EdgeNodeApp
            val secret = app.authService.getControlSecurityKey()
            if (secret == null || !SignatureVerifier.verify(ruleJson, signature, secret)) {
                logSecurityEvent("Invalid or missing rule signature")
                com.sms.edgenode.observability.CrashReporter.logError("Invalid rule signature for rule $ruleId")
                return@launch
            }

            val db = (applicationContext as EdgeNodeApp).database
            db.routingRuleDao().deactivateAll()
            db.routingRuleDao().insert(
                RoutingRuleEntity(
                    id = ruleId,
                    version = version,
                    ruleJson = ruleJson,
                    signature = signature,
                    activatedAt = System.currentTimeMillis(),
                    isActive = true
                )
            )
            db.smsDao().requeueHeld()

            com.sms.edgenode.observability.CrashReporter.logEvent("RULE_UPDATED", mapOf("ruleId" to ruleId, "version" to version.toString()))
            db.auditLogDao().insert(
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
                val app = applicationContext as EdgeNodeApp
                val secret = app.authService.getControlSecurityKey()
                if (secret == null || !SignatureVerifier.verify(payload, signature, secret)) {
                    logSecurityEvent("Invalid or missing command signature for: $command")
                    return@launch
                }
            }
            
            val app = applicationContext as EdgeNodeApp
            
            when (command) {
                "SET_BACKPRESSURE" -> {
                    val ratio = data["ratio"]?.toDoubleOrNull() ?: 1.0
                    handleBackpressure(app, ratio)
                }
                "KILL_SWITCH" -> {
                    val scope = data["scope"] ?: "GLOBAL" // GLOBAL, SIM, RULE, SENDER
                    val targetId = data["target_id"]
                    handleKillSwitch(app, scope, targetId)
                }
                "RESUME" -> {
                    handleResume(app)
                }
                "TOPUP" -> {
                    val voucher = data["voucher"] ?: ""
                    val format = app.authService.getUssdTopupCode()
                    val ussdCode = format.replace("CARD", voucher)
                    android.util.Log.i("FcmService", "Executing Remote Top-up: $ussdCode")
                    app.ussdManager.sendUssd(ussdCode, app.authService.getPreferredSimSlot())
                }
                "PURCHASE_BUNDLE" -> {
                    val code = app.authService.getUssdBundlePurchaseCode()
                    android.util.Log.i("FcmService", "Executing Remote Bundle Purchase: $code")
                    app.ussdManager.sendUssd(code, app.authService.getPreferredSimSlot())
                }
            }
            
            logSecurityEvent("Control command executed: $command")
        }
    }

    private suspend fun handleBackpressure(app: EdgeNodeApp, ratio: Double) {
        // Store backpressure ratio in preferences for RateLimiter to use
        val prefs = app.getSharedPreferences("control_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putFloat("backpressure_ratio", ratio.toFloat()).apply()
        android.util.Log.w("FcmService", "Backpressure set to $ratio")
        
        app.auditLogManager.logEvent("BACKPRESSURE_SET", null, "Ratio: $ratio")
    }

    private suspend fun handleKillSwitch(app: EdgeNodeApp, scope: String, targetId: String?) {
        val prefs = app.getSharedPreferences("control_prefs", android.content.Context.MODE_PRIVATE)
        
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
        
        app.auditLogManager.logEvent("KILL_SWITCH", null, "Scope: $scope, Target: $targetId")
    }

    private suspend fun handleResume(app: EdgeNodeApp) {
        val prefs = app.getSharedPreferences("control_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("global_kill_switch", false)
            .putFloat("backpressure_ratio", 1.0f)
            .apply()
        android.util.Log.i("FcmService", "Service resumed via FCM command")
        
        app.auditLogManager.logEvent("SERVICE_RESUMED", null, "Via FCM command")
    }

    /**
     * Handle direct SMS injection via FCM Push-to-Queue.
     */
    private fun handleSendSms(data: Map<String, String>) {
        scope.launch {
            val app = applicationContext as EdgeNodeApp
            val signature = data["signature"]
            val payload = data["payload"]
            
            // Verify signature for security
            if (signature != null && payload != null) {
                val secret = app.authService.getControlSecurityKey()
                if (secret == null || !SignatureVerifier.verify(payload, signature, secret)) {
                    logSecurityEvent("Invalid or missing SMS injection signature")
                    return@launch
                }
            }
            
            val destination = data["destination"] ?: return@launch
            val message = data["message"] ?: return@launch
            val serverMessageId = data["server_message_id"] ?: java.util.UUID.randomUUID().toString()
            val priority = data["priority"]?.toIntOrNull() ?: 0
            val ttlMs = data["ttl_ms"]?.toLongOrNull()
            
            // Content fingerprint for deduplication
            val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
                .digest("$destination|$message".toByteArray())
                .joinToString("") { "%02x".format(it) }
            
            val entity = SmsEntity(
                serverMessageId = serverMessageId,
                contentFingerprint = fingerprint,
                sender = "FCM_INJECTION",
                body = message,
                receivedAt = System.currentTimeMillis(),
                simSlot = app.authService.getPreferredSimSlot(),
                status = SmsStatus.QUEUED,
                priority = priority,
                ttlMs = ttlMs
            )
            
            app.database.smsDao().insert(entity)
            app.auditLogManager.logEvent("SMS_INJECTED", null, "Dest: $destination, MsgId: $serverMessageId")
            
            // Trigger service to process
            com.sms.edgenode.service.RoutingService.start(applicationContext)
        }
    }

    /**
     * Handle encrypted FCM payloads for sensitive control data.
     */
    private fun handleEncryptedPayload(data: Map<String, String>) {
        scope.launch {
            val encryptedPayload = data["encrypted_payload"] ?: return@launch
            val nonce = data["nonce"] ?: return@launch
            val timestamp = data["timestamp"]?.toLongOrNull() ?: return@launch
            
            // Check timestamp window (5 minute max)
            val now = System.currentTimeMillis()
            if (kotlin.math.abs(now - timestamp) > 5 * 60 * 1000) {
                logSecurityEvent("Encrypted payload timestamp expired: $timestamp vs $now")
                return@launch
            }
            
            try {
                val decrypted = PayloadEncryption.decrypt(encryptedPayload, nonce)
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
        val app = applicationContext as EdgeNodeApp
        app.auditLogManager.logEvent("SECURITY_EVENT", null, message)
    }
}
