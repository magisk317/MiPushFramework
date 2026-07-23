package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.fakedevice.ZygiskPackagePolicy
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.manager.application.ApplicationDetailComparison
import io.github.magisk317.mipush.manager.application.ApplicationDiagnosticsComparison
import io.github.magisk317.mipush.manager.application.ComparingApplicationDetailSource
import io.github.magisk317.mipush.manager.notification.ComparingNotificationChannelSource
import io.github.magisk317.mipush.manager.notification.NotificationChannelComparison
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
    private val applicationSource: ComparingApplicationDetailSource,
    private val notificationChannelSource: ComparingNotificationChannelSource,
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

    private val _detailComparison = MutableStateFlow<ApplicationDetailComparison>(
        ApplicationDetailComparison.NotStarted,
    )
    val detailComparison: StateFlow<ApplicationDetailComparison> = _detailComparison.asStateFlow()

    private val _diagnosticsComparison = MutableStateFlow<ApplicationDiagnosticsComparison>(
        ApplicationDiagnosticsComparison.NotStarted,
    )
    val diagnosticsComparison: StateFlow<ApplicationDiagnosticsComparison> = _diagnosticsComparison.asStateFlow()

    private var detailComparisonJob: Job? = null
    private var diagnosticsComparisonJob: Job? = null
    private val _notificationComparison = MutableStateFlow<NotificationChannelComparison?>(null)
    val notificationComparison: StateFlow<NotificationChannelComparison?> = _notificationComparison.asStateFlow()
    private var notificationComparisonJob: Job? = null

    fun setApplicationInfo(
        info: ManagerApplication,
        ignoreNotRegistered: Boolean = false,
    ) {
        _applicationInfo.value = info
        refreshZygiskConfigurable(info)
        loadZygiskState(info.packageName, blocked = info.blocked)
        loadDiagnostics(info.packageName, info.registeredType)
        scheduleDetailComparison(info, ignoreNotRegistered)
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
                settingsManager.isZygiskSpoofEnabled(packageName)
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
                applicationSource.loadPrimaryDiagnostics(packageName, registeredType)
            }
            _diagnostics.value = result
            scheduleDiagnosticsComparison(packageName, registeredType, result)
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
                    settingsManager.setZygiskSpoofEnabled(current.packageName, false)
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
        notificationComparisonJob?.cancel()
        notificationComparisonJob = viewModelScope.launch {
            _notificationComparison.value = withContext(Dispatchers.IO) {
                val primary = notificationChannelSource.loadPrimary(packageName)
                notificationChannelSource.compareRemote(packageName, primary)
            }
        }
    }
    override fun onCleared() {
        detailComparisonJob?.cancel()
        diagnosticsComparisonJob?.cancel()
        notificationComparisonJob?.cancel()
        detailComparisonJob = null
        diagnosticsComparisonJob = null
        notificationComparisonJob = null
        super.onCleared()
    }

    private fun scheduleDetailComparison(
        primary: ManagerApplication,
        ignoreNotRegistered: Boolean,
    ) {
        detailComparisonJob?.cancel()
        _detailComparison.value = ApplicationDetailComparison.Comparing
        detailComparisonJob = viewModelScope.launch {
            _detailComparison.value = withContext(Dispatchers.IO) {
                applicationSource.compareRemote(
                    packageName = primary.packageName,
                    ignoreNotRegistered = ignoreNotRegistered,
                    primary = primary,
                )
            }
        }
    }

    private fun scheduleDiagnosticsComparison(
        packageName: String,
        registeredType: Int,
        primary: ManagerApplicationDiagnostics,
    ) {
        diagnosticsComparisonJob?.cancel()
        _diagnosticsComparison.value = ApplicationDiagnosticsComparison.Comparing
        diagnosticsComparisonJob = viewModelScope.launch {
            _diagnosticsComparison.value = withContext(Dispatchers.IO) {
                applicationSource.compareRemoteDiagnostics(
                    packageName = packageName,
                    registeredType = registeredType,
                    primary = primary,
                )
            }
        }
    }
}
