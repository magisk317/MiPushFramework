package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.fakedevice.ZygiskPackagePolicy
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.manager.application.RemoteApplicationDetailSource
import io.github.magisk317.mipush.manager.notification.RemoteNotificationChannelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ApplicationInfoControlState(
    val zygiskConfigurable: Boolean,
    val recentActivityEnabled: Boolean,
    val islandEnabled: Boolean,
    val islandFocusEnabled: Boolean,
)

internal object ApplicationInfoStatePolicy {
    fun controls(info: ManagerApplication): ApplicationInfoControlState =
        ApplicationInfoControlState(
            zygiskConfigurable =
                !info.blocked && ZygiskPackagePolicy.isManagedPackage(info.packageName),
            recentActivityEnabled = !info.blocked,
            islandEnabled = !info.blocked,
            islandFocusEnabled = !info.blocked && info.islandEnabled,
        )

    fun withBlocked(info: ManagerApplication, blocked: Boolean): ManagerApplication =
        if (blocked) {
            info.copy(
                blocked = true,
                islandEnabled = false,
                islandFocusNotification = false,
            )
        } else {
            info.copy(blocked = false)
        }

    fun withIslandEnabled(info: ManagerApplication, enabled: Boolean): ManagerApplication? {
        if (!controls(info).islandEnabled) return null
        return info.copy(
            islandEnabled = enabled,
            islandFocusNotification = info.islandFocusNotification && enabled,
        )
    }

    fun withIslandFocusEnabled(info: ManagerApplication, enabled: Boolean): ManagerApplication? {
        if (!controls(info).islandFocusEnabled) return null
        return info.copy(islandFocusNotification = enabled)
    }
}

class ApplicationInfoViewModel constructor(
    private val applicationGateway: ManagerApplicationGateway,
    private val applicationSource: RemoteApplicationDetailSource,
    private val notificationChannelSource: RemoteNotificationChannelSource,
    private val settingsManager: SettingsManager,
    private val permissionGateway: ManagerPermissionGateway,
    private val context: Context,
) : ViewModel() {

    private val _applicationInfo = MutableStateFlow<ManagerApplication?>(null)
    val applicationInfo: StateFlow<ManagerApplication?> = _applicationInfo.asStateFlow()

    private val _isZygiskEnabledForApp = MutableStateFlow(false)
    val isZygiskEnabledForApp: StateFlow<Boolean> = _isZygiskEnabledForApp.asStateFlow()

    private val _isZygiskConfigurableForApp = MutableStateFlow(false)
    val isZygiskConfigurableForApp: StateFlow<Boolean> = _isZygiskConfigurableForApp.asStateFlow()

    private val _diagnostics = MutableStateFlow<ManagerApplicationDiagnostics?>(null)
    val diagnostics: StateFlow<ManagerApplicationDiagnostics?> = _diagnostics.asStateFlow()


    fun setApplicationInfo(
        info: ManagerApplication,
        ignoreNotRegistered: Boolean = false,
    ) {
        _applicationInfo.value = info
        refreshZygiskConfigurable(info)
        loadZygiskState(info.packageName, blocked = info.blocked)
        loadDiagnostics(info.packageName, info.registeredType)
    }

    private fun refreshZygiskConfigurable(info: ManagerApplication) {
        _isZygiskConfigurableForApp.value =
            ApplicationInfoStatePolicy.controls(info).zygiskConfigurable
    }

    private fun loadZygiskState(packageName: String, blocked: Boolean) {
        viewModelScope.launch {
            if (blocked || !ZygiskPackagePolicy.isManagedPackage(packageName)) {
                _isZygiskEnabledForApp.value = false
                return@launch
            }
            val enabled = withContext(Dispatchers.IO) {
                val hasRoot = permissionGateway.hasCachedRootAccess() ||
                    permissionGateway.refreshRootAccessIfGranted()
                hasRoot && settingsManager.isZygiskSpoofEnabled(packageName)
            }
            _isZygiskEnabledForApp.value = enabled
        }
    }

    fun updateZygiskEnabledForApp(enabled: Boolean) {
        val current = _applicationInfo.value ?: return
        val packageName = current.packageName
        if (current.blocked || !ZygiskPackagePolicy.isManagedPackage(packageName)) return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                if (!permissionGateway.requestRootAccess()) return@withContext false
                settingsManager.setZygiskSpoofEnabled(packageName, enabled)
            }
            if (success) {
                _isZygiskEnabledForApp.value = enabled
            }
        }
    }

    private fun loadDiagnostics(packageName: String, registeredType: Int) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                when (val remote = applicationSource.loadDiagnostics(packageName, registeredType)) {
                    is io.github.magisk317.mipush.manager.application.ApplicationReadResult.Available -> remote.value
                    is io.github.magisk317.mipush.manager.application.ApplicationReadResult.Unavailable -> null
                }
            }
            _diagnostics.value = result
        }
    }

    fun updateBlocked(blocked: Boolean) {
        val current = _applicationInfo.value ?: return
        val updated = ApplicationInfoStatePolicy.withBlocked(current, blocked)
        _applicationInfo.value = updated
        applicationGateway.updateApplication(updated)
        refreshZygiskConfigurable(updated)
        if (blocked) {
            _isZygiskEnabledForApp.value = false
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    val hasRoot = permissionGateway.hasCachedRootAccess() ||
                        permissionGateway.refreshRootAccessIfGranted()
                    if (hasRoot) {
                        settingsManager.setZygiskSpoofEnabled(current.packageName, false)
                    }
                }
            }
        } else {
            loadZygiskState(updated.packageName, blocked = false)
        }
    }

    fun updateIslandEnabled(enabled: Boolean) {
        val current = _applicationInfo.value ?: return
        val updated = ApplicationInfoStatePolicy.withIslandEnabled(current, enabled) ?: return
        _applicationInfo.value = updated
        applicationGateway.updateApplication(updated)
    }

    fun updateIslandFocusEnabled(enabled: Boolean) {
        val current = _applicationInfo.value ?: return
        val updated = ApplicationInfoStatePolicy.withIslandFocusEnabled(current, enabled) ?: return
        _applicationInfo.value = updated
        applicationGateway.updateApplication(updated)
    }
    suspend fun launchTargetAppAndForceRegister(
        packageName: String,
        registeredType: Int,
    ): String {
        return withContext(Dispatchers.IO) {
            applicationGateway.launchTargetAppAndForceRegister(
                context = context,
                packageName = packageName,
                registeredType = registeredType,
            )
        }
    }


    fun scheduleNotificationComparison(packageName: String) {
        // Remote-only path: no dual-source comparison.
    }


}
