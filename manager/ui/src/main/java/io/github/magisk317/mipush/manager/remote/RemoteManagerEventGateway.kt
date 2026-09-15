package io.github.magisk317.mipush.manager.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.mipush.manager.application.ManagerConfigSyncGateway
import io.github.magisk317.mipush.manager.application.ManagerDayCount
import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.application.EventDebugJson
import io.github.magisk317.mipush.manager.application.ManagerEventGateway
import io.github.magisk317.mipush.manager.application.MockReplayOutcome
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.events.EventListRequest
import io.github.magisk317.mipush.manager.events.EventReadResult
import io.github.magisk317.mipush.manager.events.RemoteEventListSource
import io.github.magisk317.mipush.common.utils.logW

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
            ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_FAILED_CHANNEL_DISABLED -> MockReplayOutcome.FailedChannelDisabled
            ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_FAILED_EVENT_NOT_FOUND -> MockReplayOutcome.FailedEventNotFound
            ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_FAILED_PAYLOAD_MISSING -> MockReplayOutcome.FailedPayloadMissing
            ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_FAILED_SERVICE_NOT_READY -> MockReplayOutcome.FailedServiceNotReady
            ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_FAILED_APP_NOT_INSTALLED -> MockReplayOutcome.FailedAppNotInstalled
            ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_FAILED_MISSING_REGSEC -> MockReplayOutcome.FailedMissingRegSec
            ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_FAILED_NO_RECEIVER -> MockReplayOutcome.FailedNoReceiver
            ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_FAILED -> MockReplayOutcome.Failed
            else -> if (RemoteWriteSupport.isSuccess(result)) {
                MockReplayOutcome.Dispatched
            } else {
                MockReplayOutcome.Failed
            }
        }
        val statusOk = outcome == MockReplayOutcome.Posted || outcome == MockReplayOutcome.Dispatched
        emitManager(
            stage = "manager_mock_replay",
            result = when (outcome) {
                MockReplayOutcome.Posted, MockReplayOutcome.Dispatched -> "ok"
                MockReplayOutcome.BlockedByPermission -> "skip"
                else -> "error"
            },
            reason = when (outcome) {
                MockReplayOutcome.Posted -> "posted"
                MockReplayOutcome.Dispatched -> "dispatched"
                MockReplayOutcome.BlockedByPermission -> "blocked_by_permission"
                MockReplayOutcome.FailedChannelDisabled -> "channel_disabled"
                MockReplayOutcome.FailedEventNotFound -> "event_not_found"
                MockReplayOutcome.FailedPayloadMissing -> "payload_missing"
                MockReplayOutcome.FailedServiceNotReady -> "service_not_ready"
                MockReplayOutcome.FailedAppNotInstalled -> "app_not_installed"
                MockReplayOutcome.FailedMissingRegSec -> "missing_regsec"
                MockReplayOutcome.FailedNoReceiver -> "no_receiver"
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
