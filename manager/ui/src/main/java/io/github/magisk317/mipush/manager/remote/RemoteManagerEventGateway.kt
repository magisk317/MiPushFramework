package io.github.magisk317.mipush.manager.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import io.github.magisk317.mipush.core.zygisk.ZygiskConfig
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.manager.application.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.manager.application.ManagerApplicationGateway
import io.github.magisk317.mipush.manager.application.ManagerApplications
import io.github.magisk317.mipush.manager.application.ManagerDualAppInstallationResult
import io.github.magisk317.mipush.manager.application.ManagerConfigEditorSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigGateway
import io.github.magisk317.mipush.manager.application.ManagerConfigListSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigSyncGateway
import io.github.magisk317.mipush.manager.application.ManagerConnectionSnapshot
import io.github.magisk317.mipush.manager.application.ManagerDayCount
import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.application.ManagerForceRegisterResult
import io.github.magisk317.mipush.manager.application.EventDebugJson
import io.github.magisk317.mipush.manager.application.ManagerEventGateway
import io.github.magisk317.mipush.manager.application.ManagerLogClearResult
import io.github.magisk317.mipush.manager.application.ManagerLogExportResult
import io.github.magisk317.mipush.manager.application.ManagerLogGateway
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.application.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRootAccessState
import io.github.magisk317.mipush.manager.application.ManagerRootSubjectStatus
import io.github.magisk317.mipush.manager.application.ManagerRootTarget
import io.github.magisk317.mipush.manager.application.ManagerRuntimeActions
import io.github.magisk317.mipush.manager.application.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairResult
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.manager.application.ZygiskConfigGateway
import io.github.magisk317.mipush.manager.application.ZygiskConfigReadResult
import io.github.magisk317.mipush.manager.application.ZygiskModuleReadResult
import io.github.magisk317.mipush.manager.application.ZygiskPackageScanResult
import io.github.magisk317.mipush.manager.application.MockReplayOutcome
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
import io.github.magisk317.mipush.manager.logs.ManagerLogBundleWriter
import io.github.magisk317.mipush.manager.logs.ManagerLogBundleWriteResult
import io.github.magisk317.mipush.manager.logs.RemoteLogExportSource
import io.github.magisk317.mipush.core.configuration.LocalConfigSummary
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.util.zip.Deflater
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import io.github.magisk317.mipush.manager.logging.ManagerRuntimeFileLog
import io.github.magisk317.mipush.manager.root.ManagerRootAccess
import java.util.UUID
import kotlinx.coroutines.flow.first
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.xposed.logging.MagiskOtel

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
