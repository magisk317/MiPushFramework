package io.github.magisk317.mipush.runtime.store.kmp

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeEventDeletionRepositoryTest {
    private val now = 10L * 24L * 60L * 60L * 1000L

    @Test
    fun `retention clamps to one day and uses the repository user`() = runBlocking {
        val store = FakeStore()
        val repository = repository(store, userId = 7)

        assertEquals(3, repository.deleteHistory(retentionDays = 0))

        assertEquals(now - DAY_MILLIS, store.lastHistoryCutoff)
        assertEquals(7, store.lastHistoryUserId)
    }

    @Test
    fun `plain delete and range delete stay user scoped`() = runBlocking {
        val store = FakeStore()
        val repository = repository(store, userId = 7)

        assertTrue(repository.deleteById(42))
        assertEquals(42L to 7, store.lastDeletedIdAndUser)
        assertEquals(3, repository.deleteHistoryInRange(100L, 200L))
        assertEquals(Triple(100L, 200L, 7), store.lastRange)
    }

    @Test
    fun `successful undo delete prunes snapshots with bounded retention`() = runBlocking {
        val store = FakeStore(undoDeleteResult = true)
        val repository = repository(store, userId = 7)

        assertTrue(
            repository.deleteByIdWithUndoSnapshot(
                id = 42,
                packageName = "com.example.app",
                requestedUserId = -1,
            ),
        )

        assertEquals(Triple(42L, 0, "com.example.app"), store.lastUndoDelete)
        assertEquals(
            Triple(now - EventRetentionPolicy.UNDO_RETENTION_MS, EventRetentionPolicy.MAX_UNDO_EVENTS, 0),
            store.lastPrune,
        )
    }

    @Test
    fun `failed undo delete does not prune and restore uses scoped user`() = runBlocking {
        val store = FakeStore(undoDeleteResult = false, restoredId = 84L)
        val repository = repository(store, userId = 7)

        assertFalse(repository.deleteByIdWithUndoSnapshot(42, "com.example.app"))
        assertEquals(null, store.lastPrune)
        assertEquals(
            84L,
            repository.restoreDeletedEvent(42, "com.example.app", requestedUserId = -2),
        )
        assertEquals(Triple(42L, 0, "com.example.app"), store.lastRestore)
    }

    @Test
    fun `day counts are delegated with the repository user`() = runBlocking {
        val store = FakeStore(dayCounts = listOf(DayCount("2026-08-25", 3)))
        val repository = repository(store, userId = 7)

        assertEquals(listOf(DayCount("2026-08-25", 3)), repository.countEventsByDay())
        assertEquals(7, store.lastCountUserId)
    }

    private fun repository(
        store: FakeStore,
        userId: Int,
    ): RuntimeEventDeletionRepository = RuntimeEventDeletionRepository(
        store = store,
        userId = userId,
        nowMillis = { now },
    )

    private class FakeStore(
        private val undoDeleteResult: Boolean = true,
        private val restoredId: Long? = null,
        private val dayCounts: List<DayCount> = emptyList(),
    ) : RuntimeEventDeletionStore {
        var lastHistoryCutoff: Long? = null
        var lastHistoryUserId: Int? = null
        var lastDeletedIdAndUser: Pair<Long, Int>? = null
        var lastUndoDelete: Triple<Long, Int, String>? = null
        var lastPrune: Triple<Long, Int, Int>? = null
        var lastRestore: Triple<Long, Int, String>? = null
        var lastCountUserId: Int? = null
        var lastRange: Triple<Long, Long, Int>? = null

        override suspend fun deleteHistory(cutoffMillis: Long, userId: Int): Int {
            lastHistoryCutoff = cutoffMillis
            lastHistoryUserId = userId
            return 3
        }

        override suspend fun deleteById(id: Long, userId: Int): Int {
            lastDeletedIdAndUser = id to userId
            return 1
        }

        override suspend fun deleteByIdWithUndoSnapshotForPackage(
            id: Long,
            userId: Int,
            packageName: String,
        ): Boolean {
            lastUndoDelete = Triple(id, userId, packageName)
            return undoDeleteResult
        }

        override suspend fun pruneDeletedEvents(cutoffMillis: Long, maxCount: Int, userId: Int) {
            lastPrune = Triple(cutoffMillis, maxCount, userId)
        }

        override suspend fun restoreDeletedEventForPackage(
            id: Long,
            userId: Int,
            packageName: String,
        ): Long? {
            lastRestore = Triple(id, userId, packageName)
            return restoredId
        }

        override suspend fun countEventsByDay(userId: Int): List<DayCount> {
            lastCountUserId = userId
            return dayCounts
        }

        override suspend fun deleteHistoryInRange(
            startMillis: Long,
            endMillis: Long,
            userId: Int,
        ): Int {
            lastRange = Triple(startMillis, endMillis, userId)
            return 3
        }
    }

    private companion object {
        const val DAY_MILLIS = 1000L * 3600L * 24L
    }
}
