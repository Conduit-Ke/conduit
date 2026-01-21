package com.sms.edgenode.domain

import com.sms.edgenode.data.db.EdgeNodeDatabase
import com.sms.edgenode.data.db.SmsEntity
import com.sms.edgenode.data.rules.RuleParser
import com.sms.edgenode.sender.RateLimiter
import com.sms.edgenode.sender.SmsManager
import com.sms.edgenode.sender.SimHealthManager
import kotlin.math.min
import kotlin.math.pow

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.sms.edgenode.data.db.SmsStatus
import com.sms.edgenode.data.db.AuditLogEntity
import java.util.UUID
import kotlin.random.Random

class RoutingEngine(
    private val database: EdgeNodeDatabase,
    private val smsManager: SmsManager,
    private val rateLimiter: RateLimiter,
    private val simHealthManager: SimHealthManager,
    private val authService: com.sms.edgenode.data.api.AuthService,
    private val smsApiService: com.sms.edgenode.data.api.SmsApiService,
    private val nodeId: String = java.util.UUID.randomUUID().toString()
) {
    private val mutex = Mutex()
    var onRemoteManagement: ((Int) -> Unit)? = null

    companion object {
        private const val MAX_ATTEMPTS = 10
        private const val BASE_BACKOFF_MS = 30_000L // 30 seconds
        private const val LEASE_DURATION_MS = 300_000L // 5 minutes
        private const val BATCH_SIZE = 50
    }

    suspend fun processNext(): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        
        // 1. Acquire Atomic Lease (QUEUED -> SENDING)
        val sms = database.smsDao().acquireLease(
            nodeId = nodeId,
            leaseExpiryThreshold = now - LEASE_DURATION_MS,
            now = now
        )
        if (sms == null) {
            // No messages to process
            return false
        }

        android.util.Log.i("RoutingEngine", "Acquired lease for SMS ${sms.id} (Ref: ${sms.serverMessageId}) from ${sms.sender}")

        // 2. Adaptive TTL Check
        val ttlMs = sms.ttlMs ?: (30 * 60 * 1000L) // Default 30 min
        if (now - sms.receivedAt > ttlMs) {
            database.smsDao().updateStatus(sms.id, SmsStatus.EXPIRED, sms.attempts, now, null, "TTL_EXPIRED")
            logEvent("EXPIRED", sms.id, "TTL Expired after ${ttlMs/1000}s")
            return true
        }

        // 3. Evaluate Rule
        val activeRule = database.routingRuleDao().getActiveRule()
        val action = if (activeRule != null) {
            evaluateRule(sms, activeRule.ruleJson)
        } else {
            RoutingAction.SubmitToServer
        }

        executeAction(sms, action)
        return true
    }

    private suspend fun executeAction(sms: SmsEntity, action: RoutingAction) {
        val now = System.currentTimeMillis()
        when (action) {
            is RoutingAction.Forward -> {
                // Check SIM health and select best available
                val preferredSlot = authService.getPreferredSimSlot()
                val isSmartFailoverEnabled = authService.isSmartFailoverEnabled()
                val simStatus = simHealthManager.getStatus(preferredSlot)
                
                if (simStatus == SimHealthManager.SimStatus.QUARANTINED) {
                    // Try failover to another SIM if enabled
                    if (isSmartFailoverEnabled) {
                        val bestSim = simHealthManager.getBestSim(preferredSlot)
                        if (bestSim == preferredSlot) {
                            // No alternative even with failover, requeue
                            val nextRetry = now + 60_000L
                            database.smsDao().updateStatus(sms.id, SmsStatus.QUEUED, sms.attempts, now, nextRetry, "SIM_QUARANTINED")
                            return
                        }
                    } else {
                        // Failover disabled, just backoff
                        val nextRetry = now + 120_000L
                        database.smsDao().updateStatus(sms.id, SmsStatus.QUEUED, sms.attempts, now, nextRetry, "SIM_QUARANTINED_NO_FAILOVER")
                        return
                    }
                }
                
                val selectedSlot = if (isSmartFailoverEnabled) {
                    simHealthManager.getBestSim(preferredSlot)
                } else {
                    preferredSlot
                }

                // Rate Limit & TPS Enforcement
                if (!rateLimiter.checkAndUpdate(selectedSlot)) {
                    val nextRetry = now + 60_000L // Cool down
                    database.smsDao().updateStatus(sms.id, SmsStatus.QUEUED, sms.attempts, now, nextRetry, "RATE_LIMITED")
                    return
                }

                // Invisible Mode Jitter (Humanized patterns)
                if (authService.isInvisibleModeEnabled()) {
                    val jitterDelay = Random.nextLong(3_000, 12_000)
                    android.util.Log.d("RoutingEngine", "Invisible Mode: Adding jitter delay of $jitterDelay ms")
                    kotlinx.coroutines.delay(jitterDelay)
                }

                // 4. SUBMITTED Transition (Hardware Handoff)
                val accepted = smsManager.sendSms(action.target, sms.body, sms.id, selectedSlot)
                
                if (accepted) {
                    simHealthManager.recordSuccess(selectedSlot)
                    database.smsDao().updateStatus(sms.id, SmsStatus.SUBMITTED, sms.attempts + 1, now, null, null)
                    logEvent("SUBMITTED", sms.id, "Accepted by radio for ${action.target} via SIM $selectedSlot")
                } else {
                    simHealthManager.recordFailure(selectedSlot)
                    handleFailure(sms, "RADIO_REJECTED")
                }
            }
            is RoutingAction.SubmitToServer -> {
                val orgId = authService.getOrganizationId()
                if (orgId == -1) {
                    database.smsDao().updateStatus(sms.id, SmsStatus.HELD, sms.attempts, now, null, "ORG_NOT_FOUND")
                    return
                }

                val result = smsApiService.submitSms(
                    sender = sms.sender,
                    message = sms.body,
                    messageId = sms.serverMessageId ?: sms.id.toString(),
                    destination = "SERVER",
                    sentTimestamp = sms.receivedAt,
                    organizationId = orgId.toString()
                )

                if (result.isSuccess) {
                    android.util.Log.i("RoutingEngine", "SMS ${sms.id} successfully submitted to server")
                    database.smsDao().markDelivered(sms.id, now)
                    logEvent("DELIVERED", sms.id, "Successfully submitted to server")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown API Error"
                    if (error.contains("404") || error.contains("403")) {
                        onRemoteManagement?.invoke(if (error.contains("404")) 404 else 403)
                    }
                    handleFailure(sms, error)
                }
            }
            is RoutingAction.Hold -> {
                database.smsDao().updateStatus(sms.id, SmsStatus.HELD, sms.attempts, now, null, "Rule evaluation: HOLD")
            }
            is RoutingAction.Drop -> {
                database.smsDao().updateStatus(sms.id, SmsStatus.FAILED, sms.attempts, now, null, "Rule evaluation: DROP")
            }
        }
    }

    private suspend fun handleFailure(sms: SmsEntity, reason: String) {
        val now = System.currentTimeMillis()
        if (sms.attempts >= MAX_ATTEMPTS) {
            database.smsDao().updateStatus(sms.id, SmsStatus.FAILED, sms.attempts, now, null, "Max attempts reached: $reason")
            logEvent("FAILED", sms.id, "Max attempts reached: $reason")
        } else {
            val nextRetry = calculateBackoff(sms.attempts)
            database.smsDao().updateStatus(sms.id, SmsStatus.QUEUED, sms.attempts + 1, now, nextRetry, reason)
            logEvent("RETRY_SCHEDULED", sms.id, "Retry #${sms.attempts + 1} after failure: $reason")
        }
    }

    private fun calculateBackoff(attempts: Int): Long {
        val exponential = (BASE_BACKOFF_MS * 2.0.pow(min(attempts, 6))).toLong()
        val jitter = Random.nextLong(0, min(exponential, 15_000L)) // 15s max jitter
        return System.currentTimeMillis() + exponential + jitter
    }

    private fun evaluateRule(sms: SmsEntity, ruleJson: String): RoutingAction {
        return try {
            val rule = RuleParser.parse(ruleJson)
            if (!RuleParser.validate(rule)) return RoutingAction.Hold

            val senderMatch = rule.senderPattern?.let { sms.sender.matches(it.toRegex()) } ?: true
            val bodyMatch = rule.bodyPattern?.let { sms.body.matches(it.toRegex()) } ?: true

            when {
                senderMatch && bodyMatch && rule.action == "FORWARD" -> {
                    RoutingAction.Forward(rule.forwardTarget!!, rule.maxForwardDepth)
                }
                rule.action == "DROP" -> RoutingAction.Drop
                else -> RoutingAction.Hold
            }
        } catch (e: Exception) {
            RoutingAction.Hold
        }
    }

    private suspend fun logEvent(eventType: String, smsId: Long, details: String) {
        // Audit logic will be handled by AuditLogManager to support hash chaining
        // For now, we use a basic insert and will refactor later.
        database.auditLogDao().insert(
            AuditLogEntity(
                timestamp = System.currentTimeMillis(),
                eventType = eventType,
                smsId = smsId,
                details = details
            )
        )
    }
}

sealed class RoutingAction {
    data class Forward(val target: String, val maxDepth: Int = 3) : RoutingAction()
    object SubmitToServer : RoutingAction()
    object Hold : RoutingAction()
    object Drop : RoutingAction()
}
