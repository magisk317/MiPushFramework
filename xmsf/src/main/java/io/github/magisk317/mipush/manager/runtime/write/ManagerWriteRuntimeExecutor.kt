package io.github.magisk317.mipush.manager.runtime.write

import android.content.Context
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.utils.LogUtils
import kotlinx.coroutines.runBlocking

class ManagerWriteRuntimeExecutor(
    private val context: Context,
    private val applicationGateway: ManagerApplicationGateway,
    private val eventGateway: ManagerEventGateway,
    private val runtimeActions: ManagerRuntimeActions,
    private val idempotencyStore: ManagerWriteIdempotencyStore = ManagerWriteIdempotencyStore(),
) {
    constructor(context: Context) : this(
        context = context,
        applicationGateway = AppDependencies.get(context),
        eventGateway = AppDependencies.get(context),
        runtimeActions = AppDependencies.get(context),
    )

    fun execute(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        when (val begin = idempotencyStore.begin(request.requestId)) {
            is ManagerWriteIdempotencyStore.BeginResult.Duplicate -> return begin.result
            ManagerWriteIdempotencyStore.BeginResult.Execute -> Unit
        }
        return try {
            val result = runCatching { dispatch(request) }.getOrElse {
                failed(request.requestId, "runtime_operation_failed")
            }
            idempotencyStore.complete(result)
            result
        } catch (error: Throwable) {
            idempotencyStore.abort(request.requestId)
            throw error
        }
    }

    private fun dispatch(request: ManagerWriteRequestDto): ManagerWriteResultDto =
        when (request.operation) {
            ManagerProtocol.WRITE_OP_UPDATE_APPLICATION -> updateApplication(request)
            ManagerProtocol.WRITE_OP_DELETE_EVENT -> deleteEvent(request)
            ManagerProtocol.WRITE_OP_RESTORE_EVENT -> restoreEvent(request)
            ManagerProtocol.WRITE_OP_SET_XMPP_SERVER -> setXmppServer(request)
            ManagerProtocol.WRITE_OP_CLEAR_HISTORY -> clearHistory(request)
            ManagerProtocol.WRITE_OP_SET_RUNTIME_LOG_RETENTION -> {
                val days = request.intArgument.coerceAtLeast(1)
                runtimeActions.setRuntimeLogRetentionDays(days)
                LogUtils.setRetentionDays(days)
                success(request.requestId, "runtime_log_retention:$days")
            }
            ManagerProtocol.WRITE_OP_START_FOREGROUND -> {
                runtimeActions.startMiPushServiceAsForegroundService(context)
                success(request.requestId, "foreground_started")
            }
            ManagerProtocol.WRITE_OP_XMPP_RECONNECT -> {
                runtimeActions.sendXmppReconnectRequest(context)
                success(request.requestId, "xmpp_reconnect_requested")
            }
            ManagerProtocol.WRITE_OP_APPLY_EVENT_RETENTION -> {
                val days = request.intArgument.coerceAtLeast(1)
                runtimeActions.applyEventRetentionDays(days)
                success(request.requestId, "event_retention:$days")
            }
            else -> ManagerWriteResultDto(
                requestId = request.requestId,
                status = ManagerProtocol.WRITE_STATUS_UNSUPPORTED,
                details = "unsupported_operation",
            )
        }

    private fun updateApplication(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val packageName = request.packageName
        if (packageName.isBlank()) return failed(request.requestId, "missing_package_name")
        val current = applicationGateway.getApplication(context, packageName, ignoreNotRegistered = true)
            ?: return failed(request.requestId, "application_not_found")
        val parts = request.argument.split(',')
        val updated = current.copy(
            type = parts.getOrNull(0)?.toIntOrNull() ?: current.type,
            blocked = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: current.blocked,
            islandEnabled = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: current.islandEnabled,
            islandFocusNotification = parts.getOrNull(3)?.toBooleanStrictOrNull()
                ?: current.islandFocusNotification,
            notificationOnRegister = parts.getOrNull(4)?.toBooleanStrictOrNull()
                ?: current.notificationOnRegister,
        )
        applicationGateway.updateApplication(updated)
        return success(request.requestId, "application_updated")
    }

    private fun deleteEvent(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val eventId = request.eventId ?: return failed(request.requestId, "missing_event_id")
        val deleted = runBlocking {
            eventGateway.deleteEvent(
                ManagerEvent(
                    id = eventId,
                    packageName = request.packageName,
                    configOptions = emptySet(),
                    channel = "",
                    receiveDateMs = 0L,
                    title = "",
                    content = "",
                ),
            )
        }
        return if (deleted) {
            success(request.requestId, "event_deleted", resultLong = eventId)
        } else {
            // Already absent is treated as success so retries stay idempotent.
            success(request.requestId, "event_already_absent", resultLong = eventId)
        }
    }

    private fun restoreEvent(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val eventId = request.eventId ?: return failed(request.requestId, "missing_event_id")
        val restored = runBlocking {
            eventGateway.restoreEvent(
                ManagerEvent(
                    id = eventId,
                    packageName = request.packageName,
                    configOptions = emptySet(),
                    channel = "",
                    receiveDateMs = request.longArgument,
                    title = "",
                    content = request.argument,
                    type = request.intArgument,
                ),
            )
        }
        return if (restored != null) {
            success(request.requestId, "event_restored", resultLong = restored.id)
        } else {
            failed(request.requestId, "event_restore_failed")
        }
    }

    private fun clearHistory(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val endExclusive = request.argument.toLongOrNull()
        val deleted = runBlocking {
            when {
                request.longArgument > 0L && endExclusive != null && endExclusive > request.longArgument ->
                    eventGateway.clearHistoryInRange(request.longArgument, endExclusive)
                request.longArgument > 0L && request.argument.isBlank() ->
                    eventGateway.clearHistoryBefore(request.longArgument)
                else -> {
                    runtimeActions.clearHistory()
                    0
                }
            }
        }
        return success(
            requestId = request.requestId,
            details = "history_cleared",
            resultLong = deleted.toLong(),
        )
    }

    private fun setXmppServer(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val host = request.argument.trim()
        if (host.isBlank()) return failed(request.requestId, "missing_xmpp_host")
        runtimeActions.setXmppServer(context, host)
        return success(request.requestId, "xmpp_server_set")
    }

    private fun success(
        requestId: String,
        details: String,
        resultLong: Long = 0L,
    ) = ManagerWriteResultDto(
        requestId = requestId,
        status = ManagerProtocol.WRITE_STATUS_SUCCESS,
        details = details,
        resultLong = resultLong,
    )

    private fun failed(requestId: String, details: String) = ManagerWriteResultDto(
        requestId = requestId,
        status = ManagerProtocol.WRITE_STATUS_FAILED,
        details = details,
    )
}
