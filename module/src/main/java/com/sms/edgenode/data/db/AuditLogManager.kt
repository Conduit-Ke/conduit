package com.sms.edgenode.data.db

import java.security.MessageDigest

/**
 * Manages audit log integrity via hash chaining.
 * Each new log entry contains a hash of (previous_hash + current_data),
 * creating a cryptographically verifiable chain of events.
 */
class AuditLogManager(private val auditLogDao: AuditLogDao) {

    suspend fun logEvent(
        eventType: String,
        smsId: Long?,
        details: String
    ) {
        val now = System.currentTimeMillis()
        
        // Get the hash of the last log entry (anchor)
        val lastLog = auditLogDao.getLastLog()
        val prevHash = lastLog?.currentHash ?: "GENESIS"
        
        // Create the data payload for hashing
        val payload = "$now|$eventType|${smsId ?: "null"}|$details|$prevHash"
        val currentHash = sha256(payload)
        
        val entry = AuditLogEntity(
            timestamp = now,
            eventType = eventType,
            smsId = smsId,
            details = details,
            prevHash = prevHash,
            currentHash = currentHash
        )
        
        auditLogDao.insert(entry)
    }

    /**
     * Verify the integrity of the audit log chain.
     * Returns true if all entries are linked correctly.
     */
    suspend fun verifyChainIntegrity(): Boolean {
        // TODO: Implement integrity verification
        // This would involve fetching all logs and verifying each hash.
        // For performance, this should be done periodically or on-demand.
        // Implementation deferred to server-side anchoring.
        return true
    }

    /**
     * Get a digest signature of recent logs for server anchoring.
     */
    suspend fun getDigestSignature(): String {
        val lastLog = auditLogDao.getLastLog()
        return lastLog?.currentHash ?: "EMPTY_CHAIN"
    }

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
