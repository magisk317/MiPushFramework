package com.magisk317.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magisk317.data.DataStoreManager
import com.xiaomi.push.sdk.MyPushMessageHandler
import com.xiaomi.push.sdk.PushMessageProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.magisk317.data.PreferenceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.xiaomi.xmsf.SettingsManager

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

    val iceboxSupported: StateFlow<Boolean> = preferenceRepository.iceboxSupported
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    fun setIceboxSupported(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setIceboxSupported(value)
    }
    
    fun startMiPushServiceAsForegroundService(context: android.content.Context) {
        settingsManager.startMiPushServiceAsForegroundService(context)
    }
    
    fun notifyMockNotification(context: android.content.Context) {
        settingsManager.notifyMockNotification(context)
    }
    
    fun isIceBoxInstalled(): Boolean = settingsManager.isIceBoxInstalled()
    
    fun iceBoxPermissionGranted(context: android.content.Context): Boolean = settingsManager.iceBoxPermissionGranted(context)
    
    fun clearHistory(context: android.content.Context) {
        settingsManager.clearHistory(context, viewModelScope)
    }
    
    fun clearLog(context: android.content.Context) {
        settingsManager.clearLog(context)
    }
}
