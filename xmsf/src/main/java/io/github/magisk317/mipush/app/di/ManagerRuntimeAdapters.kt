package io.github.magisk317.mipush.app.di

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.app.MiPushFrameworkApp
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.fakedevice.ZygiskConfig
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerForceRegisterResult
import io.github.magisk317.mipush.common.manager.ManagerApplications
import io.github.magisk317.mipush.common.manager.ManagerConfigEditorSnapshot
import io.github.magisk317.mipush.common.manager.ManagerConfigGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigListSnapshot
import io.github.magisk317.mipush.common.manager.ManagerConfigSyncGateway
import io.github.magisk317.mipush.common.manager.ManagerDualAppInstallationResult
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.manager.EventDebugJson
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.common.manager.ManagerEventResult
import io.github.magisk317.mipush.common.manager.ManagerEventType
import io.github.magisk317.mipush.common.manager.ManagerLogClearResult
import io.github.magisk317.mipush.common.manager.ManagerLogExportResult
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.common.manager.ManagerNotificationChannelCommandGateway
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.common.manager.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.common.manager.ManagerRootAccessState
import io.github.magisk317.mipush.common.manager.ManagerRootSubjectStatus
import io.github.magisk317.mipush.common.manager.ManagerRootTarget
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
import io.github.magisk317.mipush.common.manager.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairResult
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.common.notification.MockReplayOutcome
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
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication
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
import java.io.File
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

class XmsfManagerEventGateway(
    private val context: Context,
    private val eventRepository: EventRepository,
) : ManagerEventGateway {
    override suspend fun getEventsById(lastId: Long?, size: Int, packageName: String, query: String): List<ManagerEvent> =
        eventRepository.getEventsById(lastId, size, packageName, query).map { it.toManagerEvent() }

    override fun startManagePermissions(packageName: String, ignoreNotRegistered: Boolean) {
        eventRepository.startManagePermissions(packageName, ignoreNotRegistered)
    }

    override suspend fun startConfigPreview(packageName: String) {
        eventRepository.startConfigPreview(packageName)
    }

    override fun copyToClipboard(content: String) {
        eventRepository.copyToClipboard(content)
    }

    override suspend fun mockMessage(event: ManagerEvent): MockReplayOutcome {
        val resolved = resolveEventForMock(event) ?: return MockReplayOutcome.Failed
        val container = RegSecUtils.getContainerWithRegSec(resolved.payload, resolved.regSec)
            ?: return MockReplayOutcome.Failed
        return eventRepository.mockMessage(container)
    }

    private suspend fun resolveEventForMock(event: ManagerEvent): ManagerEvent? {
        if (event.id <= 0L || event.packageName.isBlank()) return null
        val stored = EventDb.getByIdAsync(event.id, event.userId) ?: return null
        if (stored.pkg != event.packageName) return null
        return stored.toManagerEvent()
    }

    override suspend fun getJson(event: ManagerEvent): String? {
        val owned = if (event.id > 0L) resolveEventForMock(event) ?: return null else event
        return eventRepository.getJson(owned.toEvent())?.toString()
            ?: runCatching { EventDebugJson.format(owned) }.getOrNull()
    }

    override suspend fun getContent(event: ManagerEvent): String? {
        val resolved = if (event.id > 0L) resolveEventForMock(event) ?: return null else event
        val container = RegSecUtils.getContainerWithRegSec(resolved.payload, resolved.regSec)
            ?: return null
        return eventRepository.getContent(resolved.toEvent(), container)
    }

    override suspend fun deleteEvent(event: ManagerEvent): Boolean =
        eventRepository.deleteEvent(event.toEvent())

    override suspend fun restoreEvent(event: ManagerEvent): ManagerEvent? {
        val restoredId = eventRepository.restoreEvent(event.toEvent())
        return restoredId.takeIf { it > 0L }?.let { event.copy(id = it) }
    }

    override suspend fun countEventsByDay(): List<io.github.magisk317.mipush.common.manager.ManagerDayCount> =
        eventRepository.countEventsByDay().map {
            io.github.magisk317.mipush.common.manager.ManagerDayCount(day = it.day, count = it.count)
        }

    override suspend fun clearHistoryBefore(cutoffMillis: Long): Int =
        eventRepository.deleteHistoryBefore(cutoffMillis)

    override suspend fun clearHistoryInRange(startMillis: Long, endMillis: Long): Int =
        eventRepository.deleteHistoryInRange(startMillis, endMillis)

    private fun Event.toManagerEvent(): ManagerEvent {
        val eventType = TypeFactory.createForDisplay(this)
        val container = RegSecUtils.getContainerWithRegSec(this)
        val summary = eventType.getSummary(context).toString()
        val content = if (container != null) {
            eventRepository.getDecoratedSummary(summary, container)
        } else {
            summary
        }
        return ManagerEvent(
            id = id ?: 0L,
            userId = userId,
            packageName = pkg,
            configOptions = eventRepository.getStatus(container),
            channel = eventRepository.getStatusDescription(this),
            receiveDateMs = date,
            title = eventType.getTitle(context).toString(),
            content = content,
            appName = Global.applicationNameCache().getAppName(context, pkg)?.toString(),
            type = type,
            result = result,
            info = info,
            payload = payload,
            regSec = regSec,
        )
    }

    private fun ManagerEvent.toEvent(): Event =
        Event(
            id = id.takeIf { it > 0L },
            userId = userId,
            pkg = packageName,
            type = type,
            date = receiveDateMs,
            result = result,
            info = info,
            payload = payload,
            regSec = regSec,
        )
}

class XmsfManagerLogGateway : ManagerLogGateway {
    override suspend fun setRetentionDays(days: Int) {
        LogUtils.setRetentionDays(days)
    }

    override suspend fun buildLogBundle(context: Context): ManagerLogExportResult {
        val result = LogBundleExporter.buildLogBundle(
            context = context,
            mode = DiagnosticExportModes.fromDebugLoggingSetting(context),
        )
        return ManagerLogExportResult(file = result.file, details = result.details)
    }

    override fun buildShareIntent(context: Context, file: File): Intent =
        LogBundleExporter.buildShareIntent(context, file)

    override suspend fun clearLogFolders(context: Context): ManagerLogClearResult {
        val result = LogBundleExporter.clearLogFolders(context)
        return ManagerLogClearResult(success = result.success, details = result.details)
    }
}

class XmsfManagerPermissionGateway : ManagerPermissionGateway {
    companion object {
        private const val XSPACE_USER_ID = 999
        private val XSPACE_SUPPORT_PACKAGES = listOf(
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.android.externalstorage",
            "com.android.providers.downloads",
            "com.android.providers.media.module",
            "com.android.providers.media",
            "com.android.mtp",
        )
    }

    override suspend fun getRootAccessSnapshot(refresh: Boolean): ManagerRootAccessSnapshot {
        val granted = if (refresh) {
            PermissionUtils.refreshRootAccessIfGranted()
        } else {
            PermissionUtils.hasCachedRootAccess()
        }
        return rootSnapshot(
            runtimeState = if (granted) {
                ManagerRootAccessState.GRANTED
            } else {
                ManagerRootAccessState.NOT_GRANTED
            },
        )
    }

    override suspend fun requestRootAccess(target: ManagerRootTarget): ManagerRootAccessSnapshot {
        val runtimeState = if (target == ManagerRootTarget.RUNTIME && PermissionUtils.requestRootAccess()) {
            ManagerRootAccessState.GRANTED
        } else if (PermissionUtils.refreshRootAccessIfGranted()) {
            ManagerRootAccessState.GRANTED
        } else {
            ManagerRootAccessState.NOT_GRANTED
        }
        return rootSnapshot(runtimeState)
    }

    override suspend fun hasCachedRootAccess(): Boolean = PermissionUtils.hasCachedRootAccess()

    override suspend fun refreshRootAccessIfGranted(): Boolean = PermissionUtils.refreshRootAccessIfGranted()

    override suspend fun requestRootAccess(): Boolean = PermissionUtils.requestRootAccess()

    override suspend fun repairXSpaceUserSupport(): ManagerXSpaceRepairResult {
        if (!canManageDualAppFromUser(Utils.myUserId())) {
            return ManagerXSpaceRepairResult(
                stage = ManagerXSpaceRepairStage.PRIMARY_USER_REQUIRED,
                details = "currentUserId=${Utils.myUserId()}",
            )
        }
        if (!PermissionUtils.refreshRootAccessIfGranted()) {
            return ManagerXSpaceRepairResult(stage = ManagerXSpaceRepairStage.ROOT_MISSING)
        }
        val users = runRootCommand("cmd user list", timeoutMs = 5_000L)
        if (!users.isSuccess || !users.output.contains("{${XSPACE_USER_ID}:")) {
            return ManagerXSpaceRepairResult(
                stage = ManagerXSpaceRepairStage.XSPACE_USER_NOT_FOUND,
                details = users.output.ifBlank { users.stderrText },
            )
        }

        val managerInstalled = isPackageInstalledForUser(Constants.MANAGER_APP_NAME)
        if (!managerInstalled) {
            if (isPackageInstalledForUser(Constants.SERVICE_APP_NAME)) {
                uninstallForUser(Constants.SERVICE_APP_NAME)
            }
            val xmsfInstalled = isPackageInstalledForUser(Constants.SERVICE_APP_NAME)
            return ManagerXSpaceRepairResult(
                stage = if (!xmsfInstalled) {
                    ManagerXSpaceRepairStage.COMPLETED
                } else {
                    ManagerXSpaceRepairStage.PARTIAL_FAILED
                },
                xmsfInstalled = xmsfInstalled,
                documentsUiAvailable = false,
                details = "managerInstalled=false, xmsfInstalled=$xmsfInstalled",
            )
        }

        installExistingForUser(Constants.SERVICE_APP_NAME)
        XSPACE_SUPPORT_PACKAGES.forEach(::installExistingForUser)
        val xmsfInstalled = isPackageInstalledForUser(Constants.SERVICE_APP_NAME)
        val documentsUiAvailable = canResolveDocumentTreePicker()
        val details = buildString {
            append("managerInstalled=")
            append(managerInstalled)
            append(", ")
            append("xmsfInstalled=")
            append(xmsfInstalled)
            append(", documentsUiAvailable=")
            append(documentsUiAvailable)
        }
        return ManagerXSpaceRepairResult(
            stage = if (xmsfInstalled && documentsUiAvailable) {
                ManagerXSpaceRepairStage.COMPLETED
            } else {
                ManagerXSpaceRepairStage.PARTIAL_FAILED
            },
            xmsfInstalled = xmsfInstalled,
            documentsUiAvailable = documentsUiAvailable,
            details = details,
        )
    }

    override suspend fun setDualAppEnabled(enabled: Boolean): ManagerXSpaceRepairResult {
        if (!canManageDualAppFromUser(Utils.myUserId())) {
            return ManagerXSpaceRepairResult(
                stage = ManagerXSpaceRepairStage.PRIMARY_USER_REQUIRED,
                details = "currentUserId=${Utils.myUserId()}",
            )
        }
        // refreshRootAccessIfGranted() returns false while Shell grant state is still null
        // (unknown). requestRootAccess() forces Shell init + probe so Magisk-granted apps work.
        if (!PermissionUtils.refreshRootAccessIfGranted() && !PermissionUtils.requestRootAccess()) {
            return ManagerXSpaceRepairResult(stage = ManagerXSpaceRepairStage.ROOT_MISSING)
        }
        val users = runRootCommand("cmd user list", timeoutMs = 5_000L)
        if (!users.isSuccess || !users.output.contains("{${XSPACE_USER_ID}:")) {
            return ManagerXSpaceRepairResult(
                stage = ManagerXSpaceRepairStage.XSPACE_USER_NOT_FOUND,
                details = users.output.ifBlank { users.stderrText },
            )
        }

        if (enabled) {
            installExistingForUser(Constants.SERVICE_APP_NAME)
            installExistingForUser(Constants.MANAGER_APP_NAME)
        } else {
            uninstallForUser(Constants.SERVICE_APP_NAME)
            uninstallForUser(Constants.MANAGER_APP_NAME)
        }

        val xmsfInstalled = isPackageInstalledForUser(Constants.SERVICE_APP_NAME)
        val managerInstalled = isPackageInstalledForUser(Constants.MANAGER_APP_NAME)
        val expectedInstalled = enabled
        val succeeded = xmsfInstalled == expectedInstalled && managerInstalled == expectedInstalled
        if (succeeded) {
            // Persist runtime-owned dual_app_enabled so XSpaceXmsfInstallKeeper can keep packages in sync.
            try {
                val context = Utils.context?.applicationContext ?: return ManagerXSpaceRepairResult(
                    stage = ManagerXSpaceRepairStage.PARTIAL_FAILED,
                    details = "application_context_unavailable",
                )
                PreferenceRepository(context.dataStore).setDualAppEnabled(enabled)
                // Notify SystemUI / system_server IslandPreferences caches immediately.
                context.sendBroadcast(android.content.Intent(ACTION_PREF_CHANGED))
            } catch (_: RuntimeException) {
                // Package state remains authoritative even if the local mirror cannot update.
            }
            if (enabled) {
                // Root-grant silent permissions for primary + dual-space (xmsf + manager).
                PermissionUtils.grantSilentPermissionsForFramework(
                    userId = PermissionUtils.USER_AUTO,
                )
            } else {
                // Still re-assert main-user silent grants after dual-app teardown.
                PermissionUtils.grantSilentPermissionsForFramework(
                    userId = PermissionUtils.USER_PRIMARY,
                )
            }
        }

        return ManagerXSpaceRepairResult(
            stage = if (succeeded) {
                ManagerXSpaceRepairStage.COMPLETED
            } else {
                ManagerXSpaceRepairStage.PARTIAL_FAILED
            },
            xmsfInstalled = xmsfInstalled,
            details = "enabled=$enabled, xmsfInstalled=$xmsfInstalled, managerInstalled=$managerInstalled",
        )
    }

    override suspend fun getDualAppInstallation(): ManagerDualAppInstallationResult {
        if (!canManageDualAppFromUser(Utils.myUserId())) {
            return ManagerDualAppInstallationResult.Unavailable("primary_user_required")
        }
        if (!PermissionUtils.hasCachedRootAccess() &&
            !PermissionUtils.refreshRootAccessIfGranted()
        ) {
            return ManagerDualAppInstallationResult.Unavailable("root_unavailable")
        }
        val users = runRootCommand("cmd user list", timeoutMs = 5_000L)
        if (!users.isSuccess) {
            return ManagerDualAppInstallationResult.Unavailable("user_list_unavailable")
        }
        if (!users.output.contains("{${XSPACE_USER_ID}:")) {
            return ManagerDualAppInstallationResult.NotInstalled
        }
        return if (isPackageInstalledForUser(Constants.SERVICE_APP_NAME) &&
            isPackageInstalledForUser(Constants.MANAGER_APP_NAME)
        ) {
            ManagerDualAppInstallationResult.Installed
        } else {
            ManagerDualAppInstallationResult.NotInstalled
        }
    }

    override suspend fun launchAppOps(context: Context, permission: String, tips: CharSequence): Boolean =
        PermissionUtils.lunchAppOps(context, permission, tips)

    override suspend fun isUsageStatsAllowedByRoot(packageName: String): Boolean {
        if (!PermissionUtils.hasCachedRootAccess()) return false
        val commands = listOf(
            "appops get $packageName GET_USAGE_STATS",
            "appops get $packageName android:get_usage_stats",
            "cmd appops get $packageName GET_USAGE_STATS",
        )
        return commands.any { command ->
            val result = ShellUtils.execCmd(command, true, true)
            val output = buildString {
                append(result.successMsg.orEmpty())
                append('\n')
                append(result.errorMsg.orEmpty())
            }
            isUsageStatsAppOpAllowed(output)
        }
    }

    override suspend fun requestIgnoreBatteryOptimizations(context: Context): Boolean =
        PermissionUtils.requestIgnoreBatteryOptimizations(context)

    override suspend fun grantNotificationPermission(context: Context): Boolean =
        PermissionUtils.grantNotificationPermission(context)

    private fun rootSnapshot(runtimeState: ManagerRootAccessState): ManagerRootAccessSnapshot {
        val userId = Utils.myUserId()
        val managerUid = Utils.context?.packageManager?.let { packageManager ->
            runCatching { packageManager.getPackageUid(Constants.MANAGER_APP_NAME, 0) }.getOrNull()
        }
        return ManagerRootAccessSnapshot(
            userId = userId,
            manager = ManagerRootSubjectStatus(
                target = ManagerRootTarget.MANAGER,
                packageName = Constants.MANAGER_APP_NAME,
                userId = userId,
                uid = managerUid,
                state = ManagerRootAccessState.UNAVAILABLE,
            ),
            runtime = ManagerRootSubjectStatus(
                target = ManagerRootTarget.RUNTIME,
                packageName = Constants.SERVICE_APP_NAME,
                userId = userId,
                uid = Process.myUid(),
                state = runtimeState,
            ),
        )
    }

    private fun installExistingForUser(packageName: String) {
        runRootCommand(
            command = "cmd package install-existing --user $XSPACE_USER_ID --wait $packageName",
            timeoutMs = 15_000L,
        )
    }

    private fun uninstallForUser(packageName: String) {
        runRootCommand(
            command = "cmd package uninstall --user $XSPACE_USER_ID $packageName",
            timeoutMs = 15_000L,
        )
    }

    private fun isPackageInstalledForUser(packageName: String): Boolean {
        val result = runRootCommand(
            "cmd package list packages --user $XSPACE_USER_ID $packageName",
            timeoutMs = 5_000L,
        )
        return result.isSuccess && result.stdoutText.lineSequence().any { it.trim() == "package:$packageName" }
    }

    private fun canResolveDocumentTreePicker(): Boolean {
        val result = runRootCommand(
            command = "cmd package resolve-activity --brief --user $XSPACE_USER_ID -a android.intent.action.OPEN_DOCUMENT_TREE",
            timeoutMs = 5_000L,
        )
        return result.isSuccess && result.stdout.none { it.contains("No activity found", ignoreCase = true) } &&
            result.stdoutText.contains("/")
    }

    private fun runRootCommand(command: String, timeoutMs: Long) =
        io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand(
            command,
            timeoutMs = timeoutMs,
        )

    private val io.github.magisk317.mipush.platform.support.BoundedShellResult.output: String
        get() = buildString {
            append(stdoutText)
            if (stderrText.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(stderrText)
            }
        }
}

internal fun isUsageStatsAppOpAllowed(output: String): Boolean = output.lineSequence().any { line ->
    val normalized = line.trim().lowercase()
    val isUsageStatsLine = normalized.contains("get_usage_stats:") ||
        normalized.contains("android:get_usage_stats:")
    isUsageStatsLine && listOf("allow", "foreground", "default").any { mode ->
        normalized.contains(": $mode")
    }
}

internal fun canManageDualAppFromUser(userId: Int): Boolean = userId == PermissionUtils.USER_PRIMARY

class XmsfManagerApplicationGateway : ManagerApplicationGateway {

    override suspend fun loadApplications(
        context: Context,
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean,
    ): ManagerApplications {
        val timer = ElapsedTimer()
        val registered = RegisteredApplicationDb.getList(null)
            .filter { includeSystemApps || Utils.isUserApplication(it.packageName) }
            .associateBy { it.packageName }
            .toMutableMap()
        val readSource = AndroidManagerApplicationReadSource(context)
        val catalog = readSource.readInstalledApplications(includeSystemApps)
        val lastReceiveTimes = readSource.readLastReceiveTimes(
            catalog.applications.map(InstalledApplicationSnapshot::packageName),
        )
        val displayApplications = catalog.applications
            .map { installed ->
                val app = registered[installed.packageName] ?: RegisteredApplicationDb.registerApplication(installed.packageName)
                app.existServices = installed.hasMiPushServices
                app.appName = app.appName.takeIf { it.isNotBlank() }
                    ?: installed.appName
                app.appNamePinYin = app.appName.lowercase(Locale.ROOT)
                app.lastReceiveTime = Date(lastReceiveTimes[app.packageName] ?: 0L)
                app
            }
        reconcileLocalRegistrationState(displayApplications)
        val apps = displayApplications
            .asSequence()
            .map { it.toManagerApplication(deriveAppNamePinYin = true) }
            .filter { ManagerApplicationReadPolicy.matchesQuery(it, query) }
            .filter { ManagerApplicationReadPolicy.matchesFilter(it, filterMode) }
            .sortedWith(ManagerApplicationReadPolicy.comparator)
            .toList()
        Logger.withTag("XmsfManagerApplicationGateway").d {
            "manager app list loaded total=${catalog.totalCandidatePackages} shown=${apps.size} ms=${timer.elapsed()}"
        }
        return ManagerApplications(
            registeredPkgs = registered.mapValues {
                it.value.toManagerApplication(
                    deriveAppNamePinYin = it.value.appNamePinYin.isNotEmpty(),
                )
            },
            items = apps,
            totalPkg = catalog.totalCandidatePackages,
        )
    }

    private fun reconcileLocalRegistrationState(applications: List<RegisteredApplication>) {
        val candidates = applications
            .asSequence()
            .filter { it.registeredType == RegisteredApplication.RegisteredType.NotRegistered }
            .map { it.packageName }
            .toList()
        if (candidates.isEmpty()) return
        val locallyRegistered = RegistrationStateCompat.findPackagesWithValidLocalRegistration(candidates)
        if (locallyRegistered.isEmpty()) return
        applications.forEach { application ->
            if (application.packageName in locallyRegistered) {
                RegistrationStateStore.updateIfChanged(
                    application = application,
                    nextType = RegisteredApplication.RegisteredType.Registered,
                    source = RegistrationStateStore.Source.LOCAL_PROBE,
                )
            }
        }
    }

    override suspend fun getApplication(context: Context, packageName: String, ignoreNotRegistered: Boolean): ManagerApplication? {
        var application = RegisteredApplicationDb.getRegisteredApplication(packageName)
        if (application == null && ignoreNotRegistered) {
            application = RegisteredApplication().apply {
                this.packageName = packageName
                this.registeredType = RegisteredApplication.RegisteredType.NotRegistered
                this.appName = Global.applicationNameCache().getAppName(context, packageName).toString()
            }
        }
        if (application != null &&
            application.registeredType == RegisteredApplication.RegisteredType.NotRegistered &&
            RegistrationStateCompat.hasValidLocalRegistration(packageName)
        ) {
            RegistrationStateStore.updateIfChanged(
                application = application,
                nextType = RegisteredApplication.RegisteredType.Registered,
                source = RegistrationStateStore.Source.LOCAL_PROBE,
            )
        }
        application ?: return null
        refreshTransientState(context, application)
        return application.toManagerApplication()
    }

    override suspend fun updateApplication(application: ManagerApplication) {
        RegisteredApplicationDb.update(application.toRegisteredApplication())
    }

    override suspend fun getDiagnostics(packageName: String, registeredType: Int): ManagerApplicationDiagnostics {
        val latestRegistrationEvent = EventDb.queryAsync(
            skip = 0,
            limit = 1,
            types = setOf(
                Event.Type.Registration,
                Event.Type.RegistrationResult,
                Event.Type.UnRegistration,
            ),
            pkg = packageName,
            text = null,
        ).firstOrNull()
        val hasLocalRegistration = RegistrationStateCompat.hasValidLocalRegistration(packageName)
        val regSecCount = Utils.getRegSecs(packageName).size
        return ManagerApplicationDiagnostics(
            hasLocalRegistration = hasLocalRegistration,
            regSecCount = regSecCount,
            latestRegistrationEventResult = latestRegistrationEvent?.result,
            registeredType = registeredType,
            inferenceReason = ManagerApplicationReadPolicy.inferReason(
                registeredType = registeredType,
                latestEvent = latestRegistrationEvent?.let {
                    RegistrationEventSnapshot(type = it.type, result = it.result)
                },
                hasLocalRegistration = hasLocalRegistration,
                hasRegSec = regSecCount > 0,
            ),
        )
    }

    override suspend fun launchTargetAppAndForceRegister(
        context: Context,
        packageName: String,
        registeredType: Int,
    ): ManagerForceRegisterResult {
        // Force-register must actively request elevation (KSU/Magisk prompt).
        // refreshRootAccessIfGranted() returns false when grant state is still unknown.
        if (!PermissionUtils.requestRootAccess()) {
            Logger.withTag("XmsfManagerApplicationGateway").w {
                "force-register aborted: root not granted pkg=$packageName"
            }
            return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_requires_root),
            )
        }
        val plan = RegistrationHelper.inspectForceRegisterPlan(packageName)
        if (!plan.supportsServiceDispatch && !plan.supportsReceiverFallback && plan.bridgeCandidates.isEmpty()) {
            return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_unavailable),
            )
        }
        runCatching {
            io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand("am force-stop $packageName")
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_failed),
            )
        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (runCatching { context.startActivity(launchIntent) }.isFailure) {
            return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_failed),
            )
        }
        kotlinx.coroutines.delay(500)
        return forceRegisterWithFeedback(context, packageName, registeredType)
    }

    private fun forceRegisterWithFeedback(
        context: Context,
        packageName: String,
        registeredType: Int,
    ): ManagerForceRegisterResult {
        // launchTargetAppAndForceRegister already requested and verified root for this operation.
        // Do not trigger a second Magisk/KernelSU authorization request after launching the app.
        if (
            registeredType != ManagerApplication.RegisteredType.REGISTERED &&
            RegistrationStateCompat.hasLocalRegistrationArtifacts(packageName)
        ) {
            runCatching { RegistrationHelper(context, packageName).removeMiPushData() }
        }
        val result = runCatching { RegistrationHelper.tryForceRegister(packageName) }
        if (result.getOrDefault(false)) {
            return ManagerForceRegisterResult(
                succeeded = true,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_sent),
            )
        }
        val cause = result.exceptionOrNull()
        if (cause is NoClassDefFoundError || cause is ClassNotFoundException) {
            return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_unavailable),
            )
        }
        return if (runCatching { RegistrationHelper.tryForceRegisterFallback(packageName) }.getOrDefault(false)) {
            ManagerForceRegisterResult(
                succeeded = true,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_sent),
            )
        } else {
            ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_failed),
            )
        }
    }

    private suspend fun refreshTransientState(context: Context, application: RegisteredApplication) {
        val readSource = AndroidManagerApplicationReadSource(context)
        application.lastReceiveTime = Date(readSource.readLastReceiveTime(application.packageName))
        application.existServices = readSource.readInstalledApplication(application.packageName)
            ?.hasMiPushServices == true
    }
}

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

private fun RegisteredApplication.toManagerApplication(
    deriveAppNamePinYin: Boolean = false,
): ManagerApplication =
    toStoredApplicationSnapshot().toManagerApplication(
        installed = InstalledApplicationSnapshot(
            packageName = packageName,
            appName = appName,
            hasMiPushServices = existServices,
        ),
        lastReceiveTimeMs = lastReceiveTime.time,
        locallyRegistered = false,
        fallbackToInstalledName = false,
        deriveAppNamePinYin = deriveAppNamePinYin,
    )

private fun ManagerApplication.toRegisteredApplication(): RegisteredApplication =
    RegisteredApplication(
        id = id,
        packageName = packageName,
        type = type,
        notificationOnRegister = notificationOnRegister,
        registeredType = when (registeredType) {
            ManagerApplication.RegisteredType.REGISTERED -> RegisteredApplication.RegisteredType.Registered
            ManagerApplication.RegisteredType.UNREGISTERED -> RegisteredApplication.RegisteredType.Unregistered
            else -> RegisteredApplication.RegisteredType.NotRegistered
        },
        appName = appName,
        blocked = blocked,
        islandEnabled = islandEnabled,
        islandFocusNotification = islandFocusNotification,
    ).also {
        it.userId = userId
        it.existServices = existServices
        it.appNamePinYin = appNamePinYin
        it.lastReceiveTime = Date(lastReceiveTimeMs)
    }

class XmsfZygiskConfigGateway : io.github.magisk317.mipush.common.manager.ZygiskConfigGateway {
    companion object {
        private const val ZYGISK_CONFIG_PATH = "/data/adb/mipush_zygisk/app.conf"
    }

    private fun hasExistingRootForZygisk(): Boolean =
        io.github.magisk317.mipush.platform.support.PermissionUtils.hasCachedRootAccess() ||
            io.github.magisk317.mipush.platform.support.PermissionUtils.refreshRootAccessIfGranted()

    override suspend fun isZygiskModuleEnabled(): io.github.magisk317.mipush.common.manager.ZygiskModuleReadResult {
        if (!hasExistingRootForZygisk()) {
            return io.github.magisk317.mipush.common.manager.ZygiskModuleReadResult.Unavailable("zygisk_root_missing")
        }
        return try {
            val getPropMethod = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java, String::class.java)
            val result = getPropMethod.invoke(null, "mipush.zygisk.enabled", "false") as String
            io.github.magisk317.mipush.common.manager.ZygiskModuleReadResult.Available(result == "true")
        } catch (_: Exception) {
            io.github.magisk317.mipush.common.manager.ZygiskModuleReadResult.Unavailable("zygisk_status_read_failed")
        }
    }

    override fun getZygiskConfigPath(): String = ZYGISK_CONFIG_PATH

    override suspend fun getZygiskConfig(): io.github.magisk317.mipush.common.manager.ZygiskConfigReadResult {
        if (!hasExistingRootForZygisk()) {
            return io.github.magisk317.mipush.common.manager.ZygiskConfigReadResult.Unavailable("zygisk_root_missing")
        }
        val result = io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand(
            "cat $ZYGISK_CONFIG_PATH",
            timeoutMs = 5_000L,
        )
        if (!result.isSuccess) {
            return io.github.magisk317.mipush.common.manager.ZygiskConfigReadResult.Unavailable(
                result.stderr.joinToString(" ").ifBlank { "zygisk_config_read_failed" },
            )
        }
        return io.github.magisk317.mipush.common.manager.ZygiskConfigReadResult.Available(
            ZygiskConfig.parse(result.stdout.joinToString("\n")),
        )
    }

    override suspend fun saveZygiskConfig(config: ZygiskConfig): Boolean {
        if (!hasExistingRootForZygisk()) return false
        val content = config.toFileContent()
        val command = listOf(
            "mkdir -p /data/adb/mipush_zygisk",
            "chmod 700 /data/adb/mipush_zygisk",
            "printf %s ${shellQuote(content)} > $ZYGISK_CONFIG_PATH",
            "chmod 600 $ZYGISK_CONFIG_PATH",
        ).joinToString(" && ")
        val result = io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand(
            command,
            timeoutMs = 5_000L,
        )
        return result.isSuccess
    }

    override suspend fun forceStopApp(packageName: String): Boolean {
        if (!hasExistingRootForZygisk()) return false
        return io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand(
            "am force-stop ${shellQuote(packageName)}",
            timeoutMs = 5_000L,
        ).isSuccess
    }

    override suspend fun scanZygiskPackages(): io.github.magisk317.mipush.common.manager.ZygiskPackageScanResult {
        if (!hasExistingRootForZygisk()) {
            return io.github.magisk317.mipush.common.manager.ZygiskPackageScanResult.Unavailable("zygisk_root_missing")
        }
        val result = io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand(
            "/system/bin/sh /data/adb/modules/mipush_zygisk/bin/mipushctl scan",
            timeoutMs = 30_000L,
        )
        return if (result.isSuccess) {
            io.github.magisk317.mipush.common.manager.ZygiskPackageScanResult.Available(
                result.stdout.joinToString("\n"),
            )
        } else {
            io.github.magisk317.mipush.common.manager.ZygiskPackageScanResult.Unavailable(
                result.stderr.joinToString(" ").ifBlank { "zygisk_scan_failed" },
            )
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
