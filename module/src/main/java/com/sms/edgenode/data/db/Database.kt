package com.sms.edgenode.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class SmsStatus {
    RECEIVED, QUEUED, SENDING, SENT, DELIVERED, FAILED, EXPIRED, SUBMITTED, HELD, DROP, LOOP_DETECTED, DELIVERED_UNKNOWN, SYSTEM, EXPIRED_BACKPRESSURE, IGNORED
}

class Converters {
    @TypeConverter
    fun fromSmsStatus(status: SmsStatus): String = status.name

    @TypeConverter
    fun toSmsStatus(value: String): SmsStatus = try {
        SmsStatus.valueOf(value)
    } catch (e: Exception) {
        SmsStatus.SYSTEM
    }
}

@Entity(
    tableName = "sms_queue", 
    indices = [
        Index(value = ["serverMessageId"], unique = true),
        Index(value = ["contentFingerprint"])
    ]
)
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverMessageId: String? = null,
    val contentFingerprint: String? = null,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val simSlot: Int,
    val status: SmsStatus,
    val routingRuleId: String? = null,
    val forwardTarget: String? = null,
    val forwardDepth: Int = 0,
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val deliveredAt: Long? = null,
    val nextRetryAt: Long? = null,
    val retryAfterMs: Long? = null,
    val failureReason: String? = null,
    val nodeId: String? = null,
    val leaseAt: Long? = null,
    val ttlMs: Long? = null,
    val priority: Int = 0,
    val correlationId: String? = null
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
    val prevHash: String? = null,
    val currentHash: String? = null
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

@Entity(tableName = "pending_status_updates")
data class PendingStatusEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsId: Long,
    val serverMessageId: String?,
    val phoneNumber: String,
    val status: SmsStatus,
    val error: String?,
    val timestamp: Long,
    val simSlot: Int? = null,
    val carrier: String? = null,
    val message: String? = null,
    val correlationId: String? = null
)

@Dao
interface SmsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sms: SmsEntity): Long

    @Query("SELECT * FROM sms_queue WHERE id = :id")
    suspend fun getById(id: Long): SmsEntity?

    @Transaction
    suspend fun acquireLease(nodeId: String, targetSimSlot: Int, leaseExpiryThreshold: Long, now: Long): SmsEntity? {
        val next = getNextAvailable(now, leaseExpiryThreshold, targetSimSlot) ?: return null
        val rows = updateLease(next.id, nodeId, now)
        return if (rows > 0) next.copy(nodeId = nodeId, leaseAt = now, status = SmsStatus.SENDING) else null
    }

    @Query("SELECT * FROM sms_queue WHERE (status = 'RECEIVED' OR status = 'QUEUED' OR (status = 'SENDING' AND leaseAt < :leaseExpiryThreshold)) AND (nextRetryAt IS NULL OR nextRetryAt <= :now) AND (:targetSimSlot = -1 OR simSlot = :targetSimSlot OR (simSlot = -1 AND :targetSimSlot = 0)) ORDER BY priority DESC, receivedAt ASC LIMIT 1")
    suspend fun getNextAvailable(now: Long, leaseExpiryThreshold: Long, targetSimSlot: Int): SmsEntity?

    @Query("UPDATE sms_queue SET status = 'SENDING', nodeId = :nodeId, leaseAt = :now WHERE id = :id AND (status = 'RECEIVED' OR status = 'QUEUED' OR (status = 'SENDING' AND leaseAt < :now - 300000))")
    suspend fun updateLease(id: Long, nodeId: String, now: Long): Int

    @Query("UPDATE sms_queue SET status = :status, attempts = :attempts, lastAttemptAt = :timestamp, nextRetryAt = :nextRetry, failureReason = :reason WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SmsStatus, attempts: Int, timestamp: Long, nextRetry: Long?, reason: String?)

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

    @Query("SELECT COUNT(*) FROM sms_queue WHERE status IN ('FAILED', 'EXPIRED', 'DROP', 'LOOP_DETECTED')")
    suspend fun getFailedCount(): Int

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

    @Query("SELECT id || '|' || status || '|' || sender || '|' || COALESCE(nextRetryAt, 0) || '|' || COALESCE(failureReason, 'none') FROM sms_queue WHERE status NOT IN ('DELIVERED', 'FAILED', 'EXPIRED', 'SYSTEM') LIMIT 10")
    suspend fun getStuckMessages(): List<String>

    @Query("UPDATE sms_queue SET status = CASE WHEN sender = 'FCM_INJECTION' THEN 'QUEUED' ELSE 'RECEIVED' END, nodeId = NULL, leaseAt = NULL WHERE nodeId = :nodeId AND status = 'SENDING'")
    suspend fun releaseLeases(nodeId: String)

    @Delete
    suspend fun delete(sms: SmsEntity)

    @Query("DELETE FROM sms_queue WHERE receivedAt < :before")
    suspend fun deleteOldMessages(before: Long)

    @Query("DELETE FROM sms_queue WHERE status = 'DELIVERED'")
    suspend fun deleteDelivered()

    @Query("UPDATE sms_queue SET status = 'DELIVERED', deliveredAt = :now WHERE status = 'SUBMITTED' AND lastAttemptAt IS NOT NULL AND lastAttemptAt < :threshold")
    suspend fun promoteSubmitted(threshold: Long, now: Long)

    @Query("UPDATE sms_queue SET status = 'RECEIVED', attempts = 0, nextRetryAt = NULL, failureReason = NULL WHERE status IN ('FAILED', 'EXPIRED') AND receivedAt > :threshold")
    suspend fun requeueRecentFailures(threshold: Long): Int

    @Query("SELECT status || ':' || COUNT(*) FROM sms_queue GROUP BY status")
    suspend fun getStatusSummary(): List<String>
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

@Dao
interface PendingStatusDao {
    @Insert
    suspend fun insert(status: PendingStatusEntity)

    @Query("SELECT * FROM pending_status_updates ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getNextBatch(limit: Int): List<PendingStatusEntity>

    @Query("DELETE FROM pending_status_updates WHERE id IN (:ids)")
    suspend fun deleteBatch(ids: List<Long>)
}

@Database(
    entities = [
        SmsEntity::class, 
        RoutingRuleEntity::class, 
        AuditLogEntity::class, 
        RateLimitEntity::class,
        PendingStatusEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class EdgeNodeDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao
    abstract fun routingRuleDao(): RoutingRuleDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun rateLimitDao(): RateLimitDao
    abstract fun pendingStatusDao(): PendingStatusDao
}
