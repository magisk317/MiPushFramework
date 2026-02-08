package com.magisk317.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magisk317.data.DataStoreManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdvancedSettingsViewModel : ViewModel() {
    val notificationOnRegister: StateFlow<Boolean> = DataStoreManager.notificationOnRegister
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showConfigurationList: StateFlow<Boolean> = DataStoreManager.showConfigurationList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val debugMode: StateFlow<Boolean> = DataStoreManager.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showAllEvents: StateFlow<Boolean> = DataStoreManager.showAllEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isStartForeground: StateFlow<Boolean> = DataStoreManager.isStartForeground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val accessMode: StateFlow<String> = DataStoreManager.accessMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0")

    val iceboxSupported: StateFlow<Boolean> = DataStoreManager.iceboxSupported
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setNotificationOnRegister(value: Boolean) = viewModelScope.launch {
        DataStoreManager.setNotificationOnRegister(value)
    }

    fun setShowConfigurationList(value: Boolean) = viewModelScope.launch {
        DataStoreManager.setShowConfigurationList(value)
    }

    fun setDebugMode(value: Boolean) = viewModelScope.launch {
        DataStoreManager.setDebugMode(value)
    }

    fun setShowAllEvents(value: Boolean) = viewModelScope.launch {
        DataStoreManager.setShowAllEvents(value)
    }

    fun setStartForeground(value: Boolean) = viewModelScope.launch {
        DataStoreManager.setIsStartForeground(value)
    }

    fun setAccessMode(index: Int) = viewModelScope.launch {
        DataStoreManager.setAccessMode(index.toString())
    }

    fun setIceboxSupported(value: Boolean) = viewModelScope.launch {
        DataStoreManager.setIceboxSupported(value)
    }
}
