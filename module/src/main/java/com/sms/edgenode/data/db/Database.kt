package com.sms.edgenode.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "sms_queue", 
    indices = [
        Index(value = ["serverMessageId"], unique = true),
        Index(value = ["contentFingerprint"])
    ]
)
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverMessageId: String? = null, // Immutable UUID for idempotency
    val contentFingerprint: String? = null, // Content hash for dedup
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val simSlot: Int,
    val status: String,
    val routingRuleId: String? = null,
    val forwardTarget: String? = null,
    val forwardDepth: Int = 0,
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val deliveredAt: Long? = null,
    val nextRetryAt: Long? = null,
    val retryAfterMs: Long? = null,
    val failureReason: String? = null,
    val nodeId: String? = null, // For multi-node locking
    val leaseAt: Long? = null, // Lease timestamp
    val ttlMs: Long? = null, // Message TTL
    val priority: Int = 0
)

@Entity(tableName = "routing_rules")
data class RoutingRuleEntity(
    @PrimaryKey val id: String,
    val version: Int,
    val ruleJson: String,
    val signature: String,
    val activatedAt: Long,
    val isActive: Boolean
)

@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val eventType: String,
    val smsId: Long?,
    val details: String,
    val prevHash: String? = null, // Hash chaining anchor
    val currentHash: String? = null // Hash of this entry
)

@Entity(tableName = "rate_limit")
data class RateLimitEntity(
    @PrimaryKey val simSlot: Int,
    val lastSendAt: Long,
    val sendCount: Int,
    val windowStartAt: Long,
    val dailyCount: Int = 0,
    val monthlyCount: Int = 0,
    val lastDailyResetAt: Long = 0,
    val lastMonthlyResetAt: Long = 0
)

@Dao
interface SmsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sms: SmsEntity): Long

    @Query("SELECT * FROM sms_queue WHERE id = :id")
    suspend fun getById(id: Long): SmsEntity?

    @Transaction
    suspend fun acquireLease(nodeId: String, leaseExpiryThreshold: Long, now: Long): SmsEntity? {
        val next = getNextAvailable(now, leaseExpiryThreshold) ?: return null
        val rows = updateLease(next.id, nodeId, now)
        return if (rows > 0) next.copy(nodeId = nodeId, leaseAt = now, status = "SENDING") else null
    }

    @Query("SELECT * FROM sms_queue WHERE (status = 'RECEIVED' OR status = 'QUEUED' OR (status = 'SENDING' AND leaseAt < :leaseExpiryThreshold)) AND (nextRetryAt IS NULL OR nextRetryAt <= :now) ORDER BY priority DESC, receivedAt ASC LIMIT 1")
    suspend fun getNextAvailable(now: Long, leaseExpiryThreshold: Long): SmsEntity?

    @Query("UPDATE sms_queue SET status = 'SENDING', nodeId = :nodeId, leaseAt = :now WHERE id = :id AND (status = 'RECEIVED' OR status = 'QUEUED' OR (status = 'SENDING' AND leaseAt < :now - 300000))") // 5 min lease
    suspend fun updateLease(id: Long, nodeId: String, now: Long): Int

    @Query("UPDATE sms_queue SET status = :status, attempts = :attempts, lastAttemptAt = :timestamp, nextRetryAt = :nextRetry, failureReason = :reason WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, attempts: Int, timestamp: Long, nextRetry: Long?, reason: String?)

    @Query("UPDATE sms_queue SET deliveredAt = :timestamp, status = 'DELIVERED' WHERE id = :id")
    suspend fun markDelivered(id: Long, timestamp: Long)

    @Query("SELECT * FROM sms_queue WHERE status NOT IN ('DELIVERED', 'FAILED', 'EXPIRED', 'DELIVERED_UNKNOWN') ORDER BY receivedAt DESC")
    fun observeQueue(): Flow<List<SmsEntity>>

    @Query("SELECT COUNT(*) FROM sms_queue WHERE forwardTarget = :target AND forwardDepth >= :maxDepth AND receivedAt > :since")
    suspend fun countRecentForwards(target: String, maxDepth: Int, since: Long): Int

    @Query("SELECT COUNT(*) FROM sms_queue WHERE status IN ('QUEUED', 'SENDING', 'SUBMITTED')")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM sms_queue WHERE status = 'DELIVERED'")
    suspend fun getDeliveredCount(): Int

    @Query("UPDATE sms_queue SET status = 'QUEUED' WHERE status = 'HELD' AND sender = 'FCM_INJECTION'")
    suspend fun requeueHeldOutbound()

    @Query("UPDATE sms_queue SET status = 'RECEIVED' WHERE status = 'HELD' AND sender != 'FCM_INJECTION'")
    suspend fun requeueHeldInbound()

    @Transaction
    suspend fun requeueHeld() {
        requeueHeldOutbound()
        requeueHeldInbound()
    }

    @Query("UPDATE sms_queue SET status = 'RECEIVED' WHERE status = 'RECEIVED' AND leaseAt IS NOT NULL AND leaseAt < :threshold")
    suspend fun recoverStuckReceived(threshold: Long)

    @Query("UPDATE sms_queue SET status = CASE WHEN sender = 'FCM_INJECTION' THEN 'QUEUED' ELSE 'RECEIVED' END, nodeId = NULL, leaseAt = NULL WHERE nodeId = :nodeId AND status = 'SENDING'")
    suspend fun releaseLeases(nodeId: String)

    @Delete
    suspend fun delete(sms: SmsEntity)

    @Query("DELETE FROM sms_queue WHERE receivedAt < :before")
    suspend fun deleteOldMessages(before: Long)
}

@Dao
interface RoutingRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RoutingRuleEntity)

    @Query("SELECT * FROM routing_rules WHERE isActive = 1 ORDER BY version DESC LIMIT 1")
    suspend fun getActiveRule(): RoutingRuleEntity?

    @Query("UPDATE routing_rules SET isActive = 0")
    suspend fun deactivateAll()
}

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(log: AuditLogEntity)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 1000")
    fun observeLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log ORDER BY id DESC LIMIT 1")
    suspend fun getLastLog(): AuditLogEntity?

    @Query("DELETE FROM audit_log WHERE timestamp < :before")
    suspend fun deleteOldLogs(before: Long)
}

@Dao
interface RateLimitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(limit: RateLimitEntity)

    @Query("SELECT * FROM rate_limit WHERE simSlot = :simSlot")
    suspend fun get(simSlot: Int): RateLimitEntity?
}

@Database(
    entities = [SmsEntity::class, RoutingRuleEntity::class, AuditLogEntity::class, RateLimitEntity::class],
    version = 3,
    exportSchema = false
)
abstract class EdgeNodeDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao
    abstract fun routingRuleDao(): RoutingRuleDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun rateLimitDao(): RateLimitDao
}
