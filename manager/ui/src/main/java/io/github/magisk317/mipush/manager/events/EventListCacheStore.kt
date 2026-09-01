package io.github.magisk317.mipush.manager.events

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.feature.main.subpage.EventInfoForDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import android.annotation.SuppressLint
import android.content.Context
import io.github.magisk317.mipush.common.utils.Utils

/**
 * Persistent (on-disk) cache for event-list pages, complementing the existing
 * in-memory snapshot in [EventListViewModel].
 *
 * Strategy:
 *  - On app start / page open we serve from this cache first so the UI paints
 *    immediately (no 3s+ remote `event_list` round-trip on cold start).
 *  - Automatic refresh is driven by the XMSF maintenance cycle in app-shell mode,
 *    or by the standalone host's periodic background coordinator. User pull-to-refresh
 *    remains an explicit immediate refresh, not the only way to obtain new events.
 *  - Background refresh updates this store without disturbing the visible list;
 *    `updates` lets an open page apply the new cache contents asynchronously.
 */
class EventListCacheStore(
    private val context: Context,
    private val currentUserIdProvider: () -> Int = { Utils.requireValidUserId(Utils.myUserId()) },
) {
    private val dataStore by lazy { context.eventListCacheDataStore }
    private val mutationMutex = Mutex()
    private val _updates = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val updates: SharedFlow<String> = _updates.asSharedFlow()

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** Read cached events for a query key, or null when absent/stale/undecodable. */
    suspend fun getCached(queryKey: String): List<EventInfoForDisplay>? = withContext(Dispatchers.IO) {
        readCached(queryKey)
    }

    private suspend fun readCached(queryKey: String): List<EventInfoForDisplay>? {
        val userId = currentUserIdProvider()
        val currentKey = stringPreferencesKey(buildEventListCacheKey(userId, queryKey))
        val legacyKey = legacyEventListCacheKey(userId, queryKey)?.let(::stringPreferencesKey)
        val preferences = dataStore.data.first()
        val raw = preferences[currentKey] ?: legacyKey?.let { preferences[it] }
            ?: return null
        val cached = runCatching {
            json.decodeFromString<List<ManagerEvent>>(raw)
                .takeIf { eventsBelongToUser(it, userId) }
                ?.map { it.toEventInfoForDisplay() }
        }.getOrNull()
        if (cached == null) {
            // Drop only the corrupt scoped bucket. Other users and query scopes remain intact.
            runCatching {
                dataStore.edit { values ->
                    values.remove(currentKey)
                    legacyKey?.let(values::remove)
                }
            }
            return null
        }
        // Migrate the pre-user-scoped bucket only for primary user; it has no owner identity.
        if (preferences[currentKey] == null && legacyKey != null) {
            runCatching {
                dataStore.edit { values ->
                    values[currentKey] = raw
                    values.remove(legacyKey)
                }
            }
        }
        return cached
    }

    /** Persist events for a query key. Only serializable fields are stored. */
    suspend fun putCached(queryKey: String, events: List<EventInfoForDisplay>) = withContext(Dispatchers.IO) {
        mutationMutex.withLock { writeCached(queryKey, events) }
    }

    suspend fun mergeAndPutCached(
        queryKey: String,
        incoming: List<EventInfoForDisplay>,
        maxEvents: Int = EventListCacheSync.MAX_CACHE_EVENTS,
    ): List<EventInfoForDisplay> = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val merged = mergeEventSnapshots(readCached(queryKey).orEmpty(), incoming)
                .take(maxEvents)
            writeCached(queryKey, merged)
            merged
        }
    }

    private suspend fun writeCached(queryKey: String, events: List<EventInfoForDisplay>) {
        val payload = events.map { it.event }
        val userId = currentUserIdProvider()
        require(eventsBelongToUser(payload, userId)) {
            "Event cache payload contains an event owned by another user: $userId"
        }
        val raw = json.encodeToString(payload)
        dataStore.edit { prefs -> prefs[stringPreferencesKey(scopedKey(queryKey))] = raw }
        _updates.tryEmit(queryKey)
        this.logI {
            "event cache write query=$queryKey events=${events.size} user=$userId"
        }
    }

    /** Drop every cached query bucket (e.g. on clear-history). */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutationMutex.withLock { dataStore.edit { it.clear() } }
    }

    internal fun scopedKey(queryKey: String): String =
        buildEventListCacheKey(currentUserIdProvider(), queryKey)
}

object EventListCacheStoreRegistry {
    // get() always constructs this process-wide cache with applicationContext, never an Activity.
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var instance: EventListCacheStore? = null

    fun get(context: Context): EventListCacheStore = instance ?: synchronized(this) {
        instance ?: EventListCacheStore(context.applicationContext ?: context).also { instance = it }
    }
}

internal fun buildEventListCacheKey(userId: Int, queryKey: String): String {
    require(userId >= 0) { "Invalid event cache user id: $userId" }
    return "user=$userId;$queryKey"
}

internal fun legacyEventListCacheKey(userId: Int, queryKey: String): String? {
    require(userId >= 0) { "Invalid event cache user id: $userId" }
    return queryKey.takeIf { userId == 0 }
}

internal fun eventsBelongToUser(events: List<ManagerEvent>, userId: Int): Boolean {
    require(userId >= 0) { "Invalid event cache user id: $userId" }
    return events.all { it.userId == userId }
}

private val Context.eventListCacheDataStore by preferencesDataStore(name = "event_list_cache")

private fun ManagerEvent.toEventInfoForDisplay(): EventInfoForDisplay = EventInfoForDisplay(
    id = id,
    packageName = packageName,
    configOptions = configOptions,
    channel = channel,
    receiveDate = java.util.Date(receiveDateMs),
    title = title,
    content = content,
    appName = appName,
)
