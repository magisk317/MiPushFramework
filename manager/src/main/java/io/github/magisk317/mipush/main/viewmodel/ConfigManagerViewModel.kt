package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.manager.ManagerConfigGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigSyncGateway
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.utils.ConfigDefaults
import io.github.magisk317.mipush.utils.ConfigListItem
import io.github.magisk317.mipush.utils.ConfigRemoteSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConfigManagerViewModel constructor(
    private val preferenceRepository: PreferenceRepository,
    private val syncGateway: ManagerConfigSyncGateway,
    private val configGateway: ManagerConfigGateway,
    private val context: Context,
) : ViewModel() {
    data class UiState(
        val directoryUri: String? = null,
        val remoteSource: ConfigRemoteSource = ConfigRemoteSource(),
        val iconRemoteSource: ConfigRemoteSource = ConfigRemoteSource(),
        val lastSyncTime: Long = 0L,
        val items: List<ConfigListItem> = emptyList(),
        val query: String = "",
        val isLoading: Boolean = true,
        val isSyncing: Boolean = false,
        val syncCurrent: Int = 0,
        val syncTotal: Int = 0,
        val syncPath: String? = null,
        val remoteError: String? = null,
        val message: String? = null,
    )

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
                preferenceRepository.iconRemoteRepository,
                preferenceRepository.iconRemoteBranch,
                preferenceRepository.iconRemoteAccelerator,
            ) { args ->
                val directory = args[0]
                val repository = args[1] as String
                val branch = args[2] as String
                val accelerator = args[3] as String
                val iconRepository = args[4] as String
                val iconBranch = args[5] as String
                val iconAccelerator = args[6] as String
                RemoteSettings(
                    directoryUri = directory,
                    remoteSource = ConfigRemoteSource(
                        repository = repository,
                        branch = branch,
                        accelerator = accelerator,
                    ),
                    iconRemoteSource = ConfigRemoteSource(
                        repository = iconRepository,
                        branch = iconBranch,
                        accelerator = iconAccelerator,
                    ),
                )
            }.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        directoryUri = settings.directoryUri,
                        remoteSource = settings.remoteSource,
                        iconRemoteSource = settings.iconRemoteSource,
                    )
                }
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
            preferenceRepository.setConfigDirectory(uri.toString())
            configGateway.loadConfigurations(context)
            _uiState.update { it.copy(message = "配置目录已更新") }
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

    fun updateIconRemoteSource(repository: String, branch: String, accelerator: String) {
        viewModelScope.launch {
            failedRemoteSourceKey = null
            val normalizedRepository = repository.trim()
            val normalizedBranch = branch.trim()
            preferenceRepository.setIconRemoteSource(
                normalizedRepository.ifBlank { ConfigDefaults.REMOTE_REPOSITORY },
                normalizedBranch.ifBlank { ConfigDefaults.REMOTE_BRANCH },
                accelerator.trim().ifBlank { ConfigDefaults.REMOTE_ACCELERATOR },
            )
            _uiState.update { it.copy(message = "图标源已更新") }
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

    fun reloadConfigurations() {
        viewModelScope.launch {
            failedRemoteSourceKey = null
            configGateway.loadConfigurations(context)
            _uiState.update { it.copy(message = "已重新加载配置") }
            refreshInternal(forceRemote = true)
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
                failedRemoteSourceKey = null
                _uiState.update {
                    it.copy(
                        items = snapshot.items,
                        remoteError = null,
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
        val iconRemoteSource: ConfigRemoteSource,
    )
}
