package io.github.magisk317.mipush.manager.cache

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageSnapshotStoreTest {
    private val key = SnapshotKey("events", "q", "all", 10)

    private fun snapshot(
        key: SnapshotKey = this.key,
        generation: RefreshGeneration = RefreshGeneration(0),
        value: String = "value",
        complete: Boolean = true,
        createdAt: Long = 100,
        expiresAt: Long = 200,
    ) = PageSnapshot(
        key = key,
        generation = generation,
        value = value,
        createdAtMillis = createdAt,
        expiresAtMillis = expiresAt,
        source = SnapshotSource.REMOTE,
        isComplete = complete,
        hasMore = false,
    )

    @Test
    fun `refresh generation advances without creating another content bucket`() = runBlocking {
        val store = PageSnapshotStore<String>()

        assertEquals(RefreshGeneration(0), store.generationFor(key))
        assertEquals(RefreshGeneration(1), store.nextGeneration(key))
        assertEquals(RefreshGeneration(2), store.nextGeneration(key))
        assertEquals(1, store.bucketCount())
    }

    @Test
    fun `publication exposes complete old or new snapshot atomically`() = runBlocking {
        val store = PageSnapshotStore<String>()
        val generation = store.nextGeneration(key)
        val first = snapshot(generation = generation, value = "first")

        assertTrue(store.publishIfCurrent(key, generation, first, now = 150))
        assertEquals(first, store.read(key, now = 150))
        val secondGeneration = store.nextGeneration(key)
        val second = snapshot(generation = secondGeneration, value = "second")
        assertTrue(store.publishIfCurrent(key, secondGeneration, second, now = 150))

        val visible = store.read(key, now = 150) ?: error("published snapshot was not visible")
        assertTrue(visible == first || visible == second)
        assertEquals(visible.value, if (visible === first) "first" else "second")
        assertEquals(visible.key, key)
        assertTrue(visible.isComplete)
    }

    @Test
    fun `bad bucket is isolated and does not corrupt unrelated snapshot`() = runBlocking {
        val goodKey = key.copy(query = "good")
        val badKey = key.copy(query = "bad")
        val store = PageSnapshotStore<String>(integrity = { it != "corrupt" })
        val goodGeneration = store.nextGeneration(goodKey)
        val badGeneration = store.nextGeneration(badKey)

        assertTrue(store.publishIfCurrent(goodKey, goodGeneration, snapshot(goodKey, goodGeneration, "good"), now = 150))
        assertFalse(store.publishIfCurrent(badKey, badGeneration, snapshot(badKey, badGeneration, "corrupt"), now = 150))

        assertEquals("good", store.read(goodKey, now = 150)?.value)
        assertNull(store.read(badKey, now = 150))
        assertEquals(2, store.bucketCount())
    }

    @Test
    fun `disk persistence retries while retaining memory snapshot`() = runBlocking {
        val attempts = AtomicInteger(0)
        val disk = object : PageSnapshotDiskStore<String> {
            override suspend fun persist(snapshot: PageSnapshot<String>) {
                if (attempts.incrementAndGet() == 1) error("temporary disk failure")
            }

            override suspend fun invalidate(keys: Set<SnapshotKey>) = Unit
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = PageSnapshotStore(
            diskStore = disk,
            persistenceScope = scope,
            initialBackoffMillis = 0,
        )
        try {
            val generation = store.nextGeneration(key)
            val valid = snapshot(generation = generation, value = "kept")

            assertTrue(store.publishIfCurrent(key, generation, valid, now = 150))
            withTimeout(2_000) {
                while (attempts.get() < 2) yield()
            }
            assertEquals(valid, store.read(key, now = 150))
        } finally {
            store.close()
        }
    }

    @Test
    fun `query change immediately isolates old in flight result`() = runBlocking {
        val store = PageSnapshotStore<String>()
        val oldGeneration = store.nextGeneration(key)
        val queryKey = key.copy(query = "new-query")
        val newGeneration = store.nextGeneration(queryKey)
        store.nextGeneration(key)

        // A query change advances the old request before its delayed result returns.
        assertFalse(
            store.publishIfCurrent(
                key,
                oldGeneration,
                snapshot(generation = oldGeneration, value = "old"),
                now = 150,
            ),
        )
        assertNull(store.read(key, oldGeneration, now = 150))
        assertTrue(
            store.publishIfCurrent(
                queryKey,
                newGeneration,
                snapshot(queryKey, newGeneration, "new"),
                now = 150,
            ),
        )
        assertEquals("new", store.read(queryKey, now = 150)?.value)
    }

    @Test
    fun `stale generation result is discarded and previous snapshot remains visible`() = runBlocking {
        val store = PageSnapshotStore<String>()
        val firstGeneration = store.nextGeneration(key)
        assertTrue(store.publishIfCurrent(key, firstGeneration, snapshot(generation = firstGeneration, value = "old"), now = 150))

        val currentGeneration = store.nextGeneration(key)
        assertFalse(store.publishIfCurrent(key, firstGeneration, snapshot(generation = firstGeneration, value = "late"), now = 150))

        assertEquals("old", store.read(key, now = 150)?.value)
        assertNull(store.read(key, currentGeneration, now = 150))
    }

    @Test
    fun `access scope change preserves snapshots`() = runBlocking {
        val store = PageSnapshotStore<String>()
        val generation = store.nextGeneration(key)
        assertTrue(store.publishIfCurrent(key, generation, snapshot(generation = generation), now = 150))

        assertEquals(0, store.invalidateFor(SnapshotInvalidationReason.ACCESS_SCOPE_CHANGED, key.userId))
        assertEquals("value", store.read(key, now = 150)?.value)
        assertEquals(1, store.bucketCount())
    }

    @Test
    fun `history user and permission changes invalidate affected user scopes`() = runBlocking {
        val store = PageSnapshotStore<String>()
        val user10 = key
        val user11 = key.copy(userId = 11)
        listOf(user10, user11).forEach { scopeKey ->
            val generation = store.nextGeneration(scopeKey)
            assertTrue(store.publishIfCurrent(scopeKey, generation, snapshot(scopeKey, generation), now = 150))
        }

        assertEquals(1, store.invalidateFor(SnapshotInvalidationReason.PERMISSIONS_CHANGED, 10))
        assertNull(store.read(user10, now = 150))
        assertEquals("value", store.read(user11, now = 150)?.value)
        assertEquals(1, store.invalidateFor(SnapshotInvalidationReason.HISTORY_CLEARED))
        assertEquals(0, store.bucketCount())
    }

    @Test
    fun `user scope rejects reads and preserves other buckets`() = runBlocking {
        val otherUserKey = key.copy(userId = 11)
        val store = PageSnapshotStore<String>(userScope = { it.userId == 10 })
        val generation = store.nextGeneration(key)
        assertTrue(store.publishIfCurrent(key, generation, snapshot(generation = generation), now = 150))

        assertEquals("value", store.read(key, now = 150)?.value)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { store.read(otherUserKey, now = 150) }
        }
        assertEquals(1, store.bucketCount())
    }

    @Test
    fun `refresh retains old content until replacement is published`() = runBlocking {
        val store = PageSnapshotStore<String>()
        val firstGeneration = store.nextGeneration(key)
        val old = snapshot(generation = firstGeneration, value = "old")
        assertTrue(store.publishIfCurrent(key, firstGeneration, old, now = 150))

        val refreshGeneration = store.nextGeneration(key)
        assertEquals("old", store.read(key, now = 150)?.value)
        assertNull(store.read(key, refreshGeneration, now = 150))

        val fresh = snapshot(generation = refreshGeneration, value = "fresh")
        assertTrue(store.publishIfCurrent(key, refreshGeneration, fresh, now = 150))
        assertEquals("fresh", store.read(key, now = 150)?.value)
    }
}
