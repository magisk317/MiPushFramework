package com.magisk317.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magisk317.network.NetworkPolicyCompat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.magisk317.data.DataStoreManager
import top.trumeet.common.utils.Utils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.magisk317.data.PreferenceRepository

import com.xiaomi.xmsf.SettingsManager

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {
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

    val accessMode: StateFlow<String> = preferenceRepository.accessMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0")

    val iceboxSupported: StateFlow<Boolean> = preferenceRepository.iceboxSupported
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    fun previewHazeBlurRadius(radius: Int?) {
        viewModelScope.launch {
            DataStoreManager.previewHazeBlurRadius(radius)
        }
    }

    fun previewHazeTintAlpha(alpha: Float?) {
        viewModelScope.launch {
            DataStoreManager.previewHazeTintAlpha(alpha)
        }
    }

    fun updateXmppServer(host: String) {
        viewModelScope.launch {
            preferenceRepository.setXmppServer(host)
            Utils.getApplication()?.let { app ->
                com.magisk317.network.NetworkPolicyCompat.applyXmppHostOverride(app)
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

    fun setAccessMode(mode: Int) {
        viewModelScope.launch { preferenceRepository.setAccessMode(mode.toString()) }
    }

    fun setIceboxSupported(supported: Boolean) {
        viewModelScope.launch { preferenceRepository.setIceboxSupported(supported) }
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

    fun isIceBoxInstalled(): Boolean = settingsManager.isIceBoxInstalled()

    fun iceBoxPermissionGranted(context: android.content.Context): Boolean =
        settingsManager.iceBoxPermissionGranted(context)
    
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
