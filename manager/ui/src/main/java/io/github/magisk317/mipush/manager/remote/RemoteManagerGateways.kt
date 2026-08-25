package io.github.magisk317.mipush.manager.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.core.content.FileProvider
import io.github.magisk317.mipush.common.fakedevice.ZygiskConfig
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerApplications
import io.github.magisk317.mipush.common.manager.ManagerDualAppInstallationResult
import io.github.magisk317.mipush.common.manager.ManagerConfigEditorSnapshot
import io.github.magisk317.mipush.common.manager.ManagerConfigGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigListSnapshot
import io.github.magisk317.mipush.common.manager.ManagerConfigSyncGateway
import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.common.manager.ManagerDayCount
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.manager.ManagerForceRegisterResult
import io.github.magisk317.mipush.common.manager.EventDebugJson
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.common.manager.ManagerLogClearResult
import io.github.magisk317.mipush.common.manager.ManagerLogExportResult
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.common.manager.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.common.manager.ManagerRootAccessState
import io.github.magisk317.mipush.common.manager.ManagerRootSubjectStatus
import io.github.magisk317.mipush.common.manager.ManagerRootTarget
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
import io.github.magisk317.mipush.common.manager.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairResult
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.common.manager.ZygiskConfigGateway
import io.github.magisk317.mipush.common.manager.ZygiskConfigReadResult
import io.github.magisk317.mipush.common.manager.ZygiskModuleReadResult
import io.github.magisk317.mipush.common.manager.ZygiskPackageScanResult
import io.github.magisk317.mipush.common.notification.MockReplayOutcome
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.application.ApplicationListRequest
import io.github.magisk317.mipush.manager.application.ApplicationReadResult
import io.github.magisk317.mipush.manager.application.RemoteApplicationDetailSource
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.preferences.RuntimePreferenceGateway
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSourceResult
import io.github.magisk317.mipush.manager.connection.RemoteConnectionSnapshotSource
import io.github.magisk317.mipush.manager.events.EventListRequest
import io.github.magisk317.mipush.manager.events.EventReadResult
import io.github.magisk317.mipush.manager.events.RemoteEventListSource
import io.github.magisk317.mipush.manager.logs.LogExportReadResult
import io.github.magisk317.mipush.manager.logs.RemoteLogExportSource
import io.github.magisk317.mipush.utils.LocalConfigSummary
import java.io.File
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.util.zip.Deflater
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import io.github.magisk317.mipush.manager.logging.ManagerRuntimeFileLog
import io.github.magisk317.mipush.manager.root.ManagerRootAccess
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.flow.first
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Binder-backed gateways used when the manager UI runs in the standalone `:mipush` process.
 * Supported reads/writes go through [ManagerRuntimeClient]; unsupported capabilities stay local
 * no-ops so individual screens degrade without blocking the rest of the host.
 */
private fun emitManager(
    stage: String,
    result: String,
    reason: String,
    statusOk: Boolean = true,
    targetPackage: String? = null,
) {
    val attrs = mutableMapOf(
        "result" to result,
        "duration_ms" to "0",
        "process" to "manager",
        "stage" to stage,
        "reason" to reason,
    )
    if (!targetPackage.isNullOrBlank()) {
        attrs["target_package"] = targetPackage
    }
    MagiskOtel.event(
        name = "app.monitor",
        attributes = attrs,
        statusOk = statusOk,
    )
}


class RemoteManagerApplicationGateway(
    private val client: ManagerRuntimeClient,
) : ManagerApplicationGateway {
    private val listSource = RemoteApplicationListSource(client)
    private val detailSource = RemoteApplicationDetailSource(client)

    override suspend fun loadApplications(
        context: Context,
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean,
    ): ManagerApplications {
        return when (
            val result = listSource.load(
                ApplicationListRequest(
                    query = query,
                    filterMode = filterMode,
                    includeSystemApps = includeSystemApps,
                ),
            )
        ) {
            is ApplicationReadResult.Available -> result.value.applications
            is ApplicationReadResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("loadApplications", result.status)
                throw RuntimeReadUnavailableException(
                    status = result.status.name,
                    operation = "loadApplications",
                )
            }
        }
    }

    override suspend fun getApplication(
        context: Context,
        packageName: String,
        ignoreNotRegistered: Boolean,
    ): ManagerApplication? {
        return when (val result = detailSource.load(packageName, ignoreNotRegistered)) {
            is ApplicationReadResult.Available -> result.value
            is ApplicationReadResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("getApplication", result.status)
                throw RuntimeReadUnavailableException(
                    status = result.status.name,
                    operation = "getApplication",
                )
            }
        }
    }

    override suspend fun updateApplication(application: ManagerApplication) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
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
            ),
            operation = ManagerProtocol.WRITE_OP_UPDATE_APPLICATION,
        )
    }

    override suspend fun getDiagnostics(
        packageName: String,
        registeredType: Int,
    ): ManagerApplicationDiagnostics {
        return when (val result = detailSource.loadDiagnostics(packageName, registeredType)) {
            is ApplicationReadResult.Available -> result.value
            is ApplicationReadResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("getDiagnostics", result.status)
                throw RuntimeReadUnavailableException(
                    status = result.status.name,
                    operation = "getDiagnostics",
                )
            }
        }
    }

    override suspend fun launchTargetAppAndForceRegister(
        context: Context,
        packageName: String,
        registeredType: Int,
    ): ManagerForceRegisterResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_LAUNCH_TARGET_FORCE_REGISTER,
            packageName = packageName,
            intArgument = registeredType,
        )
        if (result == null) {
            throw IllegalStateException("${ManagerProtocol.WRITE_OP_LAUNCH_TARGET_FORCE_REGISTER}:null_response")
        }
        return ManagerForceRegisterResult(
            succeeded = RemoteWriteSupport.isSuccess(result),
            message = result.details.ifBlank { "force_register_completed" },
        )
    }
}

class RemoteManagerEventGateway(
    private val context: Context,
    private val client: ManagerRuntimeClient,
    private val configSyncGateway: ManagerConfigSyncGateway,
) : ManagerEventGateway {
    private val eventSource = RemoteEventListSource(client)

    override suspend fun getEventsById(
        lastId: Long?,
        size: Int,
        packageName: String,
        query: String,
    ): List<ManagerEvent> {
        return when (
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
            is EventReadResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("getEventsById", result.status)
                throw RuntimeReadUnavailableException(
                    status = result.status.name,
                    operation = "getEventsById",
                )
            }
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
        configSyncGateway.openForPackage(packageName)
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
        )
        if (result == null) {
            emitManager(
                stage = "manager_mock_replay",
                result = "error",
                reason = "null_response",
                statusOk = false,
                targetPackage = event.packageName,
            )
            return MockReplayOutcome.Failed
        }
        val outcome = when (result.details) {
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
        val statusOk = outcome != MockReplayOutcome.Failed && outcome != MockReplayOutcome.BlockedByPermission
        emitManager(
            stage = "manager_mock_replay",
            result = when (outcome) {
                MockReplayOutcome.Failed -> "error"
                MockReplayOutcome.BlockedByPermission -> "skip"
                else -> "ok"
            },
            reason = when (outcome) {
                MockReplayOutcome.Posted -> "posted"
                MockReplayOutcome.Dispatched -> "dispatched"
                MockReplayOutcome.BlockedByPermission -> "blocked_by_permission"
                MockReplayOutcome.Failed -> "failed"
            },
            statusOk = statusOk,
            targetPackage = event.packageName,
        )
        return outcome
    }

    override suspend fun getJson(event: ManagerEvent): String? {
        val remote = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_GET_EVENT_JSON,
            packageName = event.packageName,
            userId = event.userId,
            eventId = event.id,
            intArgument = event.type,
            longArgument = event.receiveDateMs,
            argument = event.content,
        )
        remote?.let { result ->
            if (RemoteWriteSupport.isSuccess(result) && result.details.isNotBlank()) {
                return result.details
            }
        }
        return runCatching { EventDebugJson.format(event) }.getOrNull()
    }

    override suspend fun getContent(event: ManagerEvent): String? {
        val remote = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_GET_EVENT_CONTENT,
            packageName = event.packageName,
            userId = event.userId,
            eventId = event.id,
            intArgument = event.type,
            longArgument = event.receiveDateMs,
            argument = event.content,
        )
        return remote?.details?.takeIf {
            RemoteWriteSupport.isSuccess(remote) && it.isNotBlank()
        }
    }

    override suspend fun deleteEvent(event: ManagerEvent): Boolean {
        val ok = RemoteWriteSupport.isSuccess(
            RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_DELETE_EVENT,
                packageName = event.packageName,
                userId = event.userId,
                eventId = event.id,
            ),
        )
        emitManager(
            stage = "manager_event_delete",
            result = if (ok) "ok" else "error",
            reason = if (ok) "deleted" else "delete_failed",
            statusOk = ok,
            targetPackage = event.packageName,
        )
        return ok
    }

    override suspend fun restoreEvent(event: ManagerEvent): ManagerEvent? {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_RESTORE_EVENT,
            packageName = event.packageName,
            userId = event.userId,
            eventId = event.id,
            intArgument = event.type,
            longArgument = event.receiveDateMs,
            argument = event.content,
        )
        return if (RemoteWriteSupport.isSuccess(result)) event else null
    }

    override suspend fun countEventsByDay(): List<ManagerDayCount> {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_COUNT_EVENTS_BY_DAY,
        ) ?: run {
            logW("countEventsByDay unavailable status=runtime_unavailable")
            throw RuntimeReadUnavailableException(
                status = "runtime_unavailable",
                operation = "countEventsByDay",
            )
        }
        if (!RemoteWriteSupport.isSuccess(result)) {
            logW("countEventsByDay unavailable status=${result.status}")
            throw RuntimeReadUnavailableException(
                status = result.status,
                operation = "countEventsByDay",
            )
        }
        return result.details.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && ':' in it }
            .mapNotNull { line ->
                val day = line.substringBeforeLast(':').trim()
                val count = line.substringAfterLast(':').trim().toIntOrNull() ?: return@mapNotNull null
                if (day.isEmpty()) return@mapNotNull null
                ManagerDayCount(day = day, count = count)
            }
            .toList()
    }

    override suspend fun clearHistoryBefore(cutoffMillis: Long): Int {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
            longArgument = cutoffMillis,
        )
        return RemoteWriteSupport.requireSuccess(
            result = result,
            operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
        ).resultLong.toInt()
    }

    override suspend fun clearHistoryInRange(startMillis: Long, endMillis: Long): Int {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
            longArgument = startMillis,
            argument = endMillis.toString(),
        )
        return RemoteWriteSupport.requireSuccess(
            result = result,
            operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
        ).resultLong.toInt()
    }
}

class RemoteManagerLogGateway(
    private val client: ManagerRuntimeClient,
    private val appContext: Context? = null,
) : ManagerLogGateway {
    private val exportSource = RemoteLogExportSource(client)

    override suspend fun setRetentionDays(days: Int) {
        val keepDays = days.coerceAtLeast(1)
        ManagerRuntimeFileLog.setRetentionDays(keepDays)
        pruneLocalManagerLogArtifacts(keepDays)
        // Runtime retention is written once by RemoteManagerRuntimeActions before
        // this local manager-log maintenance step.
    }

    /**
     * Best-effort local cleanup on the manager package: share temp zips and any residual
     * diagnostic dirs. Runtime-owned files/xmsf_logs still prune on the xmsf side.
     */
    private fun pruneLocalManagerLogArtifacts(keepDays: Int) {
        val context = appContext ?: return
        val cutoff = java.util.Calendar.getInstance(java.util.Locale.US).apply {
            timeInMillis = System.currentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -(keepDays - 1))
        }.timeInMillis
        val targets = buildList {
            add(context.cacheDir)
            add(File(context.filesDir, "log"))
            add(File(context.filesDir, "crash"))
            add(File(context.filesDir, "private_export"))
            add(File(context.cacheDir, "log"))
            add(File(context.cacheDir, "logs"))
        }
        targets.forEach { root ->
            if (!root.exists()) return@forEach
            root.walkBottomUp().forEach { file ->
                if (file == root && file.isDirectory) return@forEach
                val mtime = file.lastModified()
                if (mtime > 0L && mtime < cutoff) {
                    runCatching {
                        if (file.isDirectory) {
                            if (file.listFiles().isNullOrEmpty()) file.delete()
                        } else {
                            file.delete()
                        }
                    }
                }
            }
        }
    }

    override suspend fun buildLogBundle(context: Context): ManagerLogExportResult = run {
        val export = when (val result = exportSource.export()) {
            is LogExportReadResult.Available -> {
                val dto = result.value
                val descriptor = dto.parcelFileDescriptor
                if (!dto.success || descriptor == null) {
                    return@run ManagerLogExportResult(file = null, details = dto.details).also {
                        emitManager(
                            stage = "manager_log_export",
                            result = "error",
                            reason = "descriptor_missing",
                            statusOk = false,
                        )
                    }
                }
                // Restore pre-split share name: mipush_logs_<timestamp>.zip (not UUID).
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val remoteTmp = File(context.cacheDir, ".runtime-export-$timestamp.zip")
                val outFile = File(context.cacheDir, "mipush_logs_$timestamp.zip")
                try {
                    FileOutputStream(remoteTmp).use { output ->
                        ParcelFileDescriptorAutoClose(descriptor).use { input ->
                            input.copyTo(output)
                        }
                    }
                    val mergedDetail = mergeRuntimeZipWithManagerLogs(
                        context = context,
                        remoteZip = remoteTmp,
                        outZip = outFile,
                    )
                    runCatching { remoteTmp.delete() }
                    val details = listOf(dto.details, mergedDetail)
                        .filter { it.isNotBlank() }
                        .joinToString("; ")
                    ManagerLogExportResult(file = outFile, details = details)
                } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                    runCatching { descriptor.close() }
                    runCatching { remoteTmp.delete() }
                    runCatching { outFile.delete() }
                    ManagerLogExportResult(
                        file = null,
                        details = "log_export_copy_failed:${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
            is LogExportReadResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("buildLogBundle", result.status)
                ManagerLogExportResult(
                    file = null,
                    details = "runtime_log_export_unavailable:${result.status.name.lowercase()}",
                )
            }
        }
        emitManager(
            stage = "manager_log_export",
            result = if (export.file != null) "ok" else "error",
            reason = if (export.file != null) "exported" else "export_failed",
            statusOk = export.file != null,
        )
        export
    }

    /**
     * Copy remote zip entries and inject manager-local FileAntilog jsonl under app/log/.
     * Keeps the historical mipush_logs_* share name and one cohesive bundle.
     */
    private fun mergeRuntimeZipWithManagerLogs(
        context: Context,
        remoteZip: File,
        outZip: File,
    ): String {
        val managerLogs = ManagerRuntimeFileLog.listExportableLogFiles(context)
        if (!remoteZip.exists()) {
            throw IllegalStateException("remote export zip missing")
        }
        val seen = linkedSetOf<String>()
        ZipInputStream(remoteZip.inputStream().buffered()).use { input ->
            ZipOutputStream(outZip.outputStream().buffered()).use { output ->
                output.setLevel(Deflater.BEST_SPEED)
                while (true) {
                    val entry = input.nextEntry ?: break
                    val name = entry.name
                    if (name.isBlank() || name in seen) {
                        input.closeEntry()
                        continue
                    }
                    seen += name
                    output.putNextEntry(ZipEntry(name))
                    input.copyTo(output)
                    output.closeEntry()
                    input.closeEntry()
                }
                managerLogs.forEach { file ->
                    val name = "app/log/${file.name}"
                    if (name in seen) return@forEach
                    seen += name
                    output.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
        }
        return if (managerLogs.isEmpty()) {
            "manager_logs:0"
        } else {
            "manager_logs:${managerLogs.size},bytes=${managerLogs.sumOf { it.length() }}"
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

    override suspend fun clearLogFolders(context: Context): ManagerLogClearResult {
        // Clear manager-local diagnostic dirs best-effort.
        runCatching {
            ManagerRuntimeFileLog.clear(context)
            listOf("log", "crash", "private_export").forEach { name ->
                File(context.filesDir, name).deleteRecursively()
            }
            File(context.cacheDir, "log").deleteRecursively()
            // Legacy UUID share temps + current mipush_logs share temps.
            context.cacheDir.listFiles()
                ?.filter { it.isFile && (it.name.startsWith("runtime-log-") || it.name.startsWith("mipush_logs_") || it.name.startsWith(".runtime-export-")) }
                ?.forEach { runCatching { it.delete() } }
        }
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_CLEAR_LOG_FOLDERS,
        )
        val clearResult = if (RemoteWriteSupport.isSuccess(result)) {
            ManagerLogClearResult(
                success = true,
                details = result?.details.orEmpty().ifBlank { "clear_log_folders_ok" },
            )
        } else {
            ManagerLogClearResult(
                success = false,
                details = result?.details ?: "clear_logs_runtime_unavailable",
            )
        }
        emitManager(
            stage = "manager_log_clear",
            result = if (clearResult.success) "ok" else "error",
            reason = if (clearResult.success) "cleared" else "clear_failed",
            statusOk = clearResult.success,
        )
        return clearResult
    }
}

class RemoteManagerConfigGateway(
    private val preferenceRepository: PreferenceRepository,
    private val configSyncGateway: io.github.magisk317.mipush.manager.configuration.sync.LocalManagerConfigSyncGateway,
    private val runtimePreferenceGateway: RuntimePreferenceGateway,
) : ManagerConfigGateway {
    override suspend fun getXmppServer(): String? = runtimePreferenceGateway.getXmppServer()

    override suspend fun setXmppServer(host: String): Boolean {
        return runtimePreferenceGateway.setXmppServer(host)
    }

    override suspend fun getConfigurationDirectory(): Uri? =
        preferenceRepository.configDirectory.first()?.let(Uri::parse)

    override suspend fun setConfigurationDirectory(uri: Uri): Boolean {
        preferenceRepository.setConfigDirectory(uri.toString())
        return true
    }

    override suspend fun loadConfigurations(context: Context) {
        val tree = preferenceRepository.configDirectory.first()?.let(Uri::parse)
        configSyncGateway.activateAllLocalConfigs(tree)
    }
}


class RemoteManagerRuntimeActions(
    private val client: ManagerRuntimeClient,
) : ManagerRuntimeActions {
    private val connectionSource = RemoteConnectionSnapshotSource(client)

    override suspend fun clearHistory() {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
            ),
            operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
        )
    }

    override suspend fun startMiPushServiceAsForegroundService(context: Context) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_START_FOREGROUND,
            ),
            operation = ManagerProtocol.WRITE_OP_START_FOREGROUND,
        )
    }

    override suspend fun resetTopActivityCache() {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_RESET_TOP_ACTIVITY_CACHE,
            ),
            operation = ManagerProtocol.WRITE_OP_RESET_TOP_ACTIVITY_CACHE,
        )
    }

    override suspend fun sendXmppReconnectRequest(context: Context): Boolean =
        RemoteWriteSupport.isSuccess(
            RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_XMPP_RECONNECT,
            ),
        )

    override suspend fun setXmppServer(context: Context, newHost: String) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_SET_XMPP_SERVER,
                argument = newHost,
            ),
            operation = ManagerProtocol.WRITE_OP_SET_XMPP_SERVER,
        )
    }

    override suspend fun getRuntimeEnvironmentSnapshot(context: Context): ManagerRuntimeEnvironmentSnapshot {
        return when (val result = client.getRuntimeEnvironmentSnapshot()) {
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeResult.Success ->
                ManagerRuntimeEnvironmentSnapshot(
                    isMiui = result.value.isMiui,
                    imei = result.value.imei,
                    macAddress = result.value.macAddress,
                    xmppServerHost = result.value.xmppServerHost,
                )
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeResult.Unsupported -> {
                logW("getRuntimeEnvironmentSnapshot unavailable status=unsupported")
                throw RuntimeReadUnavailableException("unsupported", "getRuntimeEnvironmentSnapshot")
            }
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeResult.Unavailable -> {
                logW("getRuntimeEnvironmentSnapshot unavailable status=${result.availability}")
                throw RuntimeReadUnavailableException(
                    result.availability.toString(),
                    "getRuntimeEnvironmentSnapshot",
                )
            }
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeResult.Failed -> {
                logW("getRuntimeEnvironmentSnapshot unavailable status=${result.reason}")
                throw RuntimeReadUnavailableException(result.reason, "getRuntimeEnvironmentSnapshot")
            }
        }
    }

    override suspend fun getConnectionSnapshot(): ManagerConnectionSnapshot {
        return when (val result = connectionSource.load()) {
            is ConnectionSnapshotSourceResult.Available -> result.snapshot
            is ConnectionSnapshotSourceResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("getConnectionSnapshot", result.status)
                throw RuntimeReadUnavailableException(
                    status = result.status.name,
                    operation = "getConnectionSnapshot",
                )
            }
        }
    }

    override fun observeNotificationEvent(packageName: String, action: String, source: String) = Unit

    override suspend fun setRuntimeLogRetentionDays(days: Int) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_SET_RUNTIME_LOG_RETENTION,
                intArgument = days.coerceAtLeast(1),
            ),
            operation = ManagerProtocol.WRITE_OP_SET_RUNTIME_LOG_RETENTION,
        )
    }

    override suspend fun applyEventRetentionDays(days: Int) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_APPLY_EVENT_RETENTION,
                intArgument = days.coerceAtLeast(1),
            ),
            operation = ManagerProtocol.WRITE_OP_APPLY_EVENT_RETENTION,
        )
    }

}

class RemoteManagerPermissionGateway(
    private val context: Context,
    private val client: ManagerRuntimeClient,
    private val managerRootAccess: ManagerRootAccess,
    private val preferenceRepository: PreferenceRepository,
) : ManagerPermissionGateway {
    @Volatile
    private var runtimeRootState: ManagerRootAccessState = ManagerRootAccessState.UNAVAILABLE

    override suspend fun getRootAccessSnapshot(refresh: Boolean): ManagerRootAccessSnapshot {
        val managerGranted = if (refresh) {
            managerRootAccess.refreshRootAccessIfGranted()
        } else {
            managerRootAccess.cachedGrantState()
        }
        val runtimeState = if (refresh) queryRootState(requestAuthorization = false) else runtimeRootState
        return rootSnapshot(
            managerState = managerGranted.toRootAccessState(),
            runtimeState = runtimeState,
        )
    }

    override suspend fun requestRootAccess(target: ManagerRootTarget): ManagerRootAccessSnapshot {
        val managerState = when (target) {
            ManagerRootTarget.MANAGER -> managerRootAccess.requestRootAccess()
            ManagerRootTarget.RUNTIME -> managerRootAccess.refreshRootAccessIfGranted()
        }.toRootAccessState()
        val runtimeState = queryRootState(
            requestAuthorization = target == ManagerRootTarget.RUNTIME,
        )
        return rootSnapshot(managerState = managerState, runtimeState = runtimeState)
    }

    override suspend fun hasCachedRootAccess(): Boolean = runtimeRootState == ManagerRootAccessState.GRANTED

    override suspend fun refreshRootAccessIfGranted(): Boolean {
        return queryRootState(requestAuthorization = false) == ManagerRootAccessState.GRANTED
    }

    override suspend fun requestRootAccess(): Boolean {
        return queryRootState(requestAuthorization = true) == ManagerRootAccessState.GRANTED
    }

    override suspend fun repairXSpaceUserSupport(): ManagerXSpaceRepairResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_REPAIR_XSPACE,
        ) ?: return ManagerXSpaceRepairResult(
            stage = ManagerXSpaceRepairStage.PARTIAL_FAILED,
            details = "runtime_write_unavailable",
        )
        val stage = when (result.details) {
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_COMPLETED -> ManagerXSpaceRepairStage.COMPLETED
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_ROOT_MISSING -> ManagerXSpaceRepairStage.ROOT_MISSING
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_PRIMARY_USER_REQUIRED ->
                ManagerXSpaceRepairStage.PRIMARY_USER_REQUIRED
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_XSPACE_MISSING -> ManagerXSpaceRepairStage.XSPACE_USER_NOT_FOUND
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_PARTIAL_FAILED -> ManagerXSpaceRepairStage.PARTIAL_FAILED
            else -> if (RemoteWriteSupport.isSuccess(result)) {
                ManagerXSpaceRepairStage.COMPLETED
            } else {
                ManagerXSpaceRepairStage.PARTIAL_FAILED
            }
        }
        return ManagerXSpaceRepairResult(
            stage = stage,
            xmsfInstalled = result.resultLong == 1L,
            details = result.details,
        )
    }

    override suspend fun setDualAppEnabled(enabled: Boolean): ManagerXSpaceRepairResult {
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
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_PRIMARY_USER_REQUIRED ->
                ManagerXSpaceRepairStage.PRIMARY_USER_REQUIRED
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
            try {
                val iconId = preferenceRepository.selectedLauncherIcon.first()
                RemoteWriteSupport.execute(
                    client = client,
                    operation = ManagerProtocol.WRITE_OP_SYNC_LAUNCHER_ICON,
                    argument = iconId,
                )
            } catch (_: RuntimeException) {
                // Dual-app setup is already complete; icon synchronization is best effort.
            }
        }
        return ManagerXSpaceRepairResult(stage = stage, details = result.details)
    }

    override suspend fun getDualAppInstallation(): ManagerDualAppInstallationResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_QUERY_DUAL_APP,
        ) ?: return ManagerDualAppInstallationResult.Unavailable("runtime_unavailable")
        if (!RemoteWriteSupport.isSuccess(result)) {
            return ManagerDualAppInstallationResult.Unavailable(
                result.details.ifBlank { result.status },
            )
        }
        return if (result.details == ManagerProtocol.WRITE_DETAIL_DUAL_APP_INSTALLED || result.resultLong == 1L) {
            ManagerDualAppInstallationResult.Installed
        } else {
            ManagerDualAppInstallationResult.NotInstalled
        }
    }

    override suspend fun launchAppOps(context: Context, permission: String, tips: CharSequence): Boolean {
        // Grant the requested appop for both packages, primary + dual-space.
        return grantSilentPermissions(userId = -1, packageName = "", op = permission)
    }

    override suspend fun isUsageStatsAllowedByRoot(packageName: String): Boolean {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_QUERY_USAGE_STATS,
            packageName = packageName,
        )
        return resolveUsageStatsAllowed(result)
    }

    override suspend fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        return grantSilentPermissions(userId = 0, packageName = "", op = "deviceidle")
    }

    override suspend fun grantNotificationPermission(context: Context): Boolean {
        return grantSilentPermissions(userId = -1, packageName = "", op = "all")
    }

    private suspend fun queryRootState(requestAuthorization: Boolean): ManagerRootAccessState {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_QUERY_ROOT,
            booleanArgument = requestAuthorization,
        )
        return resolveRuntimeRootAccessState(result).also { runtimeRootState = it }
    }

    private fun rootSnapshot(
        managerState: ManagerRootAccessState,
        runtimeState: ManagerRootAccessState,
    ): ManagerRootAccessSnapshot {
        val userId = Process.myUid() / PER_USER_RANGE
        return ManagerRootAccessSnapshot(
            userId = userId,
            manager = ManagerRootSubjectStatus(
                target = ManagerRootTarget.MANAGER,
                packageName = ManagerProtocol.MANAGER_PACKAGE,
                userId = userId,
                uid = Process.myUid(),
                state = managerState,
            ),
            runtime = ManagerRootSubjectStatus(
                target = ManagerRootTarget.RUNTIME,
                packageName = ManagerProtocol.RUNTIME_PACKAGE,
                userId = userId,
                uid = context.packageUidOrNull(ManagerProtocol.RUNTIME_PACKAGE),
                state = runtimeState,
            ),
        )
    }

    private suspend fun grantSilentPermissions(userId: Int, packageName: String, op: String): Boolean {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_GRANT_SILENT_PERMISSIONS,
            packageName = packageName,
            intArgument = userId,
            argument = op,
        ) ?: return false
        return RemoteWriteSupport.isSuccess(result) ||
            result.details == ManagerProtocol.WRITE_DETAIL_GRANT_SILENT_OK
    }
}

internal fun resolveRuntimeRootAccessState(result: ManagerWriteResultDto?): ManagerRootAccessState {
    if (result == null) return ManagerRootAccessState.UNAVAILABLE
    if (result.resultLong == 1L || result.details == ManagerProtocol.WRITE_DETAIL_ROOT_AVAILABLE) {
        return ManagerRootAccessState.GRANTED
    }
    if (RemoteWriteSupport.isSuccess(result) && result.details == ManagerProtocol.WRITE_DETAIL_ROOT_MISSING) {
        return ManagerRootAccessState.NOT_GRANTED
    }
    return ManagerRootAccessState.UNAVAILABLE
}

internal fun resolveUsageStatsAllowed(result: ManagerWriteResultDto?): Boolean =
    RemoteWriteSupport.isSuccess(result) && result?.resultLong == 1L

private fun Boolean?.toRootAccessState(): ManagerRootAccessState = when (this) {
    true -> ManagerRootAccessState.GRANTED
    false -> ManagerRootAccessState.NOT_GRANTED
    null -> ManagerRootAccessState.UNAVAILABLE
}

private fun Context.packageUidOrNull(packageName: String): Int? = runCatching {
    packageManager.getPackageUid(packageName, 0)
}.getOrNull()

private const val PER_USER_RANGE = 100_000

class RemoteZygiskConfigGateway(
    private val client: ManagerRuntimeClient,
) : ZygiskConfigGateway {
    override suspend fun isZygiskModuleEnabled(): ZygiskModuleReadResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_IS_ENABLED,
        ) ?: return ZygiskModuleReadResult.Unavailable("runtime_unavailable")
        if (!RemoteWriteSupport.isSuccess(result)) {
            return ZygiskModuleReadResult.Unavailable(result.details.ifBlank { "zygisk_status_unavailable" })
        }
        return ZygiskModuleReadResult.Available(result.resultLong == 1L)
    }

    override fun getZygiskConfigPath(): String = "/data/adb/mipush_zygisk/app.conf"

    override suspend fun getZygiskConfig(): ZygiskConfigReadResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_GET_CONFIG,
        ) ?: return ZygiskConfigReadResult.Unavailable("runtime_unavailable")
        if (!RemoteWriteSupport.isSuccess(result)) {
            return ZygiskConfigReadResult.Unavailable(result.details.ifBlank { "zygisk_config_unavailable" })
        }
        return ZygiskConfigReadResult.Available(ZygiskConfig.parse(result.details))
    }

    override suspend fun saveZygiskConfig(config: ZygiskConfig): Boolean {
        val content = config.toFileContent()
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_SAVE_CONFIG,
            argument = content,
        ) ?: return false
        return RemoteWriteSupport.isSuccess(result)
    }

    override suspend fun forceStopApp(packageName: String): Boolean {
        return RemoteWriteSupport.isSuccess(RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_FORCE_STOP,
            packageName = packageName,
        ))
    }

    override suspend fun scanZygiskPackages(): ZygiskPackageScanResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_SCAN,
        ) ?: return ZygiskPackageScanResult.Unavailable("runtime_unavailable")
        return if (RemoteWriteSupport.isSuccess(result)) {
            ZygiskPackageScanResult.Available(result.details)
        } else {
            ZygiskPackageScanResult.Unavailable(result.details.ifBlank { "zygisk_scan_unavailable" })
        }
    }
}


private object RemoteRuntimeLog {
    // Startup/bind races are expected after force-stop or dual-APK process churn.
    fun unavailable(operation: String, status: Enum<*>) {
        when (status.name) {
            "BINDING",
            "DISCONNECTED",
            "TEMPORARILY_DISCONNECTED",
            -> logD("$operation unavailable status=$status")
            else -> logW("$operation unavailable status=$status")
        }
    }
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
