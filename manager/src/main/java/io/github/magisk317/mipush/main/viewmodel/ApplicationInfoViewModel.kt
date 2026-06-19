package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigSyncGateway
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
    private val configSyncGateway: ManagerConfigSyncGateway,
    private val context: Context,
) : ViewModel() {

    private val _applicationInfo = MutableStateFlow<ManagerApplication?>(null)
    val applicationInfo: StateFlow<ManagerApplication?> = _applicationInfo.asStateFlow()

    private val _integrationTypeReason = MutableStateFlow<String?>(null)
    val integrationTypeReason: StateFlow<String?> = _integrationTypeReason.asStateFlow()

    private val _diagnostics = MutableStateFlow<ManagerApplicationDiagnostics?>(null)
    val diagnostics: StateFlow<ManagerApplicationDiagnostics?> = _diagnostics.asStateFlow()

    fun setApplicationInfo(info: ManagerApplication) {
        _applicationInfo.value = info
        loadIntegrationTypeReason(info.packageName)
        loadDiagnostics(info.packageName, info.registeredType)
    }

    private fun loadIntegrationTypeReason(packageName: String) {
        viewModelScope.launch {
            val reason = withContext(Dispatchers.IO) {
                applicationGateway.loadIntegrationTypeReason(context, packageName)
            }
            _integrationTypeReason.value = reason
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

    fun openConfigSync(packageName: String) {
        viewModelScope.launch {
            configSyncGateway.openForPackage(packageName)
        }
    }
}
