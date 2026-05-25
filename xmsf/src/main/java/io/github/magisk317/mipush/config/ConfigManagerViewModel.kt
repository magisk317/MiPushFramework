package io.github.magisk317.mipush.config

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.app.ConfigCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConfigManagerViewModel constructor(
    private val preferenceRepository: PreferenceRepository,
    private val syncRepository: ConfigSyncRepository,
    private val configCenter: ConfigCenter,
    private val context: Context,
) : ViewModel() {
    data class UiState(
        val directoryUri: String? = null,
        val remoteSource: ConfigRemoteSource = ConfigRemoteSource(),
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

    init {
        viewModelScope.launch {
            combine(
                preferenceRepository.configDirectory,
                preferenceRepository.configRemoteRepository,
                preferenceRepository.configRemoteBranch,
            ) { directory, repository, branch ->
                Triple(directory, repository, branch)
            }.collectLatest { (directory, repository, branch) ->
                _uiState.update {
                    it.copy(
                        directoryUri = directory,
                        remoteSource = ConfigRemoteSource(
                            repository = repository,
                            branch = branch,
                        ),
                    )
                }
                refreshInternal()
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
        viewModelScope.launch { refreshInternal() }
    }

    fun updateConfigurationDirectory(uri: Uri) {
        viewModelScope.launch {
            preferenceRepository.setConfigDirectory(uri.toString())
            configCenter.loadConfigurations(context)
            _uiState.update { it.copy(message = "配置目录已更新") }
        }
    }

    fun updateRemoteSource(repository: String, branch: String) {
        viewModelScope.launch {
            preferenceRepository.setConfigRemoteRepository(
                repository.ifBlank { ConfigCatalogService.REMOTE_REPOSITORY },
            )
            preferenceRepository.setConfigRemoteBranch(
                branch.ifBlank { ConfigCatalogService.REMOTE_BRANCH },
            )
            _uiState.update { it.copy(message = "远端源已更新") }
        }
    }

    fun importDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val treeUri = currentTreeUri()
            if (treeUri == null) {
                _uiState.update { it.copy(message = "请先选择配置目录") }
                return@launch
            }
            _uiState.update { it.copy(isSyncing = true) }
            runCatching {
                syncRepository.importDocuments(treeUri, uris)
            }.onSuccess { imported ->
                configCenter.loadConfigurations(context)
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
                syncRepository.pullAll(treeUri) { current, total, path ->
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
                configCenter.loadConfigurations(context)
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
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncCurrent = 0,
                        syncTotal = 0,
                        syncPath = null,
                        message = error.message ?: error.toString(),
                    )
                }
            }
        }
    }

    fun reloadConfigurations() {
        viewModelScope.launch {
            configCenter.loadConfigurations(context)
            _uiState.update { it.copy(message = "已重新加载配置") }
            refresh()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun currentTreeUri(): Uri? {
        return preferenceRepository.configDirectory.first()?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }

    private suspend fun refreshInternal() {
        val generation = ++refreshGeneration
        val treeUri = currentTreeUri()
        _uiState.update { it.copy(isLoading = true, remoteError = null) }

        val localSnapshot = syncRepository.loadLocalSnapshot(treeUri)
        if (generation != refreshGeneration) return
        _uiState.update {
            it.copy(
                items = localSnapshot.items,
                remoteError = null,
                isLoading = false,
            )
        }

        runCatching { syncRepository.loadRemoteSnapshot(treeUri) }
            .onSuccess { snapshot ->
                if (generation != refreshGeneration) return
                _uiState.update {
                    it.copy(
                        items = snapshot.items,
                        remoteError = null,
                    )
                }
            }
            .onFailure { error ->
                if (generation != refreshGeneration) return
                _uiState.update { it.copy(remoteError = error.message ?: error.toString()) }
            }
    }
}
