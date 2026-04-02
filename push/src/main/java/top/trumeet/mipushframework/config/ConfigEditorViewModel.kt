package top.trumeet.mipushframework.config

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magisk317.data.PreferenceRepository
import com.xiaomi.xmsf.utils.ConfigCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ConfigEditorViewModel @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
    private val syncRepository: ConfigSyncRepository,
    private val configCenter: ConfigCenter,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    data class UiState(
        val path: String = "",
        val directoryUri: String? = null,
        val localContent: ConfigDocumentContent? = null,
        val remoteContent: ConfigDocumentContent? = null,
        val localMeta: LocalConfigFile? = null,
        val remoteMeta: RemoteConfigFile? = null,
        val selectedSource: ConfigContentSource = ConfigContentSource.LOCAL,
        val isEditing: Boolean = false,
        val draft: String = "",
        val validationError: String? = null,
        val remoteError: String? = null,
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val message: String? = null,
    ) {
        val hasDirectory: Boolean get() = !directoryUri.isNullOrBlank()
        val hasLocal: Boolean get() = localContent != null
        val hasRemote: Boolean get() = remoteContent != null
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    fun load(path: String) {
        if (_uiState.value.path == path && !_uiState.value.isLoading) return
        viewModelScope.launch {
            val directoryUri = preferenceRepository.configDirectory.first()
            _uiState.update {
                it.copy(
                    path = path,
                    directoryUri = directoryUri,
                    isLoading = true,
                    remoteError = null,
                    message = null,
                    isEditing = false,
                )
            }
            val snapshot = syncRepository.readEditorSnapshot(
                treeUri = directoryUri?.takeIf { it.isNotBlank() }?.let(Uri::parse),
                path = path,
            )
            val preferredSource = when {
                snapshot.local != null -> ConfigContentSource.LOCAL
                snapshot.remote != null -> ConfigContentSource.REMOTE
                else -> ConfigContentSource.LOCAL
            }
            _uiState.update {
                it.copy(
                    localContent = snapshot.local,
                    remoteContent = snapshot.remote,
                    localMeta = snapshot.localMeta,
                    remoteMeta = snapshot.remoteMeta,
                    selectedSource = preferredSource,
                    remoteError = snapshot.remoteError,
                    isLoading = false,
                    validationError = snapshot.local?.validation?.errorMessage,
                    draft = "",
                )
            }
        }
    }

    fun selectSource(source: ConfigContentSource) {
        _uiState.update { it.copy(selectedSource = source, isEditing = false, draft = "", validationError = null) }
    }

    fun beginEdit() {
        _uiState.update { state ->
            val baseText = when {
                state.localContent != null -> state.localContent.displayText
                state.remoteContent != null -> state.remoteContent.displayText
                else -> ""
            }
            state.copy(
                selectedSource = ConfigContentSource.LOCAL,
                isEditing = true,
                draft = baseText,
                validationError = null,
            )
        }
    }

    fun updateDraft(value: String) {
        _uiState.update { it.copy(draft = value, validationError = null) }
    }

    fun cancelEdit() {
        _uiState.update { it.copy(isEditing = false, draft = "", validationError = null) }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val treeUri = state.directoryUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            if (treeUri == null) {
                _uiState.update { it.copy(message = "请先选择配置目录") }
                return@launch
            }
            val validation = ConfigJsonSupport.validateAndFormat(state.draft)
            if (!validation.valid || validation.formatted == null) {
                _uiState.update { it.copy(validationError = validation.errorMessage ?: "JSON 无法解析") }
                return@launch
            }
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                syncRepository.saveLocal(treeUri, state.path, validation.formatted)
            }.onSuccess {
                configCenter.loadConfigurations(context)
                _uiState.update { it.copy(isSaving = false, isEditing = false, draft = "", message = "配置已保存") }
                load(state.path)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = error.message ?: error.toString(),
                    )
                }
            }
        }
    }

    fun resetToRemote() {
        viewModelScope.launch {
            val state = _uiState.value
            val treeUri = state.directoryUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            if (treeUri == null) {
                _uiState.update { it.copy(message = "请先选择配置目录") }
                return@launch
            }
            if (state.remoteMeta == null) {
                _uiState.update { it.copy(message = "远端没有可用配置") }
                return@launch
            }
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                syncRepository.resetToRemote(treeUri, state.path)
            }.onSuccess {
                configCenter.loadConfigurations(context)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isEditing = false,
                        draft = "",
                        message = "已恢复远端配置",
                    )
                }
                load(state.path)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = error.message ?: error.toString(),
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
