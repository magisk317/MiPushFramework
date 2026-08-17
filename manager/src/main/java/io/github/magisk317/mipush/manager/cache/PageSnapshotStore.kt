package io.github.magisk317.mipush.manager.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The content scope of a page snapshot. Refresh generations are deliberately not part of this key. */
data class SnapshotKey(
    val page: String,
    val query: String,
    val filter: String,
    val userId: Int,
) {
    init {
        require(page.isNotBlank()) { "page must not be blank" }
        require(userId >= 0) { "userId must be non-negative" }
    }
}

/** Request/version metadata for one [SnapshotKey], independent from its content bucket. */
@JvmInline
value class RefreshGeneration(val value: Long) {
    init {
        require(value >= 0) { "refresh generation must be non-negative" }
    }

    fun next(): RefreshGeneration = require(value < Long.MAX_VALUE) {
        "refresh generation overflow"
    }.let { RefreshGeneration(value + 1) }
}

enum class SnapshotSource {
    MEMORY,
    DISK,
    REMOTE,
}

enum class SnapshotInvalidationReason {
    HISTORY_CLEARED,
    USER_CHANGED,
    PERMISSIONS_CHANGED,
    ACCESS_SCOPE_CHANGED,
}

/** Persistence boundary for snapshots. Implementations must keep their own writes off the UI thread. */
interface PageSnapshotDiskStore<T> {
    suspend fun persist(snapshot: PageSnapshot<T>)

    suspend fun invalidate(keys: Set<SnapshotKey>)
}

private class NoopPageSnapshotDiskStore<T> : PageSnapshotDiskStore<T> {
    override suspend fun persist(snapshot: PageSnapshot<T>) = Unit

    override suspend fun invalidate(keys: Set<SnapshotKey>) = Unit
}

/**
 * An immutable envelope for validated page content.
 *
 * The value itself must be immutable (or defensively copied) at the call site; the store never
 * exposes a mutable envelope or mutates a published snapshot.
 */
data class PageSnapshot<T>(
    val key: SnapshotKey,
    val generation: RefreshGeneration,
    val value: T,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val source: SnapshotSource,
    val isComplete: Boolean,
    val hasMore: Boolean,
) {
    init {
        require(createdAtMillis >= 0) { "createdAtMillis must be non-negative" }
        require(expiresAtMillis >= createdAtMillis) { "expiresAtMillis must not precede creation" }
    }

    fun isFresh(nowMillis: Long): Boolean =
        nowMillis >= createdAtMillis && nowMillis <= expiresAtMillis
}

/**
 * Atomic, generation-aware in-memory page snapshot store.
 *
 * Publication is completed while holding [mutex], then persistence is scheduled on a supervised
 * background scope. A failed disk write never rolls back memory and is retried with bounded
 * exponential backoff. Refresh generations remain request metadata and never create content
 * buckets.
 */
class PageSnapshotStore<T>(
    private val userScope: (SnapshotKey) -> Boolean = { true },
    private val integrity: (T) -> Boolean = { true },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val diskStore: PageSnapshotDiskStore<T> = NoopPageSnapshotDiskStore(),
    private val persistenceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val maxPersistenceAttempts: Int = 3,
    private val initialBackoffMillis: Long = 100,
    private val backoff: suspend (Long) -> Unit = { delay(it) },
) {
    private data class Bucket<T>(
        var generation: RefreshGeneration,
        var snapshot: PageSnapshot<T>?,
    )

    private val mutex = Mutex()
    private val persistenceMutex = Mutex()
    private val buckets = LinkedHashMap<SnapshotKey, Bucket<T>>()
    private val _updates = MutableSharedFlow<SnapshotKey>(extraBufferCapacity = 16)
    val updates: Flow<SnapshotKey> = _updates.asSharedFlow()

    init {
        require(maxPersistenceAttempts > 0) { "maxPersistenceAttempts must be positive" }
        require(initialBackoffMillis >= 0) { "initialBackoffMillis must be non-negative" }
    }

    /** Returns the current generation without allocating a content bucket for the key. */
    suspend fun generationFor(key: SnapshotKey): RefreshGeneration = mutex.withLock {
        requireValidKey(key)
        buckets[key]?.generation ?: RefreshGeneration(0)
    }

    /** Advances the request generation for [key], retaining exactly one content bucket. */
    suspend fun nextGeneration(key: SnapshotKey): RefreshGeneration = mutex.withLock {
        requireValidKey(key)
        val bucket = buckets[key]
        if (bucket == null) {
            val created = Bucket<T>(RefreshGeneration(1), null)
            buckets[key] = created
            created.generation
        } else {
            bucket.generation = bucket.generation.next()
            bucket.generation
        }
    }

    /** Reads a fresh, complete snapshot for [key], validating scope and generation metadata. */
    suspend fun read(
        key: SnapshotKey,
        generation: RefreshGeneration? = null,
        now: Long = nowMillis(),
    ): PageSnapshot<T>? = mutex.withLock {
        requireValidKey(key)
        val bucket = buckets[key] ?: return@withLock null
        if (generation != null && generation != bucket.generation) return@withLock null
        val snapshot = bucket.snapshot ?: return@withLock null
        if (!isValidSnapshot(
                snapshot = snapshot,
                expectedKey = key,
                expectedGeneration = bucket.generation,
                now = now,
                requireExactGeneration = generation != null,
            )
        ) return@withLock null
        snapshot
    }

    /**
     * Publishes an immutable snapshot atomically if its key and generation are still current.
     * Memory publication happens before this method schedules disk persistence and never waits for
     * that persistence. A later publication supersedes an older pending disk write.
     */
    suspend fun publishIfCurrent(
        key: SnapshotKey,
        generation: RefreshGeneration,
        snapshot: PageSnapshot<T>,
        now: Long = nowMillis(),
    ): Boolean {
        val published = mutex.withLock {
            requireValidKey(key)
            val bucket = buckets[key] ?: return@withLock false
            if (bucket.generation != generation) return@withLock false
            if (!isValidSnapshot(
                    snapshot = snapshot,
                    expectedKey = key,
                    expectedGeneration = generation,
                    now = now,
                    requireExactGeneration = true,
                )
            ) return@withLock false
            bucket.snapshot = snapshot
            _updates.tryEmit(key)
            true
        }
        if (published) {
            persistenceScope.launch { persistLatest(snapshot) }
        }
        return published
    }

    /** Invalidates one content scope and resets its request generation. */
    suspend fun invalidate(key: SnapshotKey): Boolean = invalidateWhere { it == key } > 0

    /** Invalidates selected memory buckets and schedules matching disk invalidation. */
    suspend fun invalidateWhere(predicate: (SnapshotKey) -> Boolean): Int {
        val removedKeys = mutex.withLock {
            val keys = buckets.keys.filter(predicate).toSet()
            keys.forEach(buckets::remove)
            keys
        }
        if (removedKeys.isNotEmpty()) {
            persistenceScope.launch { invalidatePersisted(removedKeys) }
        }
        return removedKeys.size
    }

    /**
     * Invalidates only user-affecting changes. Access-scope-only changes deliberately preserve
     * both memory and disk snapshots because they do not change content ownership.
     */
    suspend fun invalidateFor(
        reason: SnapshotInvalidationReason,
        affectedUserId: Int? = null,
    ): Int = when (reason) {
        SnapshotInvalidationReason.ACCESS_SCOPE_CHANGED -> 0
        SnapshotInvalidationReason.HISTORY_CLEARED,
        SnapshotInvalidationReason.USER_CHANGED,
        SnapshotInvalidationReason.PERMISSIONS_CHANGED,
        -> invalidateWhere { key -> affectedUserId == null || key.userId == affectedUserId }
    }

    /** Number of content buckets, useful for asserting refreshes do not duplicate storage. */
    suspend fun bucketCount(): Int = mutex.withLock { buckets.size }

    /** Cancels supervised persistence jobs when the owning lifecycle is destroyed. */
    fun close() {
        persistenceScope.cancel()
    }

    private suspend fun persistLatest(snapshot: PageSnapshot<T>) {
        persistenceMutex.withLock {
            var attempt = 1
            while (attempt <= maxPersistenceAttempts) {
                val current = mutex.withLock {
                    val bucket = buckets[snapshot.key]
                    if (bucket?.generation != snapshot.generation || bucket.snapshot !== snapshot) {
                        null
                    } else {
                        snapshot
                    }
                } ?: return
                val failure = runCatching { diskStore.persist(current) }.exceptionOrNull()
                if (failure == null) return
                if (failure is CancellationException) throw failure
                if (attempt == maxPersistenceAttempts) return
                val multiplier = 1L shl (attempt - 1).coerceAtMost(30)
                backoff(initialBackoffMillis * multiplier)
                attempt++
            }
        }
    }

    private suspend fun invalidatePersisted(keys: Set<SnapshotKey>) {
        persistenceMutex.withLock {
            var attempt = 1
            while (attempt <= maxPersistenceAttempts) {
                val failure = runCatching { diskStore.invalidate(keys) }.exceptionOrNull()
                if (failure == null) return
                if (failure is CancellationException) throw failure
                if (attempt == maxPersistenceAttempts) return
                val multiplier = 1L shl (attempt - 1).coerceAtMost(30)
                backoff(initialBackoffMillis * multiplier)
                attempt++
            }
        }
    }

    private fun requireValidKey(key: SnapshotKey) {
        check(userScope(key)) { "snapshot key is outside the current user scope" }
    }

    private fun isValidSnapshot(
        snapshot: PageSnapshot<T>,
        expectedKey: SnapshotKey,
        expectedGeneration: RefreshGeneration,
        now: Long,
        requireExactGeneration: Boolean,
    ): Boolean = snapshot.key == expectedKey &&
        (if (requireExactGeneration) {
            snapshot.generation == expectedGeneration
        } else {
            snapshot.generation.value <= expectedGeneration.value
        }) &&
        snapshot.isComplete &&
        snapshot.isFresh(now) &&
        userScope(snapshot.key) &&
        integrity(snapshot.value)
}
