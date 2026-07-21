package io.github.magisk317.mipush.app.di

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.app.MiPushFrameworkApp
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.fakedevice.ZygiskConfig
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerApplications
import io.github.magisk317.mipush.common.manager.ManagerConfigEditorSnapshot
import io.github.magisk317.mipush.common.manager.ManagerConfigGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigListSnapshot
import io.github.magisk317.mipush.common.manager.ManagerConfigSyncGateway
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.common.manager.ManagerEventResult
import io.github.magisk317.mipush.common.manager.ManagerEventType
import io.github.magisk317.mipush.common.manager.ManagerLogClearResult
import io.github.magisk317.mipush.common.manager.ManagerLogExportResult
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.common.manager.ManagerNotificationGateway
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
import io.github.magisk317.mipush.common.manager.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairResult
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.common.notification.MockReplayOutcome
import io.github.magisk317.mipush.config.ConfigNavigationHelper
import io.github.magisk317.mipush.config.ConfigSyncRepository
import io.github.magisk317.mipush.config.toSummary
import io.github.magisk317.mipush.common.utils.ElapsedTimer
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.compat.RegistrationStateCompat
import io.github.magisk317.mipush.compat.RegistrationStateStore
import io.github.magisk317.mipush.notification.NotificationChannelManager
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
) : ManagerConfigGateway {
    override suspend fun getXmppServer(): String? = configCenter.getXMPPServerAsync()

    override suspend fun getConfigurationDirectory(): Uri? = configCenter.getConfigurationDirectoryAsync()

    override suspend fun setConfigurationDirectory(uri: Uri): Boolean =
        configCenter.setConfigurationDirectoryAsync(uri)

    override fun loadConfigurations(context: Context) {
        configCenter.loadConfigurations(context)
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

    private fun io.github.magisk317.mipush.config.ConfigEditorSnapshot.toManagerConfigEditorSnapshot() =
        ManagerConfigEditorSnapshot(
            path = path,
            local = local,
            remote = remote,
            remoteMeta = remoteMeta,
            localMeta = localMeta?.toSummary(),
            remoteError = remoteError,
        )
}

class XmsfManagerNotificationGateway : ManagerNotificationGateway {
    override val isHooked: Boolean
        get() = NotificationManagerEx.isHooked

    // filterNotNull/orEmpty: Ex layer may return nullable list/elements; manager UI expects non-null.
    override fun getNotificationChannels(packageName: String): List<NotificationChannel> =
        NotificationManagerEx.getNotificationChannels(packageName)?.filterNotNull().orEmpty()

    override fun getNotificationChannelGroups(packageName: String): List<NotificationChannelGroup> =
        NotificationManagerEx.getNotificationChannelGroups(packageName)?.filterNotNull().orEmpty()

    override fun deleteNotificationChannel(packageName: String, channelId: String) {
        NotificationManagerEx.deleteNotificationChannel(packageName, channelId)
    }

    override fun isNotificationChannelEnabled(channel: NotificationChannel): Boolean =
        NotificationChannelManager.isNotificationChannelEnabled(channel)
}

class XmsfManagerEventGateway(
    private val context: Context,
    private val eventRepository: EventRepository,
) : ManagerEventGateway {
    override fun getEventsById(lastId: Long?, size: Int, packageName: String, query: String): List<ManagerEvent> =
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
        val container = io.github.magisk317.mipush.common.configurations.RegSecUtils
            .getContainerWithRegSec(event.payload, event.regSec)
            ?: return MockReplayOutcome.Failed
        return eventRepository.mockMessage(container)
    }

    override fun getJson(event: ManagerEvent): String? =
        eventRepository.getJson(event.toEvent())?.toString()

    override fun getContent(event: ManagerEvent): String {
        val container = io.github.magisk317.mipush.common.configurations.RegSecUtils
            .getContainerWithRegSec(event.payload, event.regSec)
            ?: return event.content
        return eventRepository.getContent(event.toEvent(), container)
    }

    override suspend fun deleteEvent(event: ManagerEvent): Boolean =
        eventRepository.deleteEvent(event.toEvent())

    override suspend fun restoreEvent(event: ManagerEvent): ManagerEvent? {
        val restoredId = eventRepository.restoreEvent(event.toEvent())
        return event.copy(id = restoredId)
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
    override fun setRetentionDays(days: Int) {
        LogUtils.setRetentionDays(days)
    }

    override fun buildLogBundle(context: Context): ManagerLogExportResult {
        val result = LogBundleExporter.buildLogBundle(context)
        return ManagerLogExportResult(file = result.file, details = result.details)
    }

    override fun buildShareIntent(context: Context, file: File): Intent =
        LogBundleExporter.buildShareIntent(context, file)

    override fun clearLogFolders(context: Context): ManagerLogClearResult {
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

    override fun hasCachedRootAccess(): Boolean = PermissionUtils.hasCachedRootAccess()

    override fun refreshRootAccessIfGranted(): Boolean = PermissionUtils.refreshRootAccessIfGranted()

    override fun requestRootAccess(): Boolean = PermissionUtils.requestRootAccess()

    override fun repairXSpaceUserSupport(): ManagerXSpaceRepairResult {
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

    override fun setDualAppEnabled(enabled: Boolean): ManagerXSpaceRepairResult {
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

    override fun isDualAppInstalled(): Boolean {
        if (!PermissionUtils.hasCachedRootAccess()) return false
        val users = runRootCommand("cmd user list", timeoutMs = 5_000L)
        if (!users.isSuccess || !users.output.contains("{${XSPACE_USER_ID}:")) return false
        return isPackageInstalledForUser(Constants.SERVICE_APP_NAME) &&
            isPackageInstalledForUser(Constants.MANAGER_APP_NAME)
    }

    override fun launchAppOps(context: Context, permission: String, tips: CharSequence): Boolean =
        PermissionUtils.lunchAppOps(context, permission, tips)

    override fun isUsageStatsAllowedByRoot(packageName: String): Boolean {
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
            output.contains("GET_USAGE_STATS: allow", ignoreCase = true) ||
                output.contains("android:get_usage_stats: allow", ignoreCase = true)
        }
    }

    override fun requestIgnoreBatteryOptimizations(context: Context): Boolean =
        PermissionUtils.requestIgnoreBatteryOptimizations(context)

    override fun grantNotificationPermission(context: Context): Boolean =
        PermissionUtils.grantNotificationPermission(context)

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

class XmsfManagerApplicationGateway : ManagerApplicationGateway {

    override fun loadApplications(
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
        Napier.d(
            "manager app list loaded total=${catalog.totalCandidatePackages} shown=${apps.size} ms=${timer.elapsed()}",
            tag = "XmsfManagerApplicationGateway",
        )
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

    override fun getApplication(context: Context, packageName: String, ignoreNotRegistered: Boolean): ManagerApplication? {
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

    override fun updateApplication(application: ManagerApplication) {
        RegisteredApplicationDb.update(application.toRegisteredApplication())
    }

    override fun getDiagnostics(packageName: String, registeredType: Int): ManagerApplicationDiagnostics {
        val latestRegistrationEvent = runBlocking {
            EventDb.queryAsync(
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
        }
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

    override suspend fun launchTargetAppAndForceRegister(context: Context, packageName: String, registeredType: Int): String {
        // Force-register must actively request elevation (KSU/Magisk prompt).
        // refreshRootAccessIfGranted() returns false when grant state is still unknown.
        if (!PermissionUtils.requestRootAccess()) {
            Napier.w(
                "force-register aborted: root not granted pkg=$packageName",
                tag = "XmsfManagerApplicationGateway",
            )
            return context.getString(com.xiaomi.xmsf.R.string.force_register_requires_root)
        }
        val plan = RegistrationHelper.inspectForceRegisterPlan(packageName)
        if (!plan.supportsServiceDispatch && !plan.supportsReceiverFallback && plan.bridgeCandidates.isEmpty()) {
            return context.getString(com.xiaomi.xmsf.R.string.force_register_unavailable)
        }
        runCatching {
            io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand("am force-stop $packageName")
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return context.getString(com.xiaomi.xmsf.R.string.force_register_failed)
        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (runCatching { context.startActivity(launchIntent) }.isFailure) {
            return context.getString(com.xiaomi.xmsf.R.string.force_register_failed)
        }
        kotlinx.coroutines.delay(500)
        return forceRegisterWithFeedback(context, packageName, registeredType)
    }

    private fun forceRegisterWithFeedback(context: Context, packageName: String, registeredType: Int): String {
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
            return context.getString(com.xiaomi.xmsf.R.string.force_register_sent)
        }
        val cause = result.exceptionOrNull()
        if (cause is NoClassDefFoundError || cause is ClassNotFoundException) {
            return context.getString(com.xiaomi.xmsf.R.string.force_register_unavailable)
        }
        return if (runCatching { RegistrationHelper.tryForceRegisterFallback(packageName) }.getOrDefault(false)) {
            context.getString(com.xiaomi.xmsf.R.string.force_register_sent)
        } else {
            context.getString(com.xiaomi.xmsf.R.string.force_register_failed)
        }
    }

    private fun refreshTransientState(context: Context, application: RegisteredApplication) {
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

    override fun startMiPushServiceAsForegroundService(context: Context) {
        runtimeSettingsAdapter.startMiPushServiceAsForegroundService(context)
    }

    override fun resetTopActivityCache() {
        runtimeSettingsAdapter.resetTopActivityCache()
    }

    override fun sendXmppReconnectRequest(context: Context) {
        runtimeSettingsAdapter.sendXmppReconnectRequest(context)
    }

    override fun setXmppServer(context: Context, newHost: String) {
        runtimeSettingsAdapter.setXmppServer(context, newHost)
    }

    override fun getXmppServerHint(): String = runtimeSettingsAdapter.getXmppServerHint()

    override fun getRuntimeEnvironmentSnapshot(context: Context): ManagerRuntimeEnvironmentSnapshot {
        return runtimeSettingsAdapter.getRuntimeEnvironmentSnapshot(context)
    }

    override fun getConnectionSnapshot() = runtimeSettingsAdapter.getConnectionSnapshot()

    override fun observeNotificationEvent(packageName: String, action: String, source: String) {
        PushRuntime.observeNotificationEvent(packageName, action, source)
    }

    override fun setRuntimeLogRetentionDays(days: Int) {
        LogUtils.setRetentionDays(days)
    }

    override fun applyEventRetentionDays(days: Int) {
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
        it.existServices = existServices
        it.appNamePinYin = appNamePinYin
        it.lastReceiveTime = Date(lastReceiveTimeMs)
    }

class XmsfZygiskConfigGateway : io.github.magisk317.mipush.common.manager.ZygiskConfigGateway {
    companion object {
        private const val ZYGISK_CONFIG_PATH = "/data/adb/mipush_zygisk/app.conf"
    }

    override fun isZygiskModuleEnabled(): Boolean {
        if (!io.github.magisk317.mipush.platform.support.PermissionUtils.hasCachedRootAccess() &&
            !io.github.magisk317.mipush.platform.support.PermissionUtils.refreshRootAccessIfGranted()
        ) {
            return false
        }
        return try {
            val getPropMethod = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
            val result = getPropMethod.invoke(null, "mipush.zygisk.enabled", "false") as String
            result == "true"
        } catch (_: Exception) {
            false
        }
    }

    override fun getZygiskConfigPath(): String = ZYGISK_CONFIG_PATH

    override fun getZygiskConfig(): ZygiskConfig {
        if (!io.github.magisk317.mipush.platform.support.PermissionUtils.refreshRootAccessIfGranted()) return ZygiskConfig()
        val result = io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand(
            "cat $ZYGISK_CONFIG_PATH",
            timeoutMs = 5_000L
        )
        if (!result.isSuccess) return ZygiskConfig()
        return ZygiskConfig.parse(result.stdout.joinToString("\n"))
    }

    override fun saveZygiskConfig(config: ZygiskConfig): Boolean {
        if (!io.github.magisk317.mipush.platform.support.PermissionUtils.refreshRootAccessIfGranted()) return false
        val content = config.toFileContent()
        val command = listOf(
            "mkdir -p /data/adb/mipush_zygisk",
            "chmod 700 /data/adb/mipush_zygisk",
            "printf %s ${shellQuote(content)} > $ZYGISK_CONFIG_PATH",
            "chmod 600 $ZYGISK_CONFIG_PATH",
        ).joinToString(" && ")
        val result = io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand(
            command,
            timeoutMs = 5_000L
        )
        return result.isSuccess
    }

    override fun forceStopApp(packageName: String) {
        if (!io.github.magisk317.mipush.platform.support.PermissionUtils.refreshRootAccessIfGranted()) return
        io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand("am force-stop $packageName", timeoutMs = 5_000L)
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
