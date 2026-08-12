package io.github.magisk317.mipush.manager.events

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.feature.main.subpage.EventInfoForDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import io.github.magisk317.mipush.common.utils.Utils

/**
 * Persistent (on-disk) cache for event-list pages, complementing the existing
 * in-memory snapshot in [EventListViewModel].
 *
 * Strategy:
 *  - On app start / page open we serve from this cache first so the UI paints
 *    immediately (no 3s+ remote `event_list` round-trip on cold start).
 *  - We never proactively fetch from the runtime on start; remote is only hit
 *    on user pull-to-refresh, on an empty cache, or during a silent refresh.
 *  - Silent refresh (idle / good-network / periodic) updates this store in the
 *    background without disturbing what the user is currently looking at.
 */
class EventListCacheStore(
    private val context: Context,
    private val currentUserIdProvider: () -> Int = { Utils.myUserId() },
) {
    private val dataStore by lazy { context.eventListCacheDataStore }

    private val json = Json { ignoreUnknownKeys = true }

    /** Read cached events for a query key, or null when absent/stale/undecodable. */
    suspend fun getCached(queryKey: String): List<EventInfoForDisplay>? = withContext(Dispatchers.IO) {
        val currentKey = stringPreferencesKey(scopedKey(queryKey))
        val legacyKey = stringPreferencesKey(queryKey)
        val preferences = dataStore.data.first()
        val raw = preferences[currentKey] ?: preferences[legacyKey]
            ?: return@withContext null
        val cached = runCatching {
            json.decodeFromString<List<ManagerEvent>>(raw).map { it.toEventInfoForDisplay() }
        }.getOrNull() ?: return@withContext null
        // Migrate the pre-user-scoped bucket so existing records remain cache-first.
        if (preferences[currentKey] == null) {
            runCatching {
                dataStore.edit { values ->
                    values[currentKey] = raw
                    values.remove(legacyKey)
                }
            }
        }
        cached
    }

    /** Persist events for a query key. Only serializable fields are stored. */
    suspend fun putCached(queryKey: String, events: List<EventInfoForDisplay>) = withContext(Dispatchers.IO) {
        val payload = events.map { it.event }
        runCatching {
            val raw = json.encodeToString(payload)
            dataStore.edit { prefs -> prefs[stringPreferencesKey(scopedKey(queryKey))] = raw }
        }
    }

    /** Drop every cached query bucket (e.g. on clear-history). */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        runCatching { dataStore.edit { it.clear() } }
    }

    internal fun scopedKey(queryKey: String): String =
        buildEventListCacheKey(currentUserIdProvider(), queryKey)
}

internal fun buildEventListCacheKey(userId: Int, queryKey: String): String =
    "user=${userId.coerceAtLeast(0)};$queryKey"

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
