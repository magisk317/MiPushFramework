package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.data.PreferenceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.magisk317.mipush.app.SettingsManager

class AdvancedSettingsViewModel constructor(
    private val preferenceRepository: PreferenceRepository,
    private val settingsManager: SettingsManager,
) : ViewModel() {
    val debugMode: StateFlow<Boolean> = preferenceRepository.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showAllEvents: StateFlow<Boolean> = preferenceRepository.showAllEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isStartForeground: StateFlow<Boolean> = preferenceRepository.isStartForeground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val keepAliveOomAdj: StateFlow<Boolean> = preferenceRepository.keepAliveOomAdj
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val keepAliveAntiKill: StateFlow<Boolean> = preferenceRepository.keepAliveAntiKill
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val keepAliveStandbyBypass: StateFlow<Boolean> = preferenceRepository.keepAliveStandbyBypass
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val keepAliveDozeBypass: StateFlow<Boolean> = preferenceRepository.keepAliveDozeBypass
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val accessMode: StateFlow<String> = preferenceRepository.accessMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0")

    fun setDebugMode(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setDebugMode(value)
    }

    fun setShowAllEvents(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setShowAllEvents(value)
    }

    fun setStartForeground(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setIsStartForeground(value)
    }

    fun setKeepAliveOomAdj(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setKeepAliveOomAdj(value)
    }

    fun setKeepAliveAntiKill(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setKeepAliveAntiKill(value)
    }

    fun setKeepAliveStandbyBypass(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setKeepAliveStandbyBypass(value)
    }

    fun setKeepAliveDozeBypass(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setKeepAliveDozeBypass(value)
    }

    fun setAccessMode(index: Int) = viewModelScope.launch {
        preferenceRepository.setAccessMode(index.toString())
        settingsManager.resetTopActivityCache()
    }

    fun startMiPushServiceAsForegroundService(context: android.content.Context) {
        settingsManager.startMiPushServiceAsForegroundService(context)
    }
    
    fun notifyMockNotification(context: android.content.Context) {
        settingsManager.notifyMockNotification(context)
    }

    fun notifyMockNotification(
        context: android.content.Context,
        kind: io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind,
        packageName: String
    ) {
        settingsManager.notifyMockNotification(context, kind, packageName)
    }
    
    fun clearHistory(context: android.content.Context) {
        settingsManager.clearHistory(context, viewModelScope)
    }
}
