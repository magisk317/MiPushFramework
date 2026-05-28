package io.github.magisk317.mipush.notification

import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Platform-independent cache logic for focus notification filtering.
 *
 * Extracted from `NotificationSortFilter` in `:xmsf` to allow pure-logic testing
 * without Android dependencies. Handles:
 * - Parsing `miui.focus.param` JSON (legacy root and HyperIsland `param_v2`)
 * - In-memory LRU cache of deleted focus notification keys
 * - TTL-based expiration and max-size enforcement
 * - Delegation to a [DeletedFocusStore] for persistence
 */
object FocusNotificationCache {

    private const val TAG = "FocusNotificationCache"
    private const val MAX_CACHE_SIZE = 50
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    private val json = Json { ignoreUnknownKeys = true }

    private val deletedFocusCache = LinkedHashMap<String, Long>(MAX_CACHE_SIZE, 0.75f, true)
    private var persistentStore: DeletedFocusStore? = null
    private var loadedFromStore = false

    /**
     * Abstraction for persistent storage of deleted focus notification keys.
     * Implementations may use SharedPreferences, databases, or in-memory maps (for testing).
     */
    interface DeletedFocusStore {
        fun readAll(): Map<String, Long>
        fun put(key: String, expiresAtMs: Long)
        fun remove(key: String)
        fun clear()
    }

    /**
     * Parsed focus parameter result.
     */
    data class FocusParam(val updatable: Boolean, val reopen: String)

    /**
     * Check if a notification should be filtered based on its focus parameters.
     * Returns true if the notification should be dropped (i.e., it was previously deleted
     * and the reopen mode is "close").
     *
     * @param focusParam raw JSON string from `miui.focus.param` extra
     * @param packageName the notification's package name
     * @param notificationId the notification's ID
     * @param nowMs current time in milliseconds
     */
    @JvmStatic
    fun shouldFilter(
        focusParam: String?,
        packageName: String,
        notificationId: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val focus = parseFocusParam(focusParam) ?: return false
        if (!focus.updatable) return false

        val key = cacheKey(packageName, notificationId)
        synchronized(deletedFocusCache) {
            ensureLoadedLocked()
            pruneExpiredLocked(nowMs)
            if (focus.reopen != "close") {
                deletedFocusCache.remove(key)
                persistentStore?.remove(key)
                return false
            }
            val cached = deletedFocusCache.containsKey(key)
            if (cached) {
                Napier.d("filtering deleted focus notification key=$key", tag = TAG)
            }
            return cached
        }
    }

    /**
     * Record that a focus notification has been deleted/removed.
     * Should be called when a notification with focus parameters is cancelled.
     *
     * @param packageName the notification's package name
     * @param notificationId the notification's ID
     * @param nowMs current time in milliseconds
     */
    @JvmStatic
    fun onFocusDeleted(
        packageName: String,
        notificationId: Int,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val key = cacheKey(packageName, notificationId)
        synchronized(deletedFocusCache) {
            ensureLoadedLocked()
            pruneExpiredLocked(nowMs)
            val expiresAtMs = nowMs + CACHE_TTL_MS
            deletedFocusCache[key] = expiresAtMs
            persistentStore?.put(key, expiresAtMs)
            enforceMaxSizeLocked()
            Napier.d("recorded focus deletion key=$key cacheSize=${deletedFocusCache.size}", tag = TAG)
        }
    }

    /**
     * Parse the `miui.focus.param` JSON string into a [FocusParam].
     * Supports both legacy root format and HyperIsland ToolKit `param_v2` format.
     *
     * @return parsed [FocusParam] or null if parsing fails or input is null
     */
    @JvmStatic
    fun parseFocusParam(focusParam: String?): FocusParam? {
        focusParam ?: return null
        return try {
            val root = json.parseToJsonElement(focusParam).jsonObject
            val payload = root["param_v2"]?.jsonObject ?: root
            val reopenPrimitive = payload["reopen"]?.jsonPrimitive
            FocusParam(
                updatable = payload["updatable"]?.jsonPrimitive?.booleanOrNull ?: false,
                reopen = when (reopenPrimitive?.booleanOrNull) {
                    true -> "reopen"
                    false -> "close"
                    null -> reopenPrimitive?.contentOrNull ?: "close"
                }
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generate a cache key from package name and notification ID.
     */
    @JvmStatic
    fun cacheKey(packageName: String, notificationId: Int): String = "$packageName:$notificationId"

    /**
     * Install a persistent store for delegation. Must be called before any
     * filter/delete operations if persistence is desired.
     */
    @JvmStatic
    fun installPersistentStore(store: DeletedFocusStore) {
        synchronized(deletedFocusCache) {
            deletedFocusCache.clear()
            persistentStore = store
            loadedFromStore = false
        }
    }

    /**
     * Remove expired entries from the in-memory cache.
     * Must be called while holding the lock on [deletedFocusCache].
     */
    internal fun pruneExpiredLocked(nowMs: Long) {
        val iterator = deletedFocusCache.entries.iterator()
        val expiredKeys = mutableListOf<String>()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value <= nowMs) {
                expiredKeys.add(entry.key)
                iterator.remove()
            }
        }
        expiredKeys.forEach { persistentStore?.remove(it) }
    }

    /**
     * Enforce the maximum cache size by removing the oldest entries.
     * Must be called while holding the lock on [deletedFocusCache].
     */
    internal fun enforceMaxSizeLocked() {
        val iterator = deletedFocusCache.entries.iterator()
        while (deletedFocusCache.size > MAX_CACHE_SIZE && iterator.hasNext()) {
            val key = iterator.next().key
            iterator.remove()
            persistentStore?.remove(key)
        }
    }

    // --- Test helpers ---

    @JvmStatic
    fun resetForTest() {
        synchronized(deletedFocusCache) {
            deletedFocusCache.clear()
            persistentStore?.clear()
            persistentStore = null
            loadedFromStore = false
        }
    }

    @JvmStatic
    fun clearMemoryCacheForTest() {
        synchronized(deletedFocusCache) {
            deletedFocusCache.clear()
            loadedFromStore = false
        }
    }

    @JvmStatic
    fun installPersistentStoreForTest(store: DeletedFocusStore) {
        synchronized(deletedFocusCache) {
            deletedFocusCache.clear()
            persistentStore = store
            loadedFromStore = false
        }
    }

    // --- Private helpers ---

    private fun ensureLoadedLocked() {
        if (loadedFromStore) return
        persistentStore?.let { store ->
            deletedFocusCache.putAll(store.readAll())
            enforceMaxSizeLocked()
            loadedFromStore = true
        }
    }
}
