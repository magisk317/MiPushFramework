package io.github.magisk317.mipush.app.di

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import io.github.aakira.napier.Napier
import com.xiaomi.push.sdk.PushMessageProcessor
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge
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
import io.github.magisk317.mipush.common.manager.ManagerRuntimeLogFileContent
import io.github.magisk317.mipush.common.manager.ManagerRuntimeLogFileInfo
import io.github.magisk317.mipush.common.manager.ManagerRuntimeLogFileSummary
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairResult
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.config.ConfigNavigationHelper
import io.github.magisk317.mipush.config.ConfigSyncRepository
import io.github.magisk317.mipush.config.toSummary
import io.github.magisk317.mipush.common.utils.ElapsedTimer
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.compat.RegistrationStateCompat
import io.github.magisk317.mipush.compat.RegistrationStateStore
import io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind
import io.github.magisk317.mipush.notification.NotificationChannelManager
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.MiPushManifestChecker
import io.github.magisk317.mipush.platform.support.PermissionUtils
import io.github.magisk317.mipush.platform.support.ShellUtils
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.data.EventRepository
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication
import io.github.magisk317.mipush.runtime.store.event.type.NotificationType
import io.github.magisk317.mipush.runtime.store.event.type.TypeFactory
import io.github.magisk317.mipush.service.runtime.RuntimeSettingsAdapter
import io.github.magisk317.mipush.utils.RegSecUtils
import io.github.magisk317.mipush.utils.LogBundleExporter
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.mipush.utils.RegistrationHelper
import java.io.File
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

    override suspend fun importDocuments(treeUri: Uri, uris: List<Uri>): Int =
        syncRepository.importDocuments(treeUri, uris)

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

    override fun mockMessage(event: ManagerEvent): Boolean {
        val container = io.github.magisk317.mipush.common.configurations.RegSecUtils
            .getContainerWithRegSec(event.payload, event.regSec)
            ?: return false
        eventRepository.mockMessage(container)
        return true
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

    override fun summarizeFiles(context: Context): ManagerRuntimeLogFileSummary {
        val summary = LogUtils.summarizeFiles(context)
        return ManagerRuntimeLogFileSummary(
            fileCount = summary.fileCount,
            totalBytes = summary.totalBytes,
            entryCount = summary.entryCount,
            firstTimestamp = summary.firstTimestamp,
            lastTimestamp = summary.lastTimestamp,
            files = summary.files.map {
                ManagerRuntimeLogFileInfo(
                    name = it.name,
                    sizeBytes = it.sizeBytes,
                    lineCount = it.lineCount,
                    lastTimestamp = it.lastTimestamp,
                )
            },
        )
    }

    override fun readLogFile(context: Context, fileName: String): ManagerRuntimeLogFileContent? {
        val content = LogUtils.readLogFile(context, fileName) ?: return null
        return ManagerRuntimeLogFileContent(name = content.name, text = content.text)
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

        installExistingForUser(Constants.SERVICE_APP_NAME)
        XSPACE_SUPPORT_PACKAGES.forEach(::installExistingForUser)
        val xmsfInstalled = isPackageInstalledForUser(Constants.SERVICE_APP_NAME)
        val documentsUiAvailable = canResolveDocumentTreePicker()
        val details = buildString {
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

    private fun isPackageInstalledForUser(packageName: String): Boolean {
        val result = runRootCommand("pm path --user $XSPACE_USER_ID $packageName", timeoutMs = 5_000L)
        return result.isSuccess && result.stdoutText.contains("package:")
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

    override fun loadApplications(context: Context, query: String, filterMode: Int): ManagerApplications {
        val timer = ElapsedTimer()
        val registered = RegisteredApplicationDb.getList(null)
            .filter { Utils.isUserApplication(it.packageName) }
            .associateBy { it.packageName }
            .toMutableMap()
        val packageInfos = loadPackagesOnDevice(context).filter(::isUserApplication).toMutableList()
        val total = packageInfos.size
        val lastReceiveTimes = runBlocking { EventDb.getAllLastReceiveTimesAsync() }
        val checker = createManifestChecker(context)
        val displayApplications = packageInfos
            .asSequence()
            .filter { shouldShowInList(it, checker) }
            .map { info ->
                val app = registered[info.packageName] ?: RegisteredApplicationDb.registerApplication(info.packageName)
                app.existServices = hasMiPushServices(checker, info)
                app.appName = app.appName.takeIf { it.isNotBlank() }
                    ?: Global.applicationNameCache().getAppName(context, app.packageName).toString()
                app.appNamePinYin = app.appName.lowercase(Locale.ROOT)
                app.lastReceiveTime = Date(maxOf(lastReceiveTimes[app.packageName] ?: 0L, Utils.getLastReceiveTime(app.packageName) ?: 0L))
                app
            }
            .toList()
        reconcileLocalRegistrationState(displayApplications)
        val apps = displayApplications
            .asSequence()
            .map { it.toManagerApplication() }
            .filter { isQueryMatched(it, query) }
            .filter { matchesFilter(it, filterMode) }
            .sortedWith(::compareForDisplay)
            .toList()
        Napier.d("manager app list loaded total=$total shown=${apps.size} ms=${timer.elapsed()}", tag = "XmsfManagerApplicationGateway")
        return ManagerApplications(
            registeredPkgs = registered.mapValues { it.value.toManagerApplication() },
            items = apps,
            totalPkg = total,
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
            inferenceReason = inferReason(
                registeredType = registeredType,
                latestEvent = latestRegistrationEvent,
                hasLocalRegistration = hasLocalRegistration,
                hasRegSec = regSecCount > 0,
            ),
        )
    }

    override fun loadIntegrationTypeReason(context: Context, packageName: String): String {
        val packageInfo = runCatching {
            PackageManagerCompatBridge.getPackageInfo(
                context.packageManager,
                packageName,
                PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS,
            )
        }.getOrNull() ?: return "package_not_found"
        val serviceNames = packageInfo.services?.mapNotNull(ServiceInfo::name)?.toSet().orEmpty()
        val receiverNames = packageInfo.receivers?.mapNotNull { it.name }?.toSet().orEmpty()
        return RegistrationHelper.classifyDisplayTypeReason(serviceNames, receiverNames)
    }

    override suspend fun launchTargetAppAndForceRegister(context: Context, packageName: String, registeredType: Int): String {
        if (!PermissionUtils.refreshRootAccessIfGranted()) {
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
        if (!PermissionUtils.refreshRootAccessIfGranted()) {
            return context.getString(com.xiaomi.xmsf.R.string.force_register_requires_root)
        }
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
        application.lastReceiveTime = Date(Utils.getLastReceiveTime(application.packageName) ?: 0L)
        val packageInfo = runCatching {
            PackageManagerCompatBridge.getPackageInfo(
                context.packageManager,
                application.packageName,
                PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS,
            )
        }.getOrNull()
        application.existServices = packageInfo?.let { hasMiPushServices(createManifestChecker(context), it) } ?: false
    }

    private fun loadPackagesOnDevice(context: Context): MutableList<PackageInfo> {
        val packageManager = context.packageManager
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
        return try {
            PackageManagerCompatBridge.getInstalledPackages(packageManager, 0).map { info ->
                runCatching { PackageManagerCompatBridge.getPackageInfo(packageManager, info.packageName, flags) }.getOrElse { info }
            }.toMutableList()
        } catch (error: RuntimeException) {
            Napier.e("Failed to load installed packages", error, tag = "XmsfManagerApplicationGateway")
            mutableListOf()
        }
    }

    private fun shouldShowInList(info: PackageInfo, checker: MiPushManifestChecker?): Boolean =
        isUserApplication(info) && hasMiPushServices(checker, info)

    private fun isUserApplication(info: PackageInfo): Boolean {
        val appInfo = info.applicationInfo ?: return false
        return (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED) != 0 && Utils.isUserApplication(appInfo)
    }

    private fun hasMiPushServices(checker: MiPushManifestChecker?, info: PackageInfo): Boolean {
        val serviceNames = info.services?.mapNotNull(ServiceInfo::name)?.toSet().orEmpty()
        val receiverNames = info.receivers?.mapNotNull { it.name }?.toSet().orEmpty()
        val plan = RegistrationHelper.classifyForceRegisterPlan(
            packageName = info.packageName,
            serviceNames = serviceNames,
            receiverNames = receiverNames,
        )
        if (plan.serviceCandidates.isEmpty() && plan.receiverCandidates.isEmpty() && plan.bridgeCandidates.isEmpty()) {
            return false
        }
        if (plan.serviceCandidates.isNotEmpty()) {
            checker?.checkServices(info)
        }
        return true
    }

    private fun createManifestChecker(context: Context): MiPushManifestChecker? =
        runCatching { MiPushManifestChecker.create(context) }.getOrNull()

    private fun isQueryMatched(info: ManagerApplication, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.lowercase()
        return info.packageName.lowercase().contains(q) ||
            info.appName.lowercase().contains(q) ||
            info.appNamePinYin.lowercase().contains(q)
    }

    private fun matchesFilter(info: ManagerApplication, filterMode: Int): Boolean =
        when (filterMode) {
            1 -> info.registeredType == ManagerApplication.RegisteredType.REGISTERED
            2 -> info.registeredType == ManagerApplication.RegisteredType.NOT_REGISTERED && info.lastReceiveTimeMs == 0L
            3 -> info.registeredType == ManagerApplication.RegisteredType.UNREGISTERED && info.lastReceiveTimeMs == 0L
            else -> true
        }

    private fun compareForDisplay(o1: ManagerApplication, o2: ManagerApplication): Int {
        fun priority(app: ManagerApplication): Int = when {
            app.registeredType == ManagerApplication.RegisteredType.REGISTERED -> 0
            app.lastReceiveTimeMs > 0L -> 1
            app.registeredType == ManagerApplication.RegisteredType.UNREGISTERED -> 2
            else -> 3
        }
        val p = priority(o1) - priority(o2)
        if (p != 0) return p
        val t = o2.lastReceiveTimeMs.compareTo(o1.lastReceiveTimeMs)
        if (t != 0) return t
        return o1.appNamePinYin.compareTo(o2.appNamePinYin)
    }

    private fun inferReason(
        registeredType: Int,
        latestEvent: Event?,
        hasLocalRegistration: Boolean,
        hasRegSec: Boolean,
    ): String {
        if (registeredType == ManagerApplication.RegisteredType.REGISTERED) return "registered"
        if (latestEvent == null && !hasLocalRegistration && !hasRegSec) return "never_attempted"
        if (latestEvent?.type == Event.Type.UnRegistration) return "unregistered_after_attempt"
        if (latestEvent?.type == Event.Type.RegistrationResult && latestEvent.result != ManagerEventResult.OK) {
            return "registration_result_failed"
        }
        if (latestEvent?.type == Event.Type.Registration) return "registering_or_waiting_result"
        if (hasLocalRegistration && registeredType != ManagerApplication.RegisteredType.REGISTERED) return "local_state_stale"
        if (hasRegSec && !hasLocalRegistration) return "has_secret_but_no_local_reg"
        return "unknown"
    }
}

class XmsfManagerRuntimeActions(
    private val runtimeSettingsAdapter: RuntimeSettingsAdapter,
    private val pushMessageProcessor: PushMessageProcessor,
) : ManagerRuntimeActions {
    override suspend fun clearHistory() {
        EventDb.deleteHistoryAsync()
    }

    override fun startMiPushServiceAsForegroundService(context: Context) {
        runtimeSettingsAdapter.startMiPushServiceAsForegroundService(context)
    }

    override fun notifyMockNotification(context: Context, kind: MockNotificationKind, packageName: String) {
        NotificationController.testMock(context, kind, packageName)
        runCatching {
            val type = NotificationType("mock:${kind.name}", packageName, null).apply {
                this.type = Event.Type.SendMessage
            }
            runBlocking { EventDb.insertEventAsync(Event.ResultType.OK, type) }
        }.onSuccess { eventId ->
            Napier.d(
                "mock test record inserted id=$eventId kind=${kind.name} pkg=$packageName",
                tag = "XmsfManagerRuntimeActions",
            )
            observeNotificationEvent(packageName, "mock_test_record_saved", "XmsfManagerRuntimeActions.notifyMockNotification")
        }.onFailure { error ->
            Napier.e(
                "mock test record insert failed kind=${kind.name} pkg=$packageName",
                error,
                tag = "XmsfManagerRuntimeActions",
            )
            observeNotificationEvent(packageName, "mock_test_record_save_failed", "XmsfManagerRuntimeActions.notifyMockNotification")
        }
    }

    override fun resetTopActivityCache() {
        pushMessageProcessor.resetTopActivityCache()
    }

    override fun sendXmppReconnectRequest(context: Context) {
        runtimeSettingsAdapter.sendXmppReconnectRequest(context)
    }

    override fun setXmppServer(context: Context, newHost: String) {
        runtimeSettingsAdapter.setXmppServer(context, newHost)
    }

    override fun getXmppServerHint(): String = runtimeSettingsAdapter.getXmppServerHint()

    override fun observeNotificationEvent(packageName: String, action: String, source: String) {
        PushRuntime.observeNotificationEvent(packageName, action, source)
    }

    override fun setRuntimeLogRetentionDays(days: Int) {
        LogUtils.setRetentionDays(days)
    }
}

private fun RegisteredApplication.toManagerApplication(): ManagerApplication =
    ManagerApplication(
        id = id,
        packageName = packageName,
        type = type,
        notificationOnRegister = notificationOnRegister,
        blocked = blocked,
        islandEnabled = islandEnabled,
        islandFocusNotification = islandFocusNotification,
        registeredType = when (registeredType) {
            RegisteredApplication.RegisteredType.Registered -> ManagerApplication.RegisteredType.REGISTERED
            RegisteredApplication.RegisteredType.Unregistered -> ManagerApplication.RegisteredType.UNREGISTERED
            else -> ManagerApplication.RegisteredType.NOT_REGISTERED
        },
        existServices = existServices,
        appName = appName,
        appNamePinYin = appNamePinYin,
        lastReceiveTimeMs = lastReceiveTime.time,
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
