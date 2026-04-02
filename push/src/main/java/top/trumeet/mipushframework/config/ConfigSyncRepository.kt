package top.trumeet.mipushframework.config

import android.net.Uri
import com.magisk317.data.PreferenceRepository
import com.magisk317.data.dataStore
import javax.inject.Inject
import javax.inject.Singleton
import top.trumeet.common.utils.Utils

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
            com.magisk317.utils.Singleton.reset(this)
        } catch (_: Throwable) {}
    }

    suspend fun loadSnapshot(treeUri: Uri?): ConfigListSnapshot {
        val localFiles = localConfigRepository.listLocalFiles(treeUri)
        val remoteResult = runCatching { catalogService.fetchCatalog() }
        val records = syncStateStore.getDirectoryRecords(treeUri?.toString())
        return ConfigListSnapshot(
            catalog = remoteResult.getOrNull(),
            items = mergeConfigEntries(
                remoteFiles = remoteResult.getOrNull()?.files.orEmpty(),
                localFiles = localFiles,
                syncRecords = records,
            ),
            remoteError = remoteResult.exceptionOrNull()?.message,
        )
    }

    suspend fun readEditorSnapshot(treeUri: Uri?, path: String): ConfigEditorSnapshot {
        val localMeta = localConfigRepository.listLocalFiles(treeUri).firstOrNull { it.path == path }
        val localContent = localConfigRepository.readLocalFile(treeUri, path)
        val remoteCatalog = runCatching { catalogService.fetchCatalog() }.getOrNull()
        val remoteMeta = remoteCatalog?.files?.firstOrNull { it.path == path }
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
        val catalog = catalogService.fetchCatalog()
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
        val catalog = catalogService.fetchCatalog()
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
        val remotePaths = runCatching { catalogService.fetchCatalog().files.map { it.path } }.getOrDefault(emptyList())
        return guessPackageConfigPath(packageName, localPaths + remotePaths)
    }
}
