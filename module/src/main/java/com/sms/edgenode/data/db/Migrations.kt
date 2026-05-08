package com.sms.edgenode.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sms_queue ADD COLUMN forwardDepth INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sms_queue ADD COLUMN nextRetryAt INTEGER")
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS rate_limit (
                simSlot INTEGER PRIMARY KEY NOT NULL,
                lastSendAt INTEGER NOT NULL,
                sendCount INTEGER NOT NULL,
                windowStartAt INTEGER NOT NULL
            )
        """)
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SmsEntity new columns
        db.execSQL("ALTER TABLE sms_queue ADD COLUMN serverMessageId TEXT")
        db.execSQL("ALTER TABLE sms_queue ADD COLUMN contentFingerprint TEXT")
        db.execSQL("ALTER TABLE sms_queue ADD COLUMN retryAfterMs INTEGER")
        db.execSQL("ALTER TABLE sms_queue ADD COLUMN failureReason TEXT")
        db.execSQL("ALTER TABLE sms_queue ADD COLUMN nodeId TEXT")
        db.execSQL("ALTER TABLE sms_queue ADD COLUMN leaseAt INTEGER")
        db.execSQL("ALTER TABLE sms_queue ADD COLUMN ttlMs INTEGER")
        db.execSQL("ALTER TABLE sms_queue ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
        
        // AuditLogEntity new columns for hash chaining
        db.execSQL("ALTER TABLE audit_log ADD COLUMN prevHash TEXT")
        db.execSQL("ALTER TABLE audit_log ADD COLUMN currentHash TEXT")
        
        // RateLimitEntity new quota columns
        db.execSQL("ALTER TABLE rate_limit ADD COLUMN dailyCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rate_limit ADD COLUMN monthlyCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rate_limit ADD COLUMN lastDailyResetAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rate_limit ADD COLUMN lastMonthlyResetAt INTEGER NOT NULL DEFAULT 0")
        
        // Create indexes
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sms_queue_serverMessageId ON sms_queue(serverMessageId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sms_queue_contentFingerprint ON sms_queue(contentFingerprint)")

        // Drop old unique index (might fail if doesn't exist, that's ok)
        try {
            db.execSQL("DROP INDEX IF EXISTS index_sms_queue_messageHash")
        } catch (e: Exception) { /* Ignore */ }
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sms_queue ADD COLUMN correlationId TEXT")
        db.execSQL("ALTER TABLE pending_status_updates ADD COLUMN correlationId TEXT")
    }
}
