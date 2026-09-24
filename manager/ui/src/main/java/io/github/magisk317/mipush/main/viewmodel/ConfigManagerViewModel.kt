package io.github.magisk317.mipush.main.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.manager.application.ManagerConfigGateway
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.manager.configuration.RemoteConfigurationCatalogSource
import io.github.magisk317.mipush.manager.application.ManagerConfigSyncGateway
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.utils.ConfigDefaults
import io.github.magisk317.mipush.core.configuration.ConfigListItem
import io.github.magisk317.mipush.utils.ConfigRemoteSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ConfigManagerViewModel constructor(
    private val preferenceRepository: PreferenceRepository,
    private val syncGateway: ManagerConfigSyncGateway,
    private val configGateway: ManagerConfigGateway,
    private val context: Application,
    private val configurationCatalogSource: RemoteConfigurationCatalogSource,
    private val iconResourcesUpdateRequester: IconResourcesUpdateRequester,
    private val iconLibrarySource: IconLibrarySource,
) : ViewModel() {
    private companion object {
        const val TAG = "MiPushConfigManager"
        const val ICON_LIBRARY_PAGE_SIZE = 100
        const val ICON_LIBRARY_MAX_ATTEMPTS = 3
        const val ICON_LIBRARY_RETRY_DELAY_MS = 2_000L
        const val ICON_BITMAP_CONCURRENCY = 4
    }

    data class UiState(
        val directoryUri: String? = null,
        val remoteSource: ConfigRemoteSource = ConfigRemoteSource(),
        val lastSyncTime: Long = 0L,
        val items: List<ConfigListItem> = emptyList(),
        val query: String = "",
        val isLoading: Boolean = true,
        val isSyncing: Boolean = false,
        val isUpdatingIcons: Boolean = false,
        val syncCurrent: Int = 0,
        val syncTotal: Int = 0,
        val syncPath: String? = null,
        val remoteError: String? = null,
        val message: String? = null,
        val iconLibraryItems: List<IconLibraryEntry> = emptyList(),
        val iconLibraryNextOffset: Int? = 0,
        val isIconLibraryLoading: Boolean = false,
        val iconLibraryError: String? = null,
        val iconBitmaps: Map<String, ImageBitmap?> = emptyMap(),
    )

    private val iconBitmapGate = Semaphore(ICON_BITMAP_CONCURRENCY)
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var refreshGeneration = 0L
    private var failedRemoteSourceKey: String? = null

    init {
        viewModelScope.launch {
            combine(
                preferenceRepository.configDirectory,
                preferenceRepository.configRemoteRepository,
                preferenceRepository.configRemoteBranch,
                preferenceRepository.configRemoteAccelerator,
            ) { args ->
                val directory = args[0]
                val repository = args[1] as String
                val branch = args[2] as String
                val accelerator = args[3] as String
                RemoteSettings(
                    directoryUri = directory,
                    remoteSource = ConfigRemoteSource(
                        repository = repository,
                        branch = branch,
                        accelerator = accelerator,
                    ),
                )
            }.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        directoryUri = settings.directoryUri,
                        remoteSource = settings.remoteSource,
                    )
                }
                settings.directoryUri?.let { grantXmsfTreePermission(it.toUri()) }
                refreshInternal(forceRemote = false)
            }
        }
        viewModelScope.launch {
            preferenceRepository.lastConfigSyncTime.collectLatest { time ->
                _uiState.update { it.copy(lastSyncTime = time) }
            }
        }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun refresh() {
        viewModelScope.launch { refreshInternal(forceRemote = false) }
    }

    fun updateConfigurationDirectory(uri: Uri) {
        viewModelScope.launch {
            grantXmsfTreePermission(uri)
            preferenceRepository.setConfigDirectory(uri.toString())
            configGateway.loadConfigurations(context)
            _uiState.update { it.copy(message = "配置目录已更新") }
        }
    }

    private fun grantXmsfTreePermission(uri: Uri) {
        runCatching {
            context.grantUriPermission(
                XMSF_PACKAGE_NAME,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
            Logger.withTag(TAG).i { "granted XMSF configuration directory permission uri=$uri" }
        }.onFailure { error ->
            Logger.withTag(TAG).w(error) { "unable to grant XMSF configuration directory permission uri=$uri" }
        }
    }

    fun updateRemoteSource(repository: String, branch: String, accelerator: String) {
        viewModelScope.launch {
            failedRemoteSourceKey = null
            val normalizedRepository = repository.trim()
            val normalizedBranch = branch.trim()
            preferenceRepository.setConfigRemoteSource(
                normalizedRepository.ifBlank { ConfigDefaults.REMOTE_REPOSITORY },
                normalizedBranch.ifBlank { ConfigDefaults.REMOTE_BRANCH },
                accelerator.trim().ifBlank { ConfigDefaults.REMOTE_ACCELERATOR },
            )
            _uiState.update { it.copy(message = "配置源已更新") }
        }
    }

    fun importDocuments(uris: List<Uri>, isIcon: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val treeUri = currentTreeUri()
            if (treeUri == null) {
                _uiState.update { it.copy(message = "请先选择配置目录") }
                return@launch
            }
            _uiState.update { it.copy(isSyncing = true) }
            runCatching {
                syncGateway.importDocuments(treeUri, uris, isIcon)
            }.onSuccess { imported ->
                configGateway.loadConfigurations(context)
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        message = "已导入 $imported 个配置文件",
                    )
                }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        message = error.message ?: error.toString(),
                    )
                }
            }
        }
    }

    fun pullRemote() {
        viewModelScope.launch {
            val treeUri = currentTreeUri()
            if (treeUri == null) {
                _uiState.update { it.copy(message = "请先选择配置目录") }
                return@launch
            }
            _uiState.update { it.copy(isSyncing = true, syncCurrent = 0, syncTotal = 0, syncPath = null) }
            runCatching {
                syncGateway.pullAll(treeUri) { current, total, path ->
                    _uiState.update {
                        it.copy(
                            isSyncing = true,
                            syncCurrent = current,
                            syncTotal = total,
                            syncPath = path,
                        )
                    }
                }
            }.onSuccess { count ->
                failedRemoteSourceKey = null
                configGateway.loadConfigurations(context)
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncCurrent = 0,
                        syncTotal = 0,
                        syncPath = null,
                        message = "已同步 $count 个远端配置",
                    )
                }
                refresh()
            }.onFailure { error ->
                failedRemoteSourceKey = remoteSourceKey(_uiState.value.remoteSource)
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncCurrent = 0,
                        syncTotal = 0,
                        syncPath = null,
                        remoteError = error.message ?: error.toString(),
                        message = error.message ?: error.toString(),
                    )
                }
            }
        }
    }

    /**
     * Pulls the latest ANIP icon resources inside the runtime process. The bundled asset ZIP is
     * only the cold-start fallback; a successful fetch replaces it with the remote release.
     */
    fun updateIconResources() {
        viewModelScope.launch {
            if (_uiState.value.isUpdatingIcons) return@launch
            _uiState.update { it.copy(isUpdatingIcons = true, message = null) }
            val result = runCatching { iconResourcesUpdateRequester.requestIconResourcesUpdate() }
                .getOrElse { error ->
                    IconResourcesUpdateResult(isSuccess = false, detail = error.message ?: error.toString())
                }
            _uiState.update {
                it.copy(
                    isUpdatingIcons = false,
                    message = when {
                        result.isSuccess && result.isUpToDate -> "图标资源已是最新"
                        result.isSuccess -> "图标资源已更新"
                        else -> "图标资源更新失败：${result.detail}"
                    },
                )
            }
        }
    }

    fun reloadConfigurations() {
        viewModelScope.launch {
            failedRemoteSourceKey = null
            configGateway.loadConfigurations(context)
            _uiState.update { it.copy(message = "已重新加载配置") }
            refreshInternal(forceRemote = true)
        }
    }

    /**
     * Loads ANIP icon library metadata pages sequentially until the catalog is complete, so
     * the preview grid can filter/search over the whole library without per-page UI states.
     */
    fun ensureIconLibraryLoaded() {
        if (_uiState.value.isIconLibraryLoading) return
        viewModelScope.launch {
            var attempts = 0
            while (attempts < ICON_LIBRARY_MAX_ATTEMPTS) {
                attempts++
                val next = _uiState.value.iconLibraryNextOffset ?: return@launch
                _uiState.update { it.copy(isIconLibraryLoading = true, iconLibraryError = null) }
                val page = runCatching { iconLibrarySource.loadLibraryPage(next) }
                    .onFailure { Logger.w(throwable = it, tag = TAG) { "icon library page load failed offset=$next" } }
                    .getOrElse { error ->
                        IconLibraryPage(
                            emptyList(),
                            next,
                            "vm caught ${error.javaClass.simpleName}: ${error.message?.take(120)}",
                        )
                    }
                if (page.entries.isEmpty()) {
                    // The runtime answered (or refused); keep the diagnostic visible instead of
                    // silently reporting "not connected", and retry shortly.
                    Logger.w(tag = TAG) {
                        "icon library attempt $attempts/$ICON_LIBRARY_MAX_ATTEMPTS empty: ${page.diagnostic}"
                    }
                    _uiState.update {
                        it.copy(
                            isIconLibraryLoading = false,
                            iconLibraryError = page.diagnostic ?: "empty page, no diagnostic",
                        )
                    }
                    if (attempts < ICON_LIBRARY_MAX_ATTEMPTS) {
                        delay(ICON_LIBRARY_RETRY_DELAY_MS)
                        continue
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        iconLibraryItems = it.iconLibraryItems + page.entries,
                        iconLibraryNextOffset =
                            if (page.entries.size < ICON_LIBRARY_PAGE_SIZE) null else page.nextOffset,
                        isIconLibraryLoading = false,
                        iconLibraryError = if (page.entries.isEmpty()) page.diagnostic else null,
                    )
                }
                if (page.entries.size < ICON_LIBRARY_PAGE_SIZE) return@launch
            }
        }
    }

    /** Fetches (and caches) one preview bitmap from the runtime's ANIP icon library. */
    fun requestIconBitmap(packageName: String) {
        if (_uiState.value.iconBitmaps.containsKey(packageName)) return
        _uiState.update { it.copy(iconBitmaps = it.iconBitmaps + (packageName to null as ImageBitmap?)) }
        viewModelScope.launch {
            // Each icon is a separate write command; cap concurrency so a full page cannot
            // saturate the (8s timeboxed) runtime channel.
            iconBitmapGate.withPermit {
                val bitmap = runCatching { iconLibrarySource.loadIconBitmap(packageName) }.getOrNull()
                _uiState.update { it.copy(iconBitmaps = it.iconBitmaps + (packageName to bitmap)) }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun currentTreeUri(): Uri? {
        return preferenceRepository.configDirectory.first()?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }

    private suspend fun refreshInternal(forceRemote: Boolean) {
        val generation = ++refreshGeneration
        val treeUri = currentTreeUri()
        val remoteSource = _uiState.value.remoteSource
        val remoteSourceKey = remoteSourceKey(remoteSource)
        val skipRemote = !forceRemote && failedRemoteSourceKey == remoteSourceKey
        _uiState.update {
            it.copy(
                isLoading = true,
                remoteError = if (skipRemote) it.remoteError else null,
            )
        }

        val localSnapshot = syncGateway.loadLocalSnapshot(treeUri)
        if (generation != refreshGeneration) return
        _uiState.update {
            it.copy(
                items = localSnapshot.items,
                remoteError = if (skipRemote) it.remoteError else null,
                isLoading = false,
            )
        }
        if (skipRemote) return

        runCatching { syncGateway.loadRemoteSnapshot(treeUri) }
            .onSuccess { snapshot ->
                if (generation != refreshGeneration) return
                if (snapshot.remoteError == null) {
                    failedRemoteSourceKey = null
                } else {
                    failedRemoteSourceKey = remoteSourceKey
                }
                _uiState.update { current ->
                    current.copy(
                        items = if (snapshot.items.isEmpty() && current.items.isNotEmpty()) current.items else snapshot.items,
                        remoteError = snapshot.remoteError,
                    )
                }
            }
            .onFailure { error ->
                if (generation != refreshGeneration) return
                failedRemoteSourceKey = remoteSourceKey
                _uiState.update { it.copy(remoteError = error.message ?: error.toString()) }
            }
    }


    private fun remoteSourceKey(remoteSource: ConfigRemoteSource): String {
        return "${remoteSource.cacheKey}|${remoteSource.accelerator.trim()}"
    }

    private data class RemoteSettings(
        val directoryUri: String?,
        val remoteSource: ConfigRemoteSource,
    )
}
