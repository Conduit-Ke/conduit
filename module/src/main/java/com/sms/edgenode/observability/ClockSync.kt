package com.sms.edgenode.observability

import android.content.Context
import java.util.concurrent.atomic.AtomicLong

/**
 * Clock drift synchronization and timestamp normalization.
 * Ensures consistent time across distributed nodes.
 */
object ClockSync {
    // Server time offset (serverTime - localTime)
    private val timeOffset = AtomicLong(0)
    private var lastSyncAt: Long = 0
    private const val SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

    /**
     * Sync local clock with server time.
     */
    fun syncWithServer(serverTimestampMs: Long) {
        val localTime = System.currentTimeMillis()
        val offset = serverTimestampMs - localTime
        timeOffset.set(offset)
        lastSyncAt = localTime
        
        if (kotlin.math.abs(offset) > 5000) {
            android.util.Log.w("ClockSync", "Clock drift detected: ${offset}ms")
        }
    }

    /**
     * Get normalized timestamp (adjusted for drift).
     */
    fun getNormalizedTime(): Long {
        return System.currentTimeMillis() + timeOffset.get()
    }

    /**
     * Get the current clock offset.
     */
    fun getOffset(): Long = timeOffset.get()

    /**
     * Check if time needs re-sync.
     */
    fun needsSync(): Boolean {
        return System.currentTimeMillis() - lastSyncAt > SYNC_INTERVAL_MS
    }

    /**
     * Validate a timestamp is within acceptable window.
     */
    fun isTimestampValid(timestamp: Long, windowMs: Long = 5 * 60 * 1000L): Boolean {
        val now = getNormalizedTime()
        return kotlin.math.abs(now - timestamp) <= windowMs
    }

    /**
     * Get sync status report.
     */
    fun getStatus(): SyncStatus {
        return SyncStatus(
            offset = timeOffset.get(),
            lastSyncAt = lastSyncAt,
            needsSync = needsSync()
        )
    }

    data class SyncStatus(
        val offset: Long,
        val lastSyncAt: Long,
        val needsSync: Boolean
    )
}
