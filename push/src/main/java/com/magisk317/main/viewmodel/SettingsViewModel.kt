package com.magisk317.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.magisk317.data.DataStoreManager

class SettingsViewModel : ViewModel() {
    val hazeBlurRadius: StateFlow<Int> = DataStoreManager.hazeBlurRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 25)

    val hazeTintAlpha: StateFlow<Float> = DataStoreManager.hazeTintAlpha
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.2f)

    val xmppServer: StateFlow<String?> = DataStoreManager.xmppServer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val configDirectory: StateFlow<String?> = DataStoreManager.configDirectory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateHazeBlurRadius(radius: Int) {
        viewModelScope.launch {
            DataStoreManager.setHazeBlurRadius(radius)
        }
    }

    fun updateHazeTintAlpha(alpha: Float) {
        viewModelScope.launch {
            DataStoreManager.setHazeTintAlpha(alpha)
        }
    }

    fun updateXmppServer(host: String) {
        viewModelScope.launch {
            DataStoreManager.setXmppServer(host)
        }
    }

    fun updateConfigDirectory(uri: String) {
        viewModelScope.launch {
            DataStoreManager.setConfigDirectory(uri)
        }
    }
}
