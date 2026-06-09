package io.github.magisk317.mipush.config

import android.net.Uri
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.utils.ConfigJsonSupport
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.utils.ConfigDocumentContent
import io.github.magisk317.mipush.utils.JsonValidationResult
import io.github.magisk317.mipush.utils.ConfigSyncRecord

class ConfigSyncRepository constructor(
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

    suspend fun loadLocalSnapshot(treeUri: Uri?): ConfigListSnapshot {
        val localFiles = localConfigRepository.listLocalFiles(treeUri)
        val remoteSource = catalogService.getRemoteSource()
        val iconRemoteSource = catalogService.getIconRemoteSource()
        val cachedCatalog = syncStateStore.getCachedCatalog(remoteSource)
        val cachedIconCatalog = syncStateStore.getCachedCatalog(iconRemoteSource)
        val records = syncStateStore.getDirectoryRecords(treeUri?.toString())

        val mappedIconFiles = cachedIconCatalog?.files?.map { it.copy(path = "icon/${it.path.replace('/', '_')}") }.orEmpty()
        val combinedRemoteFiles = cachedCatalog?.files.orEmpty() + mappedIconFiles

        return ConfigListSnapshot(
            catalog = cachedCatalog,
            items = mergeConfigEntries(
                remoteFiles = combinedRemoteFiles,
                localFiles = localFiles,
                syncRecords = records,
            ),
        )
    }

    suspend fun loadRemoteSnapshot(treeUri: Uri?): ConfigListSnapshot {
        val localFiles = localConfigRepository.listLocalFiles(treeUri)
        val remoteSource = catalogService.getRemoteSource()
        val iconRemoteSource = catalogService.getIconRemoteSource()
        val catalog = catalogService.fetchCatalog(remoteSource)
        val iconCatalog = catalogService.fetchCatalog(iconRemoteSource)
        syncStateStore.cacheCatalog(remoteSource, catalog)
        syncStateStore.cacheCatalog(iconRemoteSource, iconCatalog)
        val records = syncStateStore.getDirectoryRecords(treeUri?.toString())

        val mappedIconFiles = iconCatalog.files.map { it.copy(path = "icon/${it.path.replace('/', '_')}") }
        val combinedRemoteFiles = catalog.files + mappedIconFiles

        return ConfigListSnapshot(
            catalog = catalog,
            items = mergeConfigEntries(
                remoteFiles = combinedRemoteFiles,
                localFiles = localFiles,
                syncRecords = records,
            ),
        )
    }

    suspend fun readLocalEditorSnapshot(treeUri: Uri?, path: String): ConfigEditorSnapshot {
        val localMeta = localConfigRepository.listLocalFiles(treeUri).firstOrNull { it.path == path }
        val localContent = localConfigRepository.readLocalFile(treeUri, path)
        val isIcon = path.startsWith("icon/")
        val remoteSource = if (isIcon) catalogService.getIconRemoteSource() else catalogService.getRemoteSource()
        val remoteCatalog = syncStateStore.getCachedCatalog(remoteSource)
        val remotePath = if (isIcon) remoteCatalog?.files?.firstOrNull { "icon/${it.path.replace('/', '_')}" == path }?.path ?: path.removePrefix("icon/") else path
        val remoteMeta = remoteCatalog?.files?.firstOrNull { it.path == remotePath }?.let { if (isIcon) it.copy(path = path) else it }
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
        val isIcon = path.startsWith("icon/")
        val remoteSource = if (isIcon) catalogService.getIconRemoteSource() else catalogService.getRemoteSource()
        val remoteCatalog = catalogService.fetchCatalog(remoteSource)
        syncStateStore.cacheCatalog(remoteSource, remoteCatalog)
        val remotePath = if (isIcon) remoteCatalog.files.firstOrNull { "icon/${it.path.replace('/', '_')}" == path }?.path ?: path.removePrefix("icon/") else path
        val remoteMeta = remoteCatalog.files.firstOrNull { it.path == remotePath }?.let { if (isIcon) it.copy(path = path) else it }
        val remoteTextResult = if (remoteMeta != null) runCatching { catalogService.fetchRemoteFile(remoteSource, remotePath) } else null
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
        val localFiles = localConfigRepository.listLocalFiles(treeUri)
        val existingRecords = syncStateStore.getDirectoryRecords(treeUri.toString())
        val remoteSource = catalogService.getRemoteSource()
        val iconRemoteSource = catalogService.getIconRemoteSource()
        val catalog = catalogService.fetchCatalog(remoteSource)
        val iconCatalog = catalogService.fetchCatalog(iconRemoteSource)
        syncStateStore.cacheCatalog(remoteSource, catalog)
        syncStateStore.cacheCatalog(iconRemoteSource, iconCatalog)
        
        val written = mutableListOf<ConfigSyncRecord>()
        val now = System.currentTimeMillis()
        val total = catalog.files.size + iconCatalog.files.size
        var current = 0

        for (remote in catalog.files) {
            val path = remote.path
            val record = existingRecords[path]
            val localFile = localFiles.find { it.path == path }
            
            // Skip if perfectly in sync
            if (record != null && record.remoteSha == remote.sha && localFile != null && localFile.sha == record.localSha) {
                current++
                onProgress?.invoke(current, total, path)
                written += record
                continue
            }

            val content = ConfigJsonSupport.formatOrOriginal(catalogService.fetchRemoteFile(remoteSource, path))
            val local = localConfigRepository.writeLocalFile(treeUri, path, content)
            current++
            onProgress?.invoke(current, total, path)
            written += ConfigSyncRecord(
                path = path,
                remoteSha = remote.sha,
                localSha = local.sha,
                syncedAt = now,
            )
        }

        for (remote in iconCatalog.files) {
            val localPath = "icon/${remote.path.replace('/', '_')}"
            val record = existingRecords[localPath]
            val localFile = localFiles.find { it.path == localPath }
            
            // Skip if perfectly in sync
            if (record != null && record.remoteSha == remote.sha && localFile != null && localFile.sha == record.localSha) {
                current++
                onProgress?.invoke(current, total, localPath)
                written += record
                continue
            }

            val content = ConfigJsonSupport.formatOrOriginal(catalogService.fetchRemoteFile(iconRemoteSource, remote.path))
            val local = localConfigRepository.writeLocalFile(treeUri, localPath, content)
            current++
            onProgress?.invoke(current, total, localPath)
            written += ConfigSyncRecord(
                path = localPath,
                remoteSha = remote.sha,
                localSha = local.sha,
                syncedAt = now,
            )
        }

        syncStateStore.upsertAll(treeUri.toString(), written)
        preferenceRepository.setLastConfigSyncTime(now)
        return written.size
    }

    suspend fun importDocuments(treeUri: Uri, uris: List<Uri>, isIcon: Boolean = false): Int {
        val imported = localConfigRepository.importDocuments(treeUri, uris, isIcon)
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
        val isIcon = path.startsWith("icon/")
        val remoteSource = if (isIcon) catalogService.getIconRemoteSource() else catalogService.getRemoteSource()
        val catalog = catalogService.fetchCatalog(remoteSource)
        syncStateStore.cacheCatalog(remoteSource, catalog)
        val remotePath = if (isIcon) catalog.files.firstOrNull { "icon/${it.path.replace('/', '_')}" == path }?.path ?: path.removePrefix("icon/") else path
        val remote = requireNotNull(catalog.files.firstOrNull { it.path == remotePath }) {
            "Remote configuration not found: $path"
        }
        val content = ConfigJsonSupport.formatOrOriginal(catalogService.fetchRemoteFile(remoteSource, remotePath))
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
        val iconRemoteSource = catalogService.getIconRemoteSource()
        
        val cachedConfigPaths = syncStateStore.getCachedCatalog(remoteSource)?.files?.map { it.path }.orEmpty()
        val cachedIconPaths = syncStateStore.getCachedCatalog(iconRemoteSource)?.files?.map { "icon/${it.path.replace('/', '_')}" }.orEmpty()
        
        val remotePaths = runCatching {
            val catalog = catalogService.fetchCatalog(remoteSource)
            val iconCatalog = catalogService.fetchCatalog(iconRemoteSource)
            syncStateStore.cacheCatalog(remoteSource, catalog)
            syncStateStore.cacheCatalog(iconRemoteSource, iconCatalog)
            catalog.files.map { it.path } + iconCatalog.files.map { "icon/${it.path.replace('/', '_')}" }
        }.getOrDefault(cachedConfigPaths + cachedIconPaths)
        
        return guessPackageConfigPath(packageName, localPaths + remotePaths)
    }
}
