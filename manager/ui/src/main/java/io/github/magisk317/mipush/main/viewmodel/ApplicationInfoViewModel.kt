package io.github.magisk317.mipush.main.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.manager.application.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.manager.application.ManagerApplicationGateway
import io.github.magisk317.mipush.manager.application.RemoteApplicationDetailSource
import io.github.magisk317.mipush.manager.notification.NotificationChannelReadResult
import io.github.magisk317.mipush.manager.notification.NotificationChannelReadStatus
import io.github.magisk317.mipush.manager.notification.NotificationChannelSnapshot
import io.github.magisk317.mipush.manager.notification.RemoteNotificationChannelSource
import io.github.magisk317.mipush.manager.notification.RemoteNotificationChannelCommand
import io.github.magisk317.mipush.manager.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ApplicationInfoControlState(
    val recentActivityEnabled: Boolean,
    val islandEnabled: Boolean,
    val islandFocusEnabled: Boolean,
    val clickFallbackEnabled: Boolean,
    val suppressWhenDisabled: Boolean,
    val countLimitExemptEnabled: Boolean,
)

internal object ApplicationInfoStatePolicy {
    fun controls(info: ManagerApplication): ApplicationInfoControlState =
        ApplicationInfoControlState(
            recentActivityEnabled = !info.blocked,
            islandEnabled = !info.blocked,
            islandFocusEnabled = !info.blocked && info.islandEnabled,
            clickFallbackEnabled = !info.blocked,
            suppressWhenDisabled = !info.blocked,
            countLimitExemptEnabled = !info.blocked,
        )

    fun withBlocked(info: ManagerApplication, blocked: Boolean): ManagerApplication =
        info.copy(blocked = blocked)

    fun withUnregistered(info: ManagerApplication): ManagerApplication =
        withBlocked(
            info.copy(registeredType = ManagerApplication.RegisteredType.UNREGISTERED),
            blocked = true,
        )

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

    fun withClickFallbackEnabled(info: ManagerApplication, enabled: Boolean): ManagerApplication? {
        if (!controls(info).clickFallbackEnabled) return null
        return info.copy(clickFallbackEnabled = enabled)
    }

    fun withSuppressWhenDisabled(info: ManagerApplication, enabled: Boolean): ManagerApplication? {
        if (!controls(info).suppressWhenDisabled) return null
        return info.copy(suppressWhenDisabled = enabled)
    }

    fun withCountLimitExempt(info: ManagerApplication, enabled: Boolean): ManagerApplication? {
        if (!controls(info).countLimitExemptEnabled) return null
        return info.copy(countLimitExempt = enabled)
    }
}

internal data class NotificationChannelUiState(
    val packageName: String? = null,
    val snapshot: NotificationChannelSnapshot? = null,
    val isLoading: Boolean = false,
    val unavailableStatus: NotificationChannelReadStatus? = null,
)

internal object NotificationChannelUiStatePolicy {
    fun start(
        current: NotificationChannelUiState,
        packageName: String,
    ): NotificationChannelUiState = if (current.packageName == packageName) {
        current.copy(isLoading = true, unavailableStatus = null)
    } else {
        NotificationChannelUiState(packageName = packageName, isLoading = true)
    }

    fun complete(
        current: NotificationChannelUiState,
        packageName: String,
        result: NotificationChannelReadResult<NotificationChannelSnapshot>,
    ): NotificationChannelUiState {
        if (current.packageName != packageName) return current
        return when (result) {
            is NotificationChannelReadResult.Available -> {
                if (result.value.packageName == packageName) {
                    current.copy(
                        snapshot = result.value,
                        isLoading = false,
                        unavailableStatus = null,
                    )
                } else {
                    current.copy(
                        isLoading = false,
                        unavailableStatus = NotificationChannelReadStatus.FAILED,
                    )
                }
            }
            is NotificationChannelReadResult.Unavailable -> current.copy(
                isLoading = false,
                unavailableStatus = result.status,
            )
        }
    }
}

class ApplicationInfoViewModel constructor(
    private val applicationGateway: ManagerApplicationGateway,
    private val applicationSource: RemoteApplicationDetailSource,
    private val notificationChannelSource: RemoteNotificationChannelSource,
    private val notificationChannelCommand: RemoteNotificationChannelCommand,
    private val context: Application,
) : ViewModel() {

    private val _applicationInfo = MutableStateFlow<ManagerApplication?>(null)
    val applicationInfo: StateFlow<ManagerApplication?> = _applicationInfo.asStateFlow()

    private val _diagnostics = MutableStateFlow<ManagerApplicationDiagnostics?>(null)
    val diagnostics: StateFlow<ManagerApplicationDiagnostics?> = _diagnostics.asStateFlow()

    private val _notificationChannels = MutableStateFlow(NotificationChannelUiState())
    internal val notificationChannels: StateFlow<NotificationChannelUiState> =
        _notificationChannels.asStateFlow()
    private var notificationChannelsJob: Job? = null
    private val applicationWriteMutex = Mutex()

    private val _registrationBusy = MutableStateFlow(false)
    val registrationBusy: StateFlow<Boolean> = _registrationBusy.asStateFlow()

    private fun updateApplication(transform: (ManagerApplication) -> ManagerApplication?) {
        if (_registrationBusy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            applicationWriteMutex.withLock {
                // A queued setting must never overwrite a registration operation's new state.
                if (_registrationBusy.value) return@withLock
                val previous = _applicationInfo.value ?: return@withLock
                val updated = transform(previous) ?: return@withLock
                _applicationInfo.value = updated
                try {
                    applicationGateway.updateApplication(updated)
                } catch (error: CancellationException) {
                    if (_applicationInfo.value == updated) _applicationInfo.value = previous
                    throw error
                } catch (_: Exception) {
                    if (_applicationInfo.value == updated) _applicationInfo.value = previous
                }
            }
        }
    }

    fun setApplicationInfo(
        info: ManagerApplication,
        ignoreNotRegistered: Boolean = false,
    ) {
        _applicationInfo.value = info
        loadDiagnostics(info.packageName, info.registeredType)
        loadNotificationChannels(info.packageName)
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
        if (_registrationBusy.value) return
        val packageName = _applicationInfo.value?.packageName ?: return
        viewModelScope.launch {
            setRegistrationPolicy(
                packageName,
                if (blocked) ManagerApplication.Type.DENY else ManagerApplication.Type.ALLOW,
            )
        }
    }

    fun updateIslandEnabled(enabled: Boolean) = updateApplication {
        ApplicationInfoStatePolicy.withIslandEnabled(it, enabled)
    }

    fun updateIslandFocusEnabled(enabled: Boolean) = updateApplication {
        ApplicationInfoStatePolicy.withIslandFocusEnabled(it, enabled)
    }

    fun updateClickFallbackEnabled(enabled: Boolean) = updateApplication {
        ApplicationInfoStatePolicy.withClickFallbackEnabled(it, enabled)
    }

    fun updateSuppressWhenDisabled(enabled: Boolean) = updateApplication {
        ApplicationInfoStatePolicy.withSuppressWhenDisabled(it, enabled)
    }

    fun updateCountLimitExempt(enabled: Boolean) = updateApplication {
        ApplicationInfoStatePolicy.withCountLimitExempt(it, enabled)
    }

    suspend fun launchTargetAppAndForceRegister(packageName: String): String = registrationOperation(packageName) { current ->
        applicationGateway.launchTargetAppAndForceRegister(context, packageName, current.registeredType).message
    }

    suspend fun unregisterApplication(packageName: String): String = registrationOperation(packageName) {
        applicationGateway.unregisterApplication(context, packageName).message
    }

    suspend fun setRegistrationPolicy(packageName: String, type: Int): String = registrationOperation(packageName) {
        applicationGateway.setRegistrationPolicy(context, packageName, type).message
    }

    suspend fun approvePendingRegistration(packageName: String): String = registrationOperation(packageName) {
        applicationGateway.approvePendingRegistration(context, packageName).message
    }

    private suspend fun registrationOperation(
        packageName: String,
        action: suspend (ManagerApplication) -> String,
    ): String {
        if (!_registrationBusy.compareAndSet(expect = false, update = true)) {
            return context.getString(R.string.app_detail_registration_busy)
        }
        try {
            return withContext(Dispatchers.IO) {
                applicationWriteMutex.withLock {
                    val current = _applicationInfo.value?.takeIf { it.packageName == packageName }
                        ?: return@withLock context.getString(R.string.app_detail_registration_failed)
                    val message = action(current)
                    // Read the runtime's committed state on both success and a reported failure;
                    // an accepted request is not evidence that server registration completed.
                    val refreshed = applicationGateway.getApplication(context, packageName, false)
                    if (_applicationInfo.value?.packageName == packageName && refreshed?.packageName == packageName) {
                        _applicationInfo.value = refreshed
                        loadDiagnostics(packageName, refreshed.registeredType)
                    }
                    message
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return context.getString(R.string.app_detail_registration_failed)
        } finally {
            _registrationBusy.value = false
        }
    }

    fun deleteNotificationChannel(channelId: String, onResult: (Boolean) -> Unit = {}) {
        val packageName = _applicationInfo.value?.packageName ?: run {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                notificationChannelCommand.delete(packageName, channelId)
            }
            if (deleted && _applicationInfo.value?.packageName == packageName) {
                refreshNotificationChannels()
            }
            onResult(deleted)
        }
    }

    fun refreshNotificationChannels() {
        val packageName = _applicationInfo.value?.packageName ?: return
        loadNotificationChannels(packageName)
    }

    private fun loadNotificationChannels(packageName: String) {
        notificationChannelsJob?.cancel()
        _notificationChannels.update { current ->
            NotificationChannelUiStatePolicy.start(current, packageName)
        }
        notificationChannelsJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                notificationChannelSource.load(packageName)
            }
            _notificationChannels.update { current ->
                NotificationChannelUiStatePolicy.complete(current, packageName, result)
            }
        }
    }

}
