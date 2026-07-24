package io.github.magisk317.mipush.manager.configuration.sync

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
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
        stateLock.withLock { loadState().directories[directoryUri].orEmpty() }
    }

    suspend fun getCachedCatalog(remoteSource: ConfigRemoteSource): RemoteConfigCatalog? = withContext(Dispatchers.IO) {
        stateLock.withLock { loadState().cachedCatalogs[remoteSource.cacheKey] }
    }

    suspend fun upsert(directoryUri: String, record: ConfigSyncRecord) = withContext(Dispatchers.IO) {
        stateLock.withLock {
            val state = loadState()
            val current = state.directories[directoryUri].orEmpty().toMutableMap()
            current[record.path] = record
            saveState(state.copy(directories = state.directories + (directoryUri to current)))
        }
    }

    suspend fun upsertAll(directoryUri: String, records: Iterable<ConfigSyncRecord>) = withContext(Dispatchers.IO) {
        stateLock.withLock {
            val state = loadState()
            val current = state.directories[directoryUri].orEmpty().toMutableMap()
            records.forEach { record -> current[record.path] = record }
            saveState(state.copy(directories = state.directories + (directoryUri to current)))
        }
    }

    suspend fun cacheCatalog(remoteSource: ConfigRemoteSource, catalog: RemoteConfigCatalog) = withContext(Dispatchers.IO) {
        stateLock.withLock {
            val state = loadState()
            saveState(
                state.copy(
                    cachedCatalogs = state.cachedCatalogs + (remoteSource.cacheKey to catalog),
                ),
            )
        }
    }

    private fun loadState(): ConfigSyncState {
        if (!stateFile.exists()) return ConfigSyncState()
        if (stateFile.length() !in 1..MAX_STATE_FILE_BYTES) return ConfigSyncState()
        return runCatching {
            AtomicFile(stateFile).openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                json.decodeFromString(ConfigSyncState.serializer(), reader.readText())
            }
        }.getOrDefault(ConfigSyncState())
    }

    private fun saveState(state: ConfigSyncState) {
        stateFile.parentFile?.mkdirs()
        val bytes = json.encodeToString(ConfigSyncState.serializer(), state).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STATE_FILE_BYTES) { "Config sync state exceeds size limit" }
        val atomicFile = AtomicFile(stateFile)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (failure: IOException) {
            failWrite(atomicFile, output, failure)
        }
    }

    private fun failWrite(atomicFile: AtomicFile, output: FileOutputStream, failure: Exception): Nothing {
        atomicFile.failWrite(output)
        throw failure
    }

    companion object {
        private const val MAX_STATE_FILE_BYTES = 8L * 1024L * 1024L
        private val stateLock = Mutex()
    }
}
