package io.github.magisk317.mipush.runtime.store.kmp

/**
 * Platform-neutral persistence boundary for event deletion and undo snapshots.
 *
 * The Android facade owns the Room DAO adapter and clock. This repository owns
 * retention cutoff calculation, user scoping, and the rule that a successful
 * undo deletion must be followed by deleted-event pruning.
 */
interface RuntimeEventDeletionStore {
    suspend fun deleteHistory(cutoffMillis: Long, userId: Int): Int

    suspend fun deleteById(id: Long, userId: Int): Int

    suspend fun deleteByIdWithUndoSnapshotForPackage(
        id: Long,
        userId: Int,
        packageName: String,
    ): Boolean

    suspend fun pruneDeletedEvents(cutoffMillis: Long, maxCount: Int, userId: Int)

    suspend fun restoreDeletedEventForPackage(
        id: Long,
        userId: Int,
        packageName: String,
    ): Long?

    suspend fun countEventsByDay(userId: Int): List<DayCount>

    suspend fun deleteHistoryInRange(startMillis: Long, endMillis: Long, userId: Int): Int
}

class RuntimeEventDeletionRepository(
    private val store: RuntimeEventDeletionStore,
    private val userId: Int,
    private val nowMillis: () -> Long,
) {
    suspend fun deleteHistory(retentionDays: Int = EventRetentionPolicy.DEFAULT_RETENTION_DAYS): Int {
        val days = retentionDays.coerceAtLeast(1)
        val cutoff = nowMillis() - MILLIS_PER_DAY * days
        return store.deleteHistory(cutoff, userId)
    }

    suspend fun deleteById(id: Long): Boolean =
        store.deleteById(id, userId) > 0

    suspend fun deleteByIdWithUndoSnapshot(
        id: Long,
        packageName: String,
        requestedUserId: Int = userId,
    ): Boolean {
        if (requestedUserId < 0) return false
        val scopedUserId = requestedUserId
        val deleted = store.deleteByIdWithUndoSnapshotForPackage(
            id = id,
            userId = scopedUserId,
            packageName = packageName,
        )
        if (deleted) {
            store.pruneDeletedEvents(
                cutoffMillis = nowMillis() - EventRetentionPolicy.UNDO_RETENTION_MS,
                maxCount = EventRetentionPolicy.MAX_UNDO_EVENTS,
                userId = scopedUserId,
            )
        }
        return deleted
    }

    suspend fun restoreDeletedEvent(
        id: Long,
        packageName: String,
        requestedUserId: Int = userId,
    ): Long? {
        if (requestedUserId < 0) return null
        return store.restoreDeletedEventForPackage(
            id = id,
            userId = requestedUserId,
            packageName = packageName,
        )
    }

    suspend fun countEventsByDay(): List<DayCount> =
        store.countEventsByDay(userId)

    suspend fun deleteHistoryBefore(cutoffMillis: Long): Int =
        store.deleteHistory(cutoffMillis, userId)

    suspend fun deleteHistoryInRange(startMillis: Long, endMillis: Long): Int =
        store.deleteHistoryInRange(startMillis, endMillis, userId)

    private companion object {
        const val MILLIS_PER_DAY: Long = 1000L * 3600L * 24L
    }
}
