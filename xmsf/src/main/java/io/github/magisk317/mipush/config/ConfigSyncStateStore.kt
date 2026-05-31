package io.github.magisk317.mipush.config

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import io.github.magisk317.mipush.utils.ConfigSyncRecord
import io.github.magisk317.mipush.utils.ConfigRemoteSource
import io.github.magisk317.mipush.utils.RemoteConfigCatalog
import io.github.magisk317.mipush.utils.ConfigSyncState
import io.github.magisk317.mipush.common.utils.Utils

class ConfigSyncStateStore constructor(
    private val context: Context,
) {
    constructor() : this(Utils.getApplication()!!)

    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val stateFile: File
        get() = File(context.filesDir, "config-sync/state.json")

    suspend fun getDirectoryRecords(directoryUri: String?): Map<String, ConfigSyncRecord> = withContext(Dispatchers.IO) {
        if (directoryUri.isNullOrBlank()) return@withContext emptyMap()
        loadState().directories[directoryUri].orEmpty()
    }

    suspend fun getCachedCatalog(remoteSource: ConfigRemoteSource): RemoteConfigCatalog? = withContext(Dispatchers.IO) {
        loadState().cachedCatalogs[remoteSource.cacheKey]
    }

    suspend fun upsert(directoryUri: String, record: ConfigSyncRecord) = withContext(Dispatchers.IO) {
        val state = loadState()
        val current = state.directories[directoryUri].orEmpty().toMutableMap()
        current[record.path] = record
        saveState(state.copy(directories = state.directories + (directoryUri to current)))
    }

    suspend fun upsertAll(directoryUri: String, records: Iterable<ConfigSyncRecord>) = withContext(Dispatchers.IO) {
        val state = loadState()
        val current = state.directories[directoryUri].orEmpty().toMutableMap()
        records.forEach { record -> current[record.path] = record }
        saveState(state.copy(directories = state.directories + (directoryUri to current)))
    }

    suspend fun cacheCatalog(remoteSource: ConfigRemoteSource, catalog: RemoteConfigCatalog) = withContext(Dispatchers.IO) {
        val state = loadState()
        saveState(
            state.copy(
                cachedCatalogs = state.cachedCatalogs + (remoteSource.cacheKey to catalog),
            ),
        )
    }

    private fun loadState(): ConfigSyncState {
        if (!stateFile.exists()) return ConfigSyncState()
        return runCatching {
            json.decodeFromString(ConfigSyncState.serializer(), stateFile.readText(Charsets.UTF_8))
        }.getOrDefault(ConfigSyncState())
    }

    private fun saveState(state: ConfigSyncState) {
        stateFile.parentFile?.mkdirs()
        stateFile.writeText(json.encodeToString(ConfigSyncState.serializer(), state), Charsets.UTF_8)
    }
}
