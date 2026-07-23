package io.github.magisk317.mipush.manager.remote

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.github.magisk317.mipush.common.fakedevice.ZygiskConfig
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerApplications
import io.github.magisk317.mipush.common.manager.ManagerConfigEditorSnapshot
import io.github.magisk317.mipush.common.manager.ManagerConfigGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigListSnapshot
import io.github.magisk317.mipush.common.manager.ManagerConfigSyncGateway
import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.common.manager.ManagerDayCount
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.common.manager.ManagerLogClearResult
import io.github.magisk317.mipush.common.manager.ManagerLogExportResult
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.common.manager.ManagerNotificationGateway
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
import io.github.magisk317.mipush.common.manager.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairResult
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.common.manager.ZygiskConfigGateway
import io.github.magisk317.mipush.common.notification.MockReplayOutcome
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.application.ApplicationListRequest
import io.github.magisk317.mipush.manager.application.ApplicationReadResult
import io.github.magisk317.mipush.manager.application.RemoteApplicationDetailSource
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSourceResult
import io.github.magisk317.mipush.manager.connection.RemoteConnectionSnapshotSource
import io.github.magisk317.mipush.manager.events.EventListRequest
import io.github.magisk317.mipush.manager.events.EventReadResult
import io.github.magisk317.mipush.manager.events.RemoteEventListSource
import io.github.magisk317.mipush.manager.logs.LogExportReadResult
import io.github.magisk317.mipush.manager.logs.RemoteLogExportSource
import io.github.magisk317.mipush.manager.notification.NotificationChannelReadResult
import io.github.magisk317.mipush.manager.notification.RemoteNotificationChannelSource
import io.github.magisk317.mipush.utils.LocalConfigSummary
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Binder-backed gateways used when the manager UI runs in the standalone `:mipush` process.
 * Supported reads/writes go through [ManagerRuntimeClient]; unsupported capabilities stay local
 * no-ops so individual screens degrade without blocking the rest of the host.
 */
class RemoteManagerApplicationGateway(
    private val client: ManagerRuntimeClient,
) : ManagerApplicationGateway {
    private val listSource = RemoteApplicationListSource(client)
    private val detailSource = RemoteApplicationDetailSource(client)

    override fun loadApplications(
        context: Context,
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean,
    ): ManagerApplications = runBlocking {
        when (
            val result = listSource.load(
                ApplicationListRequest(
                    query = query,
                    filterMode = filterMode,
                    includeSystemApps = includeSystemApps,
                ),
            )
        ) {
            is ApplicationReadResult.Available -> result.value.applications
            is ApplicationReadResult.Unavailable -> ManagerApplications()
        }
    }

    override fun getApplication(
        context: Context,
        packageName: String,
        ignoreNotRegistered: Boolean,
    ): ManagerApplication? = runBlocking {
        when (val result = detailSource.load(packageName, ignoreNotRegistered)) {
            is ApplicationReadResult.Available -> result.value
            is ApplicationReadResult.Unavailable -> null
        }
    }

    override fun updateApplication(application: ManagerApplication) {
        RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_UPDATE_APPLICATION,
            packageName = application.packageName,
            argument = listOf(
                application.type,
                application.blocked,
                application.islandEnabled,
                application.islandFocusNotification,
                application.notificationOnRegister,
            ).joinToString(","),
        )
    }

    override fun getDiagnostics(
        packageName: String,
        registeredType: Int,
    ): ManagerApplicationDiagnostics = runBlocking {
        when (val result = detailSource.loadDiagnostics(packageName, registeredType)) {
            is ApplicationReadResult.Available -> result.value
            is ApplicationReadResult.Unavailable -> ManagerApplicationDiagnostics(
                hasLocalRegistration = false,
                regSecCount = 0,
                latestRegistrationEventResult = null,
                registeredType = registeredType,
                inferenceReason = "runtime_unavailable",
            )
        }
    }

    override suspend fun launchTargetAppAndForceRegister(
        context: Context,
        packageName: String,
        registeredType: Int,
    ): String {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return "launch_unavailable"
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return if (runCatching { context.startActivity(launchIntent) }.isSuccess) {
            "launched_without_force_register"
        } else {
            "launch_failed"
        }
    }
}

class RemoteManagerEventGateway(
    private val context: Context,
    private val client: ManagerRuntimeClient,
) : ManagerEventGateway {
    private val eventSource = RemoteEventListSource(client)

    override fun getEventsById(
        lastId: Long?,
        size: Int,
        packageName: String,
        query: String,
    ): List<ManagerEvent> = runBlocking {
        when (
            val result = eventSource.load(
                EventListRequest(
                    lastId = lastId,
                    pageSize = size,
                    packageName = packageName,
                    query = query,
                ),
            )
        ) {
            is EventReadResult.Available -> result.value
            is EventReadResult.Unavailable -> emptyList()
        }
    }

    override fun startManagePermissions(packageName: String, ignoreNotRegistered: Boolean) {
        // Package settings remain a local manager-side Intent.
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    override suspend fun startConfigPreview(packageName: String) {
        // Config editor remains manager-local; open is a no-op when remote-only.
    }

    override fun copyToClipboard(content: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("mipush", content))
    }

    override suspend fun mockMessage(event: ManagerEvent): MockReplayOutcome {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_MOCK_MESSAGE,
            packageName = event.packageName,
            eventId = event.id,
            intArgument = event.type,
            longArgument = event.receiveDateMs,
        ) ?: return MockReplayOutcome.Failed
        return when (result.details) {
            ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_POSTED -> MockReplayOutcome.Posted
            ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_DISPATCHED -> MockReplayOutcome.Dispatched
            ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_BLOCKED -> MockReplayOutcome.BlockedByPermission
            ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_FAILED -> MockReplayOutcome.Failed
            else -> if (RemoteWriteSupport.isSuccess(result)) {
                MockReplayOutcome.Dispatched
            } else {
                MockReplayOutcome.Failed
            }
        }
    }

    override fun getJson(event: ManagerEvent): String? = null

    override fun getContent(event: ManagerEvent): String = event.content

    override suspend fun deleteEvent(event: ManagerEvent): Boolean =
        RemoteWriteSupport.isSuccess(
            RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_DELETE_EVENT,
                packageName = event.packageName,
                eventId = event.id,
            ),
        )

    override suspend fun restoreEvent(event: ManagerEvent): ManagerEvent? {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_RESTORE_EVENT,
            packageName = event.packageName,
            eventId = event.id,
            intArgument = event.type,
            longArgument = event.receiveDateMs,
            argument = event.content,
        )
        return if (RemoteWriteSupport.isSuccess(result)) event else null
    }

    override suspend fun countEventsByDay(): List<ManagerDayCount> = emptyList()

    override suspend fun clearHistoryBefore(cutoffMillis: Long): Int {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
            longArgument = cutoffMillis,
        )
        return if (RemoteWriteSupport.isSuccess(result)) {
            result?.resultLong?.toInt() ?: 0
        } else {
            0
        }
    }

    override suspend fun clearHistoryInRange(startMillis: Long, endMillis: Long): Int {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
            longArgument = startMillis,
            argument = endMillis.toString(),
        )
        return if (RemoteWriteSupport.isSuccess(result)) {
            result?.resultLong?.toInt() ?: 0
        } else {
            0
        }
    }
}

class RemoteManagerNotificationGateway(
    private val client: ManagerRuntimeClient,
) : ManagerNotificationGateway {
    private val channelSource = RemoteNotificationChannelSource(client)

    @Volatile
    private var lastIsHooked: Boolean = false

    override val isHooked: Boolean
        get() = lastIsHooked

    override fun getNotificationChannels(packageName: String): List<NotificationChannel> = runBlocking {
        when (val result = channelSource.load(packageName)) {
            is NotificationChannelReadResult.Available -> {
                lastIsHooked = result.value.isHooked
                result.value.channels.map { summary ->
                    NotificationChannel(summary.id, summary.name, summary.importance).apply {
                        description = summary.description
                        if (!summary.groupId.isNullOrBlank()) {
                            group = summary.groupId
                        }
                    }
                }
            }
            is NotificationChannelReadResult.Unavailable -> emptyList()
        }
    }

    override fun getNotificationChannelGroups(packageName: String): List<NotificationChannelGroup> =
        runBlocking {
            when (val result = channelSource.load(packageName)) {
                is NotificationChannelReadResult.Available -> {
                    lastIsHooked = result.value.isHooked
                    result.value.groups.map { summary ->
                        NotificationChannelGroup(summary.id, summary.name)
                    }
                }
                is NotificationChannelReadResult.Unavailable -> emptyList()
            }
        }

    override fun deleteNotificationChannel(packageName: String, channelId: String) {
        // Channel deletion is not in the phase-4 write command set; leave local UI no-op.
    }

    override fun isNotificationChannelEnabled(channel: NotificationChannel): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel.importance != NotificationManagerImportanceNone
        } else {
            true
        }

    private companion object {
        const val NotificationManagerImportanceNone = 0
    }
}

class RemoteManagerLogGateway(
    private val client: ManagerRuntimeClient,
) : ManagerLogGateway {
    private val exportSource = RemoteLogExportSource(client)

    override fun setRetentionDays(days: Int) {
        RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_SET_RUNTIME_LOG_RETENTION,
            intArgument = days.coerceAtLeast(1),
        )
    }

    override fun buildLogBundle(context: Context): ManagerLogExportResult = runBlocking {
        when (val result = exportSource.export()) {
            is LogExportReadResult.Available -> {
                val dto = result.value
                val descriptor = dto.parcelFileDescriptor
                if (!dto.success || descriptor == null) {
                    return@runBlocking ManagerLogExportResult(file = null, details = dto.details)
                }
                val outFile = File(context.cacheDir, "runtime-log-${UUID.randomUUID()}.zip")
                try {
                    FileOutputStream(outFile).use { output ->
                        ParcelFileDescriptorAutoClose(descriptor).use { input ->
                            input.copyTo(output)
                        }
                    }
                    ManagerLogExportResult(file = outFile, details = dto.details)
                } catch (_: Exception) {
                    runCatching { descriptor.close() }
                    ManagerLogExportResult(file = null, details = "log_export_copy_failed")
                }
            }
            is LogExportReadResult.Unavailable ->
                ManagerLogExportResult(file = null, details = "runtime_log_export_unavailable")
        }
    }

    override fun buildShareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override fun clearLogFolders(context: Context): ManagerLogClearResult =
        ManagerLogClearResult(success = false, details = "clear_logs_unsupported_on_remote_host")
}

class RemoteManagerConfigGateway(
    private val preferenceRepository: PreferenceRepository,
) : ManagerConfigGateway {
    override suspend fun getXmppServer(): String? = preferenceRepository.xmppServer.first()

    override suspend fun getConfigurationDirectory(): Uri? =
        preferenceRepository.configDirectory.first()?.let(Uri::parse)

    override suspend fun setConfigurationDirectory(uri: Uri): Boolean {
        preferenceRepository.setConfigDirectory(uri.toString())
        return true
    }

    override fun loadConfigurations(context: Context) {
        // Active configuration remains runtime-owned; upload happens through Binder PFD later.
    }
}

class RemoteManagerConfigSyncGateway : ManagerConfigSyncGateway {
    override suspend fun loadLocalSnapshot(treeUri: Uri?): ManagerConfigListSnapshot =
        ManagerConfigListSnapshot(items = emptyList())

    override suspend fun loadRemoteSnapshot(treeUri: Uri?): ManagerConfigListSnapshot =
        ManagerConfigListSnapshot(
            items = emptyList(),
            remoteError = "remote_config_content_manager_local",
        )

    override suspend fun readLocalEditorSnapshot(
        treeUri: Uri?,
        path: String,
    ): ManagerConfigEditorSnapshot = ManagerConfigEditorSnapshot(path = path)

    override suspend fun readRemoteEditorSnapshot(
        treeUri: Uri?,
        path: String,
    ): ManagerConfigEditorSnapshot = ManagerConfigEditorSnapshot(
        path = path,
        remoteError = "remote_config_content_manager_local",
    )

    override suspend fun pullAll(
        treeUri: Uri,
        onProgress: ((current: Int, total: Int, path: String) -> Unit)?,
    ): Int = 0

    override suspend fun importDocuments(treeUri: Uri, uris: List<Uri>, isIcon: Boolean): Int = 0

    override suspend fun saveLocal(treeUri: Uri, path: String, content: String): LocalConfigSummary =
        LocalConfigSummary(
            path = path,
            name = path.substringAfterLast('/'),
            sha = "",
            size = content.length.toLong(),
            lastModified = System.currentTimeMillis(),
            isValid = true,
        )

    override suspend fun resetToRemote(treeUri: Uri, path: String): LocalConfigSummary =
        LocalConfigSummary(
            path = path,
            name = path.substringAfterLast('/'),
            sha = "",
            size = 0L,
            lastModified = 0L,
            isValid = false,
            validationError = "reset_to_remote_unsupported",
        )

    override suspend fun openForPackage(packageName: String) = Unit
}

class RemoteManagerRuntimeActions(
    private val client: ManagerRuntimeClient,
) : ManagerRuntimeActions {
    private val connectionSource = RemoteConnectionSnapshotSource(client)

    override suspend fun clearHistory() {
        RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
        )
    }

    override fun startMiPushServiceAsForegroundService(context: Context) {
        RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_START_FOREGROUND,
        )
    }

    override fun resetTopActivityCache() = Unit

    override fun sendXmppReconnectRequest(context: Context) {
        RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_XMPP_RECONNECT,
        )
    }

    override fun setXmppServer(context: Context, newHost: String) {
        RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_SET_XMPP_SERVER,
            argument = newHost,
        )
    }

    override fun getXmppServerHint(): String = runBlocking {
        when (val result = connectionSource.load()) {
            is io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSourceResult.Available -> {
                val host = result.snapshot.serverHost.orEmpty()
                val ip = result.snapshot.serverIp.orEmpty()
                when {
                    host.isNotBlank() && ip.isNotBlank() -> "$host ($ip)"
                    host.isNotBlank() -> host
                    ip.isNotBlank() -> ip
                    else -> ""
                }
            }
            is io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSourceResult.Unavailable -> ""
        }
    }

    override fun getRuntimeEnvironmentSnapshot(context: Context): ManagerRuntimeEnvironmentSnapshot =
        runBlocking {
            val host = getXmppServerHint()
            ManagerRuntimeEnvironmentSnapshot(
                isMiui = 0,
                imei = null,
                macAddress = null,
                xmppServerHost = host,
            )
        }

    override fun getConnectionSnapshot(): ManagerConnectionSnapshot = runBlocking {
        when (val result = connectionSource.load()) {
            is ConnectionSnapshotSourceResult.Available -> result.snapshot
            is ConnectionSnapshotSourceResult.Unavailable -> emptyConnectionSnapshot()
        }
    }

    override fun observeNotificationEvent(packageName: String, action: String, source: String) = Unit

    override fun setRuntimeLogRetentionDays(days: Int) {
        RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_SET_RUNTIME_LOG_RETENTION,
            intArgument = days.coerceAtLeast(1),
        )
    }

    override fun applyEventRetentionDays(days: Int) {
        RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_APPLY_EVENT_RETENTION,
            intArgument = days.coerceAtLeast(1),
        )
    }

    private fun emptyConnectionSnapshot() = ManagerConnectionSnapshot(
        connectionState = "unavailable",
        connectedAtMs = 0L,
        lastDisconnectedAtMs = 0L,
        connectionSessionCount = 0L,
        serverHost = null,
        serverIp = null,
        keepAliveIntervalMs = 0,
        pingIntervalMs = 0,
        downstreamMessageCount = 0L,
        deliveredToAppCount = 0L,
        duplicateMessageCount = 0L,
        ackMessageCount = 0L,
        registeredPackageCount = 0,
        trackedChannelCount = 0,
        boundChannelCount = 0,
    )
}

class RemoteManagerPermissionGateway(
    private val client: ManagerRuntimeClient,
) : ManagerPermissionGateway {
    @Volatile
    private var rootCached: Boolean? = null

    override fun hasCachedRootAccess(): Boolean = rootCached == true

    override fun refreshRootAccessIfGranted(): Boolean = queryRoot(requestShell = false)

    override fun requestRootAccess(): Boolean = queryRoot(requestShell = true)

    override fun repairXSpaceUserSupport(): ManagerXSpaceRepairResult =
        ManagerXSpaceRepairResult(stage = ManagerXSpaceRepairStage.ROOT_MISSING)

    override fun setDualAppEnabled(enabled: Boolean): ManagerXSpaceRepairResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_SET_DUAL_APP,
            booleanArgument = enabled,
        ) ?: return ManagerXSpaceRepairResult(
            stage = ManagerXSpaceRepairStage.PARTIAL_FAILED,
            details = "runtime_write_unavailable",
        )
        val stage = when (result.details) {
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_COMPLETED -> ManagerXSpaceRepairStage.COMPLETED
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_ROOT_MISSING -> ManagerXSpaceRepairStage.ROOT_MISSING
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_XSPACE_MISSING -> ManagerXSpaceRepairStage.XSPACE_USER_NOT_FOUND
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_PARTIAL_FAILED -> ManagerXSpaceRepairStage.PARTIAL_FAILED
            else -> if (RemoteWriteSupport.isSuccess(result)) {
                ManagerXSpaceRepairStage.COMPLETED
            } else {
                ManagerXSpaceRepairStage.PARTIAL_FAILED
            }
        }
        // Dual-app enable already grants silent perms on runtime; re-assert from manager as well.
        if (stage == ManagerXSpaceRepairStage.COMPLETED && enabled) {
            grantSilentPermissions(userId = -1, packageName = "", op = "all")
            // New dual-space clone starts at manifest defaults (Default alias); push current icon.
            runCatching {
                val iconId = org.koin.core.context.GlobalContext.get()
                    .get<io.github.magisk317.mipush.data.PreferenceRepository>()
                    .let { repo ->
                        kotlinx.coroutines.runBlocking {
                            repo.selectedLauncherIcon.first()
                        }
                    }
                RemoteWriteSupport.execute(
                    client = client,
                    operation = ManagerProtocol.WRITE_OP_SYNC_LAUNCHER_ICON,
                    argument = iconId,
                    uniqueRequestId = true,
                )
            }
        }
        return ManagerXSpaceRepairResult(stage = stage, details = result.details)
    }

    override fun isDualAppInstalled(): Boolean {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_QUERY_DUAL_APP,
        ) ?: return false
        return result.details == ManagerProtocol.WRITE_DETAIL_DUAL_APP_INSTALLED || result.resultLong == 1L
    }

    override fun launchAppOps(context: Context, permission: String, tips: CharSequence): Boolean {
        // Grant the requested appop for both packages, primary + dual-space.
        return grantSilentPermissions(userId = -1, packageName = "", op = permission)
    }

    override fun isUsageStatsAllowedByRoot(packageName: String): Boolean {
        // Best-effort: if root is available assume grant path works; UI re-checks AppOps.
        return refreshRootAccessIfGranted()
    }

    override fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        // Full silent suite includes deviceidle whitelist for both packages.
        return grantSilentPermissions(userId = 0, packageName = "", op = "all")
    }

    override fun grantNotificationPermission(context: Context): Boolean {
        return grantSilentPermissions(userId = -1, packageName = "", op = "all")
    }

    private fun queryRoot(requestShell: Boolean): Boolean {
        // requestShell currently maps to the same runtime ensureRootAccess path.
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_QUERY_ROOT,
            booleanArgument = requestShell,
            uniqueRequestId = true,
        ) ?: run {
            rootCached = false
            return false
        }
        val available = result.resultLong == 1L ||
            result.details == ManagerProtocol.WRITE_DETAIL_ROOT_AVAILABLE ||
            RemoteWriteSupport.isSuccess(result) && result.details != ManagerProtocol.WRITE_DETAIL_ROOT_MISSING
        rootCached = available
        return available
    }

    private fun grantSilentPermissions(userId: Int, packageName: String, op: String): Boolean {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_GRANT_SILENT_PERMISSIONS,
            packageName = packageName,
            intArgument = userId,
            argument = op,
            uniqueRequestId = true,
        ) ?: return false
        return RemoteWriteSupport.isSuccess(result) ||
            result.details == ManagerProtocol.WRITE_DETAIL_GRANT_SILENT_OK
    }
}

class RemoteZygiskConfigGateway : ZygiskConfigGateway {
    override fun isZygiskModuleEnabled(): Boolean = false
    override fun getZygiskConfigPath(): String = ""
    override fun getZygiskConfig(): ZygiskConfig = ZygiskConfig()
    override fun saveZygiskConfig(config: ZygiskConfig): Boolean = false
    override fun forceStopApp(packageName: String) = Unit
}

private class ParcelFileDescriptorAutoClose(
    private val descriptor: android.os.ParcelFileDescriptor,
) : java.io.Closeable {
    private val input = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)

    fun copyTo(output: FileOutputStream) {
        input.copyTo(output)
    }

    override fun close() {
        input.close()
    }
}
