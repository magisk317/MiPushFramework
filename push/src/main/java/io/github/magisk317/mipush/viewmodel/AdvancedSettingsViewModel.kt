package io.github.magisk317.mipush.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magisk317.mipush.app.SettingsManager
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.framework.sdk.PushMessageProcessor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 高级设置 ViewModel
 * 
 * 管理高级设置选项和推送消息处理
 */
@HiltViewModel
class AdvancedSettingsViewModel @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
    private val settingsManager: SettingsManager,
    private val pushMessageProcessor: PushMessageProcessor
) : ViewModel() {
    val notificationOnRegister: StateFlow<Boolean> = preferenceRepository.notificationOnRegister
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showConfigurationList: StateFlow<Boolean> = preferenceRepository.showConfigurationList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val debugMode: StateFlow<Boolean> = preferenceRepository.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showAllEvents: StateFlow<Boolean> = preferenceRepository.showAllEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isStartForeground: StateFlow<Boolean> = preferenceRepository.isStartForeground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val accessMode: StateFlow<String> = preferenceRepository.accessMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0")

    fun setNotificationOnRegister(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setNotificationOnRegister(value)
    }

    fun setShowConfigurationList(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setShowConfigurationList(value)
    }

    fun setDebugMode(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setDebugMode(value)
    }

    fun setShowAllEvents(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setShowAllEvents(value)
    }

    fun setStartForeground(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setIsStartForeground(value)
    }

    fun setAccessMode(index: Int) = viewModelScope.launch {
        preferenceRepository.setAccessMode(index.toString())
        pushMessageProcessor.resetTopActivityCache()
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
}
