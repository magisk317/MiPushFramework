package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.fakedevice.ZygiskConfig
import io.github.magisk317.mipush.common.fakedevice.ZygiskPackagePolicy
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.manager.application.ApplicationListRequest
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
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
    private val applicationSource: RemoteApplicationListSource,
    private val permissionGateway: ManagerPermissionGateway,
) : ViewModel() {

    private val _state = MutableStateFlow(ZygiskConfigState())
    val state: StateFlow<ZygiskConfigState> = _state

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val hasRoot = withContext(Dispatchers.IO) {
                permissionGateway.hasCachedRootAccess() ||
                    permissionGateway.refreshRootAccessIfGranted()
            }

            val isZygiskEnabled = withContext(Dispatchers.IO) {
                if (!hasRoot) false else settingsManager.isZygiskModuleEnabled()
            }

            val applications = withContext(Dispatchers.IO) {
                when (val result = applicationSource.load(ApplicationListRequest())) {
                    is io.github.magisk317.mipush.manager.application.ApplicationReadResult.Available -> result.value
                    is io.github.magisk317.mipush.manager.application.ApplicationReadResult.Unavailable -> null
                }
            }
            val allApps = applications?.applications?.items.orEmpty()
            val blockedPackages = allApps.asSequence()
                .filter { it.blocked }
                .map { it.packageName }
                .toSet()
            val appsList = allApps.filter {
                !it.blocked && ZygiskPackagePolicy.isManagedPackage(it.packageName)
            }

            val zygiskConfig = withContext(Dispatchers.IO) {
                if (!hasRoot) ZygiskConfig() else settingsManager.getZygiskConfig()
            }

            _state.value = _state.value.copy(
                isLoading = false,
                isZygiskEnabled = isZygiskEnabled,
                hasRootAccess = hasRoot,
                spoofPackages = zygiskConfig.enabledPackages() - blockedPackages,
                installedApps = appsList,
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
            val (hasRoot, success) = withContext(Dispatchers.IO) {
                val granted = permissionGateway.requestRootAccess()
                val saved = granted && settingsManager.saveZygiskConfig(
                    ZygiskConfig.fromPackages(_state.value.spoofPackages),
                )
                granted to saved
            }
            _state.value = _state.value.copy(hasRootAccess = hasRoot)
            if (success) {
                onSuccess()
            } else {
                onError()
            }
        }
    }

}
