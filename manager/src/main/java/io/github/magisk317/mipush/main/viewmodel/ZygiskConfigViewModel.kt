package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.manager.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ZygiskConfigState(
    val isLoading: Boolean = true,
    val isZygiskEnabled: Boolean = false,
    val hasRootAccess: Boolean = true,
    val spoofPackages: Set<String> = emptySet(),
    val installedApps: List<ManagerApplication> = emptyList()
)

class ZygiskConfigViewModel(
    private val settingsManager: SettingsManager,
    private val applicationGateway: ManagerApplicationGateway,
    private val context: android.content.Context
) : ViewModel() {

    private val _state = MutableStateFlow(ZygiskConfigState())
    val state: StateFlow<ZygiskConfigState> = _state

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            
            val isZygiskEnabled = settingsManager.isZygiskModuleEnabled()
            
            val appsList = withContext(Dispatchers.IO) {
                applicationGateway.loadApplications(context).items
            }

            val spoofList = withContext(Dispatchers.IO) {
                settingsManager.getZygiskSpoofPackages()
            }

            _state.value = _state.value.copy(
                isLoading = false,
                isZygiskEnabled = isZygiskEnabled,
                spoofPackages = spoofList.toSet(),
                installedApps = appsList
            )
        }
    }

    fun togglePackage(packageName: String, enabled: Boolean) {
        val currentPackages = _state.value.spoofPackages.toMutableSet()
        if (enabled) {
            currentPackages.add(packageName)
        } else {
            currentPackages.remove(packageName)
        }
        _state.value = _state.value.copy(spoofPackages = currentPackages)
    }

    fun saveConfig(onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                settingsManager.saveZygiskSpoofPackages(_state.value.spoofPackages.toList())
            }
            if (success) {
                onSuccess()
            } else {
                onError()
            }
        }
    }
}
