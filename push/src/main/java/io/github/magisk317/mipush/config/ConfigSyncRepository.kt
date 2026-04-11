package io.github.magisk317.mipush.config

import android.net.Uri
import com.magisk317.data.PreferenceRepository
import com.magisk317.data.dataStore
import javax.inject.Inject
import javax.inject.Singleton
import io.github.magisk317.mipush.common.utils.Utils

@Singleton
class ConfigSyncRepository @Inject constructor(
    private val catalogService: ConfigCatalogService,
    private val localConfigRepository: LocalConfigRepository,
    private val syncStateStore: ConfigSyncStateStore,
    private val preferenceRepository: PreferenceRepository,
) {
    constructor() : this(
        ConfigCatalogService(),
        LocalConfigRepository(),
        ConfigSyncStateStore(),
        PreferenceRepository(Utils.getApplication()!!.dataStore),
    )

    init {
        try {
            io.github.magisk317.mipush.common.utils.Singleton.reset(this)
        } catch (_: Throwable) {}
    }

    suspend fun loadLocalSnapshot(treeUri: Uri?): ConfigListSnapshot {
        val localFiles = localConfigRepository.listLocalFiles(treeUri)
        val remoteSource = catalogService.getRemoteSource()
        val cachedCatalog = syncStateStore.getCachedCatalog(remoteSource)
        val records = syncStateStore.getDirectoryRecords(treeUri?.toString())
        return ConfigListSnapshot(
            catalog = cachedCatalog,
            items = mergeConfigEntries(
                remoteFiles = cachedCatalog?.files.orEmpty(),
                localFiles = localFiles,
                syncRecords = records,
            ),
        )
    }

    suspend fun loadRemoteSnapshot(treeUri: Uri?): ConfigListSnapshot {
        val localFiles = localConfigRepository.listLocalFiles(treeUri)
        val remoteSource = catalogService.getRemoteSource()
        val catalog = catalogService.fetchCatalog()
        syncStateStore.cacheCatalog(remoteSource, catalog)
        val records = syncStateStore.getDirectoryRecords(treeUri?.toString())
        return ConfigListSnapshot(
            catalog = catalog,
            items = mergeConfigEntries(
                remoteFiles = catalog.files,
                localFiles = localFiles,
                syncRecords = records,
            ),
        )
    }

    suspend fun readLocalEditorSnapshot(treeUri: Uri?, path: String): ConfigEditorSnapshot {
        val localMeta = localConfigRepository.listLocalFiles(treeUri).firstOrNull { it.path == path }
        val localContent = localConfigRepository.readLocalFile(treeUri, path)
        val remoteCatalog = syncStateStore.getCachedCatalog(catalogService.getRemoteSource())
        val remoteMeta = remoteCatalog?.files?.firstOrNull { it.path == path }
        return ConfigEditorSnapshot(
            path = path,
            local = localContent,
            remote = null,
            remoteMeta = remoteMeta,
            localMeta = localMeta,
        )
    }

    suspend fun readRemoteEditorSnapshot(treeUri: Uri?, path: String): ConfigEditorSnapshot {
        val localMeta = localConfigRepository.listLocalFiles(treeUri).firstOrNull { it.path == path }
        val localContent = localConfigRepository.readLocalFile(treeUri, path)
        val remoteSource = catalogService.getRemoteSource()
        val remoteCatalog = catalogService.fetchCatalog()
        syncStateStore.cacheCatalog(remoteSource, remoteCatalog)
        val remoteMeta = remoteCatalog.files.firstOrNull { it.path == path }
        val remoteTextResult = if (remoteMeta != null) runCatching { catalogService.fetchRemoteFile(path) } else null
        val remoteRaw = remoteTextResult?.getOrNull()
        val remoteValidation = remoteRaw?.let { ConfigJsonSupport.validateAndFormat(it) }
        return ConfigEditorSnapshot(
            path = path,
            local = localContent,
            remote = remoteRaw?.let {
                ConfigDocumentContent(
                    rawText = it,
                    displayText = remoteValidation?.formatted ?: it,
                    validation = remoteValidation ?: JsonValidationResult(valid = true, formatted = it),
                )
            },
            remoteMeta = remoteMeta,
            localMeta = localMeta,
            remoteError = remoteTextResult?.exceptionOrNull()?.message,
        )
    }

    suspend fun pullAll(
        treeUri: Uri,
        onProgress: ((current: Int, total: Int, path: String) -> Unit)? = null,
    ): Int {
        val remoteSource = catalogService.getRemoteSource()
        val catalog = catalogService.fetchCatalog()
        syncStateStore.cacheCatalog(remoteSource, catalog)
        val written = mutableListOf<ConfigSyncRecord>()
        val now = System.currentTimeMillis()
        val total = catalog.files.size
        for ((index, remote) in catalog.files.withIndex()) {
            val content = ConfigJsonSupport.formatOrOriginal(catalogService.fetchRemoteFile(remote.path))
            val local = localConfigRepository.writeLocalFile(treeUri, remote.path, content)
            onProgress?.invoke(index + 1, total, remote.path)
            written += ConfigSyncRecord(
                path = remote.path,
                remoteSha = remote.sha,
                localSha = local.sha,
                syncedAt = now,
            )
        }
        syncStateStore.upsertAll(treeUri.toString(), written)
        preferenceRepository.setLastConfigSyncTime(now)
        return written.size
    }

    suspend fun importDocuments(treeUri: Uri, uris: List<Uri>): Int {
        val imported = localConfigRepository.importDocuments(treeUri, uris)
        val existingRecords = syncStateStore.getDirectoryRecords(treeUri.toString())
        syncStateStore.upsertAll(
            treeUri.toString(),
            imported.map { file ->
                val previous = existingRecords[file.path]
                ConfigSyncRecord(
                    path = file.path,
                    remoteSha = previous?.remoteSha,
                    localSha = file.sha,
                    syncedAt = previous?.syncedAt ?: 0L,
                )
            },
        )
        return imported.size
    }

    suspend fun saveLocal(treeUri: Uri, path: String, content: String): LocalConfigFile {
        val local = localConfigRepository.writeLocalFile(treeUri, path, content)
        val existing = syncStateStore.getDirectoryRecords(treeUri.toString())[path]
        syncStateStore.upsert(
            treeUri.toString(),
            ConfigSyncRecord(
                path = path,
                remoteSha = existing?.remoteSha,
                localSha = local.sha,
                syncedAt = existing?.syncedAt ?: 0L,
            ),
        )
        return local
    }

    suspend fun resetToRemote(treeUri: Uri, path: String): LocalConfigFile {
        val remoteSource = catalogService.getRemoteSource()
        val catalog = catalogService.fetchCatalog()
        syncStateStore.cacheCatalog(remoteSource, catalog)
        val remote = requireNotNull(catalog.files.firstOrNull { it.path == path }) {
            "Remote configuration not found: $path"
        }
        val content = ConfigJsonSupport.formatOrOriginal(catalogService.fetchRemoteFile(path))
        val local = localConfigRepository.writeLocalFile(treeUri, path, content)
        val now = System.currentTimeMillis()
        syncStateStore.upsert(
            treeUri.toString(),
            ConfigSyncRecord(
                path = path,
                remoteSha = remote.sha,
                localSha = local.sha,
                syncedAt = now,
            ),
        )
        preferenceRepository.setLastConfigSyncTime(now)
        return local
    }

    suspend fun resolvePackageConfigPath(packageName: String, treeUri: Uri?): String? {
        val localPaths = localConfigRepository.listLocalFiles(treeUri).map { it.path }
        val remoteSource = catalogService.getRemoteSource()
        val cachedPaths = syncStateStore.getCachedCatalog(remoteSource)?.files?.map { it.path }.orEmpty()
        val remotePaths = runCatching {
            val catalog = catalogService.fetchCatalog()
            syncStateStore.cacheCatalog(remoteSource, catalog)
            catalog.files.map { it.path }
        }.getOrDefault(cachedPaths)
        return guessPackageConfigPath(packageName, localPaths + remotePaths)
    }
}
