package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.fakedevice.ZygiskPackagePolicy
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.feature.main.AppRegistrationDiagnosticsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApplicationInfoViewModel constructor(
    private val applicationGateway: ManagerApplicationGateway,
    private val settingsManager: SettingsManager,
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

    fun setApplicationInfo(info: ManagerApplication) {
        _applicationInfo.value = info
        _isZygiskConfigurableForApp.value = ZygiskPackagePolicy.isManagedPackage(info.packageName)
        loadZygiskState(info.packageName)
        loadDiagnostics(info.packageName, info.registeredType)
    }

    private fun loadZygiskState(packageName: String) {
        viewModelScope.launch {
            if (!ZygiskPackagePolicy.isManagedPackage(packageName)) {
                _isZygiskEnabledForApp.value = false
                return@launch
            }
            val enabled = withContext(Dispatchers.IO) {
                settingsManager.isZygiskSpoofEnabled(packageName)
            }
            _isZygiskEnabledForApp.value = enabled
        }
    }

    fun updateZygiskEnabledForApp(enabled: Boolean) {
        val packageName = _applicationInfo.value?.packageName ?: return
        if (!ZygiskPackagePolicy.isManagedPackage(packageName)) return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
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
                AppRegistrationDiagnosticsHelper.load(
                    packageName = packageName,
                    registeredType = registeredType,
                    applicationGateway = applicationGateway,
                )
            }
            _diagnostics.value = result
        }
    }

    fun updateBlocked(blocked: Boolean) {
        val current = _applicationInfo.value ?: return
        val updated = current.copy(blocked = blocked)
        _applicationInfo.value = updated
        applicationGateway.updateApplication(updated)
    }

    fun updateIslandEnabled(enabled: Boolean) {
        val current = _applicationInfo.value ?: return
        val updated = current.copy(islandEnabled = enabled)
        _applicationInfo.value = updated
        applicationGateway.updateApplication(updated)
    }

    fun updateIslandFocusEnabled(enabled: Boolean) {
        val current = _applicationInfo.value ?: return
        val updated = current.copy(islandFocusNotification = enabled)
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
}
