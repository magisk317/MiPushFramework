package io.github.magisk317.mipush.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magisk317.mipush.app.SettingsManager
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.uikit.theme.UiKitStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置 ViewModel
 * 
 * 管理应用设置状态和 UI 交互
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {
    data class ThemeState(
        val mode: Int,
        val uiKitStyle: Int = UiKitStyle.Expressive.value,
        val centerX: Float = -1f,
        val centerY: Float = -1f,
    )

    private val _themeState = MutableStateFlow(ThemeState(0))
    val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()

    val hazeBlurRadius: StateFlow<Int> = preferenceRepository.hazeBlurRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 25)

    val hazeTintAlpha: StateFlow<Float> = preferenceRepository.hazeTintAlpha
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.2f)

    val xmppServer: StateFlow<String?> = preferenceRepository.xmppServer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val configDirectory: StateFlow<String?> = preferenceRepository.configDirectory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val notificationOnRegister: StateFlow<Boolean> = preferenceRepository.notificationOnRegister
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showConfigurationList: StateFlow<Boolean> = preferenceRepository.showConfigurationList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val debugMode: StateFlow<Boolean> = preferenceRepository.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showAllEvents: StateFlow<Boolean> = preferenceRepository.showAllEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isStartForeground: StateFlow<Boolean> = preferenceRepository.isStartForeground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            preferenceRepository.themeMode.collect { mode ->
                val previous = _themeState.value
                if (previous.mode != mode) {
                    _themeState.value = previous.copy(mode = mode)
                }
            }
        }
        viewModelScope.launch {
            preferenceRepository.uiKitStyle.collect { style ->
                val previous = _themeState.value
                if (previous.uiKitStyle != style) {
                    _themeState.value = previous.copy(uiKitStyle = style)
                }
            }
        }
    }

    fun updateHazeBlurRadius(radius: Int) {
        viewModelScope.launch {
            preferenceRepository.setHazeBlurRadius(radius)
        }
    }

    fun updateHazeTintAlpha(alpha: Float) {
        viewModelScope.launch {
            preferenceRepository.setHazeTintAlpha(alpha)
        }
    }

    fun updateXmppServer(host: String) {
        viewModelScope.launch {
            preferenceRepository.setXmppServer(host)
            Utils.getApplication()?.let { app ->
                io.github.magisk317.mipush.network.NetworkPolicyCompat.applyXmppHostOverride(app)
                settingsManager.sendXMPPReconnectRequest(app)
            }
        }
    }

    fun updateConfigDirectory(uri: String) {
        viewModelScope.launch {
            preferenceRepository.setConfigDirectory(uri)
        }
    }

    fun setNotificationOnRegister(enabled: Boolean) {
        viewModelScope.launch { preferenceRepository.setNotificationOnRegister(enabled) }
    }

    fun setShowConfigurationList(enabled: Boolean) {
        viewModelScope.launch { preferenceRepository.setShowConfigurationList(enabled) }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch { preferenceRepository.setDebugMode(enabled) }
    }

    fun setShowAllEvents(enabled: Boolean) {
        viewModelScope.launch { preferenceRepository.setShowAllEvents(enabled) }
    }

    fun setStartForeground(enabled: Boolean) {
        viewModelScope.launch { preferenceRepository.setIsStartForeground(enabled) }
    }

    fun setThemeMode(mode: Int, x: Float = -1f, y: Float = -1f) {
        viewModelScope.launch {
            preferenceRepository.setThemeMode(mode)
            _themeState.value = _themeState.value.copy(mode = mode, centerX = x, centerY = y)
        }
    }

    fun setUiKitStyle(style: Int) {
        viewModelScope.launch {
            preferenceRepository.setUiKitStyle(style)
            _themeState.value = _themeState.value.copy(uiKitStyle = style)
        }
    }

    fun startMiPushServiceAsForegroundService(context: android.content.Context) {
        settingsManager.startMiPushServiceAsForegroundService(context)
    }

    fun notifyMockNotification(context: android.content.Context) {
        settingsManager.notifyMockNotification(context)
    }

    fun clearHistory(context: android.content.Context) {
        settingsManager.clearHistory(context, viewModelScope)
    }

    fun clearLog(context: android.content.Context) {
        settingsManager.clearLog(context)
    }

    fun shareLogs(context: android.content.Context) {
        settingsManager.shareLogs(context)
    }

    fun tryForceRegisterAllApplications(context: android.content.Context) {
        settingsManager.tryForceRegisterAllApplications(context)
    }

    fun getXMPPServerHint(): String = settingsManager.getXMPPServerHint()
}
