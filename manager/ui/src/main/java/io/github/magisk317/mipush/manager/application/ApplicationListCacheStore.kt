package io.github.magisk317.mipush.manager.application

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * User and query scoped disk cache for application pages.
 *
 * A corrupt bucket is removed in isolation. Reads never turn a decode or scope failure into an
 * empty successful result, which lets callers keep their last valid in-memory snapshot visible.
 */
class ApplicationListCacheStore(
    private val context: Context,
    private val currentUserIdProvider: () -> Int = { Utils.myUserId() },
) {
    private val dataStore by lazy { context.applicationListCacheDataStore }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getCached(
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean,
    ): CachedApplicationSnapshot? = withContext(Dispatchers.IO) {
        val key = bucketKey(currentUserIdProvider(), query, filterMode, includeSystemApps)
        val preferences = dataStore.data.first()
        val raw = preferences[stringPreferencesKey(key)] ?: return@withContext null
        runCatching { json.decodeFromString<CachedApplicationSnapshot>(raw) }
            .getOrNull()
            ?.takeIf {
                it.schemaVersion == SCHEMA_VERSION &&
                    it.userId == normalizedUserId() &&
                    it.query == query &&
                    it.filterMode == filterMode &&
                    it.includeSystemApps == includeSystemApps &&
                    it.applications.all { application -> application.userId == it.userId }
            }
            ?: run {
                // Isolate only the invalid bucket; unrelated query/filter/user buckets survive.
                dataStore.edit { preferencesToEdit ->
                    preferencesToEdit.remove(stringPreferencesKey(key))
                }
                null
            }
    }

    suspend fun putCached(snapshot: CachedApplicationSnapshot) = withContext(Dispatchers.IO) {
        if (!snapshot.isValid()) return@withContext
        val key = bucketKey(snapshot.userId, snapshot.query, snapshot.filterMode, snapshot.includeSystemApps)
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey(key)] = json.encodeToString(snapshot)
        }
    }

    suspend fun clearForUser(userId: Int = normalizedUserId()) = withContext(Dispatchers.IO) {
        val prefix = "user=${userId.coerceAtLeast(0)};"
        dataStore.edit { preferences ->
            preferences.asMap().keys
                .filter { it.name.startsWith(prefix) }
                .forEach { preferences.remove(it) }
        }
    }

    internal fun normalizedUserId(): Int = currentUserIdProvider().coerceAtLeast(0)

    fun currentUserId(): Int = normalizedUserId()

    companion object {
        const val SCHEMA_VERSION = 1

        internal fun bucketKey(
            userId: Int,
            query: String,
            filterMode: Int,
            includeSystemApps: Boolean,
        ): String = "user=${userId.coerceAtLeast(0)};q=$query;f=$filterMode;s=$includeSystemApps"
    }
}

@Serializable
data class CachedApplicationSnapshot(
    val schemaVersion: Int = ApplicationListCacheStore.SCHEMA_VERSION,
    val userId: Int,
    val query: String,
    val filterMode: Int,
    val includeSystemApps: Boolean,
    val applications: List<ManagerApplication>,
    val totalPkg: Int,
    val total: Int,
    val usingMiPush: Int,
    val notUsingMiPush: Int,
    val registered: Int,
    val notRegistered: Int,
) {
    fun isValid(): Boolean = schemaVersion == ApplicationListCacheStore.SCHEMA_VERSION &&
        userId >= 0 && applications.all { it.userId == userId } &&
        total >= 0 && usingMiPush >= 0 && notUsingMiPush >= 0 &&
        registered >= 0 && notRegistered >= 0 && totalPkg >= 0
}

private val Context.applicationListCacheDataStore by preferencesDataStore(name = "application_list_cache")
