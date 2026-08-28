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

class XmsfManagerConfigGateway(
    private val configCenter: ConfigCenter,
    private val runtimeSettingsAdapter: RuntimeSettingsAdapter,
) : ManagerConfigGateway {
    override suspend fun getXmppServer(): String? = configCenter.getXMPPServerAsync()

    override suspend fun setXmppServer(host: String): Boolean = runCatching {
        runtimeSettingsAdapter.setXmppServer(newHost = host)
    }.isSuccess

    override suspend fun getConfigurationDirectory(): Uri? = configCenter.getConfigurationDirectoryAsync()

    override suspend fun setConfigurationDirectory(uri: Uri): Boolean =
        configCenter.setConfigurationDirectoryAsync(uri)

    override suspend fun loadConfigurations(context: Context) {
        configCenter.loadConfigurationsNow(context)
    }
}

class XmsfManagerConfigSyncGateway(
    private val syncRepository: ConfigSyncRepository,
    private val configNavigationHelper: ConfigNavigationHelper,
) : ManagerConfigSyncGateway {
    override suspend fun loadLocalSnapshot(treeUri: Uri?): ManagerConfigListSnapshot {
        val snapshot = syncRepository.loadLocalSnapshot(treeUri)
        return ManagerConfigListSnapshot(
            items = snapshot.items,
            remoteError = snapshot.remoteError,
        )
    }

    override suspend fun loadRemoteSnapshot(treeUri: Uri?): ManagerConfigListSnapshot {
        val snapshot = syncRepository.loadRemoteSnapshot(treeUri)
        return ManagerConfigListSnapshot(
            items = snapshot.items,
            remoteError = snapshot.remoteError,
        )
    }

    override suspend fun readLocalEditorSnapshot(treeUri: Uri?, path: String): ManagerConfigEditorSnapshot {
        val snapshot = syncRepository.readLocalEditorSnapshot(treeUri, path)
        return snapshot.toManagerConfigEditorSnapshot()
    }

    override suspend fun readRemoteEditorSnapshot(treeUri: Uri?, path: String): ManagerConfigEditorSnapshot {
        val snapshot = syncRepository.readRemoteEditorSnapshot(treeUri, path)
        return snapshot.toManagerConfigEditorSnapshot()
    }

    override suspend fun pullAll(
        treeUri: Uri,
        onProgress: ((current: Int, total: Int, path: String) -> Unit)?,
    ): Int = syncRepository.pullAll(treeUri, onProgress)

    override suspend fun importDocuments(treeUri: Uri, uris: List<Uri>, isIcon: Boolean): Int =
        syncRepository.importDocuments(treeUri, uris, isIcon)

    override suspend fun saveLocal(treeUri: Uri, path: String, content: String) =
        syncRepository.saveLocal(treeUri, path, content).toSummary()

    override suspend fun resetToRemote(treeUri: Uri, path: String) =
        syncRepository.resetToRemote(treeUri, path).toSummary()

    override suspend fun openForPackage(packageName: String) {
        configNavigationHelper.openForPackage(packageName)
    }

    private fun ConfigEditorSnapshot.toManagerConfigEditorSnapshot() =
        ManagerConfigEditorSnapshot(
            path = path,
            local = local,
            remote = remote,
            remoteMeta = remoteMeta,
            localMeta = localMeta?.toSummary(),
            remoteError = remoteError,
        )
}

class XmsfManagerNotificationChannelCommandGateway : ManagerNotificationChannelCommandGateway {
    override fun deleteNotificationChannel(packageName: String, channelId: String): Boolean =
        NotificationManagerEx.deleteNotificationChannel(packageName, channelId)
}
