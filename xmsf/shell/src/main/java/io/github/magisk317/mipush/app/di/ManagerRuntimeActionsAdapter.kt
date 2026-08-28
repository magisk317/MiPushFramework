package io.github.magisk317.mipush.app.di

import android.content.Context
import android.net.Uri
import android.os.Process
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.app.MiPushFrameworkApp
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.core.zygisk.ZygiskConfig
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.manager.application.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.manager.application.ManagerApplicationGateway
import io.github.magisk317.mipush.utils.DiagnosticExportModes
import io.github.magisk317.mipush.manager.application.ManagerForceRegisterResult
import io.github.magisk317.mipush.manager.application.ManagerApplications
import io.github.magisk317.mipush.manager.application.ManagerConfigEditorSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigGateway
import io.github.magisk317.mipush.manager.application.ManagerConfigListSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigSyncGateway
import io.github.magisk317.mipush.manager.application.ManagerDualAppInstallationResult
import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.application.EventDebugJson
import io.github.magisk317.mipush.manager.application.ManagerEventGateway
import io.github.magisk317.mipush.manager.application.ManagerEventResult
import io.github.magisk317.mipush.manager.application.ManagerEventType
import io.github.magisk317.mipush.manager.application.ManagerLogClearResult
import io.github.magisk317.mipush.manager.application.ManagerLogExportResult
import io.github.magisk317.mipush.manager.application.ManagerLogGateway
import io.github.magisk317.mipush.manager.application.ManagerNotificationChannelCommandGateway
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.application.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRootAccessState
import io.github.magisk317.mipush.manager.application.ManagerRootSubjectStatus
import io.github.magisk317.mipush.manager.application.ManagerRootTarget
import io.github.magisk317.mipush.manager.application.ManagerRuntimeActions
import io.github.magisk317.mipush.manager.application.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairResult
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.manager.application.MockReplayOutcome
import io.github.magisk317.mipush.config.ConfigNavigationHelper
import io.github.magisk317.mipush.configuration.ConfigEditorSnapshot
import io.github.magisk317.mipush.configuration.ConfigSyncRepository
import io.github.magisk317.mipush.configuration.toSummary
import io.github.magisk317.mipush.common.utils.ElapsedTimer
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.compat.RegistrationStateCompat
import io.github.magisk317.mipush.compat.RegistrationStateStore
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.PermissionUtils
import io.github.magisk317.mipush.platform.support.ShellUtils
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.data.EventRepository
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.db.EventRetentionManager
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.github.magisk317.mipush.runtime.store.kmp.EventRowType
import io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.adapter.container
import io.github.magisk317.mipush.runtime.store.event.type.NotificationType
import io.github.magisk317.mipush.runtime.store.event.type.TypeFactory
import io.github.magisk317.mipush.manager.runtime.read.AndroidManagerApplicationReadSource
import io.github.magisk317.mipush.manager.runtime.read.InstalledApplicationSnapshot
import io.github.magisk317.mipush.manager.runtime.read.ManagerApplicationReadPolicy
import io.github.magisk317.mipush.manager.runtime.read.RegistrationEventSnapshot
import io.github.magisk317.mipush.manager.runtime.read.toManagerApplication
import io.github.magisk317.mipush.manager.runtime.read.toStoredApplicationSnapshot
import io.github.magisk317.mipush.service.runtime.RuntimeSettingsAdapter
import io.github.magisk317.mipush.utils.RegSecUtils
import io.github.magisk317.mipush.utils.LogBundleExporter
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.mipush.utils.RegistrationHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Date
import java.util.Locale

class XmsfManagerRuntimeActions(
    private val runtimeSettingsAdapter: RuntimeSettingsAdapter,
) : ManagerRuntimeActions {
    override suspend fun clearHistory() {
        // 复用注入的保留天数 provider,与自动清理口径一致(而非硬编码 7 天)。
        EventRetentionManager.pruneNow()
    }

    override suspend fun startMiPushServiceAsForegroundService(context: Context) {
        runtimeSettingsAdapter.startMiPushServiceAsForegroundService(context)
    }

    override suspend fun resetTopActivityCache() {
        runtimeSettingsAdapter.resetTopActivityCache()
    }

    override suspend fun sendXmppReconnectRequest(context: Context): Boolean =
        runtimeSettingsAdapter.sendXmppReconnectRequest()

    override suspend fun setXmppServer(context: Context, newHost: String) {
        runtimeSettingsAdapter.setXmppServer(context, newHost)
    }

    override suspend fun getRuntimeEnvironmentSnapshot(context: Context): ManagerRuntimeEnvironmentSnapshot {
        return runtimeSettingsAdapter.getRuntimeEnvironmentSnapshot(context)
    }

    override suspend fun getConnectionSnapshot() = runtimeSettingsAdapter.getConnectionSnapshot()

    override fun observeNotificationEvent(packageName: String, action: String, source: String) {
        PushRuntime.observeNotificationEvent(packageName, action, source)
    }

    override suspend fun setRuntimeLogRetentionDays(days: Int) {
        LogUtils.setRetentionDays(days)
    }

    override suspend fun applyEventRetentionDays(days: Int) {
        // 保留天数已由上层写入 DataStore;App 层的 collect 会更新 EventRetentionManager 的
        // provider 缓存。这里立即跑一次清理,让改小后的保留窗口即时生效。
        MiPushFrameworkApp.applicationScope.launch {
            EventRetentionManager.pruneNow()
        }
    }
}

internal fun ManagerApplication.toRegisteredApplicationRow(): RuntimeRegisteredApplicationRow =
    RuntimeRegisteredApplicationRow(
        id = id,
        packageName = packageName,
        type = type,
        notificationOnRegister = notificationOnRegister,
        registeredType = when (registeredType) {
            ManagerApplication.RegisteredType.REGISTERED -> RegisteredAppRegisteredType.Registered
            ManagerApplication.RegisteredType.UNREGISTERED -> RegisteredAppRegisteredType.Unregistered
            else -> RegisteredAppRegisteredType.NotRegistered
        },
        appName = appName,
        blocked = blocked,
        islandEnabled = islandEnabled,
        islandFocusNotification = islandFocusNotification,
        userId = userId,
    )
