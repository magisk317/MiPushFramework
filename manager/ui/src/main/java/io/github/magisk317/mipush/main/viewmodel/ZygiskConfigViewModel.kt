package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.fakedevice.ZygiskConfig
import io.github.magisk317.mipush.common.fakedevice.ZygiskPackagePolicy
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.common.manager.ZygiskConfigReadResult
import io.github.magisk317.mipush.common.manager.ZygiskModuleReadResult
import io.github.magisk317.mipush.common.manager.ZygiskPackageScanResult
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.manager.application.ApplicationListRequest
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.runtime.Immutable

@Immutable
data class ZygiskConfigState(
    val isLoading: Boolean = true,
    val isZygiskEnabled: Boolean = false,
    val hasRootAccess: Boolean = true,
    val spoofPackages: Set<String> = emptySet(),
    val installedApps: List<ManagerApplication> = emptyList(),
    val profile: String = ZygiskConfig.DEFAULT_PROFILE,
    val observe: Boolean = false,
    val scanCandidates: List<String> = emptyList(),
    val configReadAvailable: Boolean = false,
    val configReadError: String? = null,
    val zygiskStatusAvailable: Boolean = false,
    val zygiskStatusError: String? = null,
    val scanError: String? = null,
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

            val moduleResult = withContext(Dispatchers.IO) {
                if (!hasRoot) ZygiskModuleReadResult.Unavailable("zygisk_root_missing")
                else settingsManager.isZygiskModuleEnabled()
            }
            val isZygiskEnabled = (moduleResult as? ZygiskModuleReadResult.Available)?.enabled ?: false

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

            val configResult = withContext(Dispatchers.IO) {
                if (!hasRoot) ZygiskConfigReadResult.Unavailable("zygisk_root_missing")
                else settingsManager.getZygiskConfig()
            }
            val availableConfig = (configResult as? ZygiskConfigReadResult.Available)?.config
            val enabledPackages = availableConfig?.enabledPackages().orEmpty()
            // Keep the source order within each group so refreshing the page does not reshuffle apps.
            val sortedApps = appsList.sortedByDescending { it.packageName in enabledPackages }

            val nextState = _state.value.copy(
                isLoading = false,
                isZygiskEnabled = isZygiskEnabled,
                hasRootAccess = hasRoot,
                installedApps = sortedApps,
                configReadAvailable = configResult is ZygiskConfigReadResult.Available,
                configReadError = (configResult as? ZygiskConfigReadResult.Unavailable)?.reason,
                zygiskStatusAvailable = moduleResult is ZygiskModuleReadResult.Available,
                zygiskStatusError = (moduleResult as? ZygiskModuleReadResult.Unavailable)?.reason,
                scanError = null,
            )
            _state.value = availableConfig?.let { config ->
                nextState.copy(
                    spoofPackages = enabledPackages - blockedPackages,
                    profile = config.profile,
                    observe = config.observe,
                )
            } ?: nextState
        }
    }

    fun togglePackage(packageName: String, enabled: Boolean) {
        if (!_state.value.configReadAvailable) return
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
            if (!_state.value.configReadAvailable) {
                onError()
                return@launch
            }
            val (hasRoot, success) = withContext(Dispatchers.IO) {
                val granted = permissionGateway.requestRootAccess()
                val current = (settingsManager.getZygiskConfig() as? ZygiskConfigReadResult.Available)?.config
                if (!granted || current == null) return@withContext granted to false
                val installedPackages = _state.value.installedApps.map { it.packageName }.toSet()
                val preserved = current.entries.filterNot { it.packageName in installedPackages }
                val packageEntries = _state.value.spoofPackages.map { io.github.magisk317.mipush.common.fakedevice.ZygiskConfigEntry(it) }
                val saved = granted && settingsManager.saveZygiskConfig(
                    current.copy(entries = preserved + packageEntries).copy(
                        profile = _state.value.profile,
                        observe = _state.value.observe,
                    ),
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

    fun setProfile(profile: String) {
        if (_state.value.configReadAvailable && profile in ZygiskConfig.SUPPORTED_PROFILES) {
            _state.value = _state.value.copy(profile = profile)
        }
    }

    fun setObserve(enabled: Boolean) {
        if (_state.value.configReadAvailable) _state.value = _state.value.copy(observe = enabled)
    }

    fun scan() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = settingsManager.scanZygiskPackages()) {
                is ZygiskPackageScanResult.Available -> {
                    _state.value = _state.value.copy(
                        scanCandidates = result.output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList(),
                        scanError = null,
                    )
                }
                is ZygiskPackageScanResult.Unavailable -> {
                    _state.value = _state.value.copy(scanError = result.reason)
                }
            }
        }
    }

}
