package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.manager.ManagerConfigGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class XmppServerUiState(
    val configuredServer: String? = null,
    val isLoaded: Boolean = false,
    val isSaving: Boolean = false,
    val saveResult: XmppServerSaveResult? = null,
)

enum class XmppServerSaveResult {
    SAVED,
    FAILED,
}

class XmppServerViewModel(
    private val configGateway: ManagerConfigGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(XmppServerUiState())
    val uiState: StateFlow<XmppServerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val configuredServer = runCatching { configGateway.getXmppServer() }.getOrNull()
            _uiState.update {
                it.copy(
                    configuredServer = configuredServer,
                    isLoaded = true,
                )
            }
        }
    }

    fun save(rawServer: String) {
        if (!_uiState.value.isLoaded || _uiState.value.isSaving) return
        val normalizedServer = rawServer.trim()
        _uiState.update { it.copy(isSaving = true, saveResult = null) }
        viewModelScope.launch {
            val saved = runCatching {
                configGateway.setXmppServer(normalizedServer)
            }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    configuredServer = if (saved) normalizedServer else it.configuredServer,
                    isSaving = false,
                    saveResult = if (saved) XmppServerSaveResult.SAVED else XmppServerSaveResult.FAILED,
                )
            }
        }
    }

    fun consumeSaveResult() {
        _uiState.update { it.copy(saveResult = null) }
    }
}
