package io.github.magisk317.mipush.manager.runtime.write

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_GLOBAL_KEY
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_KEY
import io.github.magisk317.mipush.common.LOG_SANITIZATION_ENABLED_KEY
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_STANDBY_BYPASS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_OOM_ADJ
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_DOZE_BYPASS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_ANTI_KILL
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import io.github.magisk317.mipush.common.ISLAND_PREF_FIRST_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLE_FLOAT
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.notification.MockReplayOutcome
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.common.manager.ManagerNotificationGateway
import io.github.magisk317.mipush.common.manager.ZygiskConfigGateway
import io.github.magisk317.mipush.common.fakedevice.ZygiskConfig
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.utils.LogUtils
import kotlinx.coroutines.runBlocking
import io.github.magisk317.xposed.logging.MagiskOtel

class ManagerWriteRuntimeExecutor(
    private val context: Context,
    private val applicationGateway: ManagerApplicationGateway,
    private val eventGateway: ManagerEventGateway,
    private val runtimeActions: ManagerRuntimeActions,
    private val permissionGateway: ManagerPermissionGateway,
    private val logGateway: ManagerLogGateway,
    private val notificationGateway: ManagerNotificationGateway,
    private val zygiskConfigGateway: ZygiskConfigGateway,
    private val idempotencyStore: ManagerWriteIdempotencyStore = ManagerWriteIdempotencyStore(),
) {
    constructor(context: Context) : this(
        context = context,
        applicationGateway = AppDependencies.get(context),
        eventGateway = AppDependencies.get(context),
        runtimeActions = AppDependencies.get(context),
        permissionGateway = AppDependencies.get(context),
        logGateway = AppDependencies.get(context),
        notificationGateway = AppDependencies.get(context),
        zygiskConfigGateway = AppDependencies.get(context),
    )

    fun execute(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        when (val begin = idempotencyStore.begin(request.requestId)) {
            is ManagerWriteIdempotencyStore.BeginResult.Duplicate -> {
                emitWrite(request, begin.result, duplicate = true)
                return begin.result
            }
            ManagerWriteIdempotencyStore.BeginResult.Execute -> Unit
        }
        return try {
            val startedAt = System.nanoTime()
            val result = runCatching { dispatch(request) }.getOrElse {
                failed(request.requestId, "runtime_operation_failed")
            }
            idempotencyStore.complete(result)
            emitWrite(request, result, duplicate = false, startedAt = startedAt)
            result
        } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
            idempotencyStore.abort(request.requestId)
            MagiskOtel.event(
                name = "push.manager",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "write",
                    "reason" to "aborted",
                    "operation" to request.operation.ifBlank { "unknown" },
                    "error_class" to error.javaClass.simpleName,
                ),
                statusOk = false,
            )
            throw error
        }
    }

    private fun emitWrite(
        request: ManagerWriteRequestDto,
        result: ManagerWriteResultDto,
        duplicate: Boolean,
        startedAt: Long = System.nanoTime(),
    ) {
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        val statusOk = result.status == ManagerProtocol.WRITE_STATUS_SUCCESS ||
            result.status == ManagerProtocol.WRITE_STATUS_DUPLICATE
        val resultToken = when (result.status) {
            ManagerProtocol.WRITE_STATUS_SUCCESS -> "ok"
            ManagerProtocol.WRITE_STATUS_DUPLICATE -> "skip"
            ManagerProtocol.WRITE_STATUS_UNSUPPORTED -> "skip"
            else -> "error"
        }
        MagiskOtel.event(
            name = "push.manager",
            attributes = mapOf(
                "result" to resultToken,
                "duration_ms" to durationMs.toString(),
                "process" to "main",
                "stage" to if (duplicate) "duplicate" else "write",
                "reason" to result.details.ifBlank { result.status }.take(64),
                "operation" to request.operation.ifBlank { "unknown" },
            ),
            statusOk = statusOk,
        )
    }

    private fun dispatch(request: ManagerWriteRequestDto): ManagerWriteResultDto =
        when (request.operation) {
            ManagerProtocol.WRITE_OP_UPDATE_APPLICATION -> updateApplication(request)
            ManagerProtocol.WRITE_OP_DELETE_EVENT -> deleteEvent(request)
            ManagerProtocol.WRITE_OP_RESTORE_EVENT -> restoreEvent(request)
            ManagerProtocol.WRITE_OP_MOCK_MESSAGE -> mockMessage(request)
            ManagerProtocol.WRITE_OP_SET_DUAL_APP -> setDualApp(request)
            ManagerProtocol.WRITE_OP_QUERY_DUAL_APP -> queryDualApp(request)
            ManagerProtocol.WRITE_OP_GRANT_SILENT_PERMISSIONS -> grantSilentPermissions(request)
            ManagerProtocol.WRITE_OP_QUERY_ROOT -> queryRoot(request)
            ManagerProtocol.WRITE_OP_SYNC_LAUNCHER_ICON -> syncLauncherIcon(request)
            ManagerProtocol.WRITE_OP_SET_RUNTIME_BOOLEAN -> setRuntimeBoolean(request)
            ManagerProtocol.WRITE_OP_SET_RUNTIME_INT -> setRuntimeInt(request)
            ManagerProtocol.WRITE_OP_RESTART_RUNTIME -> restartRuntime(request)
            ManagerProtocol.WRITE_OP_REBOOT_DEVICE -> rebootDevice(request)
            ManagerProtocol.WRITE_OP_RELAUNCH_MANAGER -> relaunchManager(request)
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
            ManagerProtocol.WRITE_OP_COUNT_EVENTS_BY_DAY -> countEventsByDay(request)
            ManagerProtocol.WRITE_OP_CLEAR_LOG_FOLDERS -> clearLogFolders(request)
            ManagerProtocol.WRITE_OP_DELETE_NOTIFICATION_CHANNEL -> deleteNotificationChannel(request)
            ManagerProtocol.WRITE_OP_ZYGISK_IS_ENABLED -> zygiskIsEnabled(request)
            ManagerProtocol.WRITE_OP_ZYGISK_GET_CONFIG -> zygiskGetConfig(request)
            ManagerProtocol.WRITE_OP_ZYGISK_SAVE_CONFIG -> zygiskSaveConfig(request)
            ManagerProtocol.WRITE_OP_ZYGISK_FORCE_STOP -> zygiskForceStop(request)
            ManagerProtocol.WRITE_OP_REPAIR_XSPACE -> repairXSpace(request)
            ManagerProtocol.WRITE_OP_RESET_TOP_ACTIVITY_CACHE -> resetTopActivityCache(request)
            ManagerProtocol.WRITE_OP_GET_EVENT_CONTENT -> getEventContent(request)
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



    private fun setDualApp(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val result = permissionGateway.setDualAppEnabled(request.booleanArgument)
        val details = when (result.stage) {
            ManagerXSpaceRepairStage.COMPLETED -> ManagerProtocol.WRITE_DETAIL_DUAL_APP_COMPLETED
            ManagerXSpaceRepairStage.ROOT_MISSING -> ManagerProtocol.WRITE_DETAIL_DUAL_APP_ROOT_MISSING
            ManagerXSpaceRepairStage.XSPACE_USER_NOT_FOUND -> ManagerProtocol.WRITE_DETAIL_DUAL_APP_XSPACE_MISSING
            ManagerXSpaceRepairStage.PARTIAL_FAILED -> ManagerProtocol.WRITE_DETAIL_DUAL_APP_PARTIAL_FAILED
        }
        logI(
            "set_dual_app enabled=${request.booleanArgument} stage=${result.stage} " +
                "details=${result.details} mapped=$details",
        )
        return if (result.stage == ManagerXSpaceRepairStage.COMPLETED) {
            success(request.requestId, details)
        } else {
            failed(request.requestId, details)
        }
    }

    private fun queryDualApp(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val installed = permissionGateway.isDualAppInstalled()
        return success(
            requestId = request.requestId,
            details = if (installed) {
                ManagerProtocol.WRITE_DETAIL_DUAL_APP_INSTALLED
            } else {
                ManagerProtocol.WRITE_DETAIL_DUAL_APP_NOT_INSTALLED
            },
            resultLong = if (installed) 1L else 0L,
        )
    }

    private fun grantSilentPermissions(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        // intArgument: userId (0 primary, 999 dual, -1 auto).
        // packageName empty => both framework packages.
        // argument empty/"all" => full silent suite; otherwise a single appop string.
        val userArg = request.intArgument
        val packages = if (request.packageName.isBlank()) {
            listOf(
                io.github.magisk317.mipush.common.Constants.SERVICE_APP_NAME,
                io.github.magisk317.mipush.common.Constants.MANAGER_APP_NAME,
            )
        } else {
            listOf(request.packageName)
        }
        if (!permissionGateway.refreshRootAccessIfGranted()) {
            return failed(request.requestId, ManagerProtocol.WRITE_DETAIL_GRANT_SILENT_ROOT_MISSING)
        }
        val op = request.argument.trim()
        val ok = if (op.isEmpty() || op.equals("all", ignoreCase = true)) {
            io.github.magisk317.mipush.platform.support.PermissionUtils.grantSilentPermissionsForFramework(
                userId = userArg,
                packages = packages,
            )
        } else {
            val users = if (userArg == io.github.magisk317.mipush.platform.support.PermissionUtils.USER_AUTO) {
                listOf(
                    io.github.magisk317.mipush.platform.support.PermissionUtils.USER_PRIMARY,
                    io.github.magisk317.mipush.platform.support.PermissionUtils.USER_XSPACE,
                )
            } else {
                listOf(userArg)
            }
            var any = false
            for (user in users) {
                for (pkg in packages) {
                    if (io.github.magisk317.mipush.platform.support.PermissionUtils.allowPermission(op, pkg, user)) {
                        any = true
                    }
                }
            }
            any
        }
        logI("grant_silent_permissions user=$userArg pkgs=$packages op=${op.ifBlank { "all" }} ok=$ok")
        return if (ok) {
            success(request.requestId, ManagerProtocol.WRITE_DETAIL_GRANT_SILENT_OK)
        } else {
            failed(request.requestId, ManagerProtocol.WRITE_DETAIL_GRANT_SILENT_FAILED)
        }
    }

    private fun queryRoot(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val available = resolveRootAccess(
            requestAuthorization = request.booleanArgument,
            refreshAccess = {
                io.github.magisk317.mipush.platform.support.PermissionUtils.refreshRootAccessIfGranted()
            },
            requestAccess = {
                io.github.magisk317.mipush.platform.support.PermissionUtils.requestRootAccess()
            },
        )
        return success(
            requestId = request.requestId,
            details = if (available) {
                ManagerProtocol.WRITE_DETAIL_ROOT_AVAILABLE
            } else {
                ManagerProtocol.WRITE_DETAIL_ROOT_MISSING
            },
            resultLong = if (available) 1L else 0L,
        )
    }

    private fun syncLauncherIcon(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val iconId = request.argument.ifBlank { "default" }
        if (!io.github.magisk317.mipush.platform.support.PermissionUtils.ensureRootAccess()) {
            return failed(request.requestId, ManagerProtocol.WRITE_DETAIL_SYNC_LAUNCHER_ICON_ROOT_MISSING)
        }
        val ok = io.github.magisk317.mipush.platform.support.PermissionUtils.syncLauncherIconAliases(iconId)
        logI("sync_launcher_icon iconId=$iconId ok=$ok")
        return if (ok) {
            success(request.requestId, ManagerProtocol.WRITE_DETAIL_SYNC_LAUNCHER_ICON_OK)
        } else {
            failed(request.requestId, ManagerProtocol.WRITE_DETAIL_SYNC_LAUNCHER_ICON_FAILED)
        }
    }


    private fun mockMessage(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val eventId = request.eventId ?: return failed(request.requestId, "missing_event_id")
        val outcome = runBlocking {
            eventGateway.mockMessage(
                ManagerEvent(
                    id = eventId,
                    packageName = request.packageName,
                    configOptions = emptySet(),
                    channel = "",
                    receiveDateMs = request.longArgument,
                    title = "",
                    content = "",
                    type = request.intArgument,
                    // payload/regSec intentionally empty: runtime loads authoritative row by id.
                ),
            )
        }
        return when (outcome) {
            MockReplayOutcome.Posted -> success(
                request.requestId,
                ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_POSTED,
                resultLong = eventId,
            )
            MockReplayOutcome.Dispatched -> success(
                request.requestId,
                ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_DISPATCHED,
                resultLong = eventId,
            )
            MockReplayOutcome.BlockedByPermission -> failed(
                request.requestId,
                ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_BLOCKED,
            )
            MockReplayOutcome.Failed -> failed(
                request.requestId,
                ManagerProtocol.WRITE_DETAIL_MOCK_REPLAY_FAILED,
            )
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


    private fun setRuntimeBoolean(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val key = request.argument.trim()
        if (key !in ALLOWED_RUNTIME_BOOLEAN_KEYS) {
            return failed(request.requestId, ManagerProtocol.WRITE_DETAIL_SET_RUNTIME_BOOLEAN_UNKNOWN_KEY)
        }
        val enabled = request.booleanArgument
        runBlocking {
            val repo = PreferenceRepository()
            when (key) {
                COLOR_STATUS_BAR_ICON_KEY -> repo.setColorStatusBarIcon(enabled)
                COLOR_STATUS_BAR_ICON_GLOBAL_KEY -> repo.setColorStatusBarIconGlobal(enabled)
                "debug_mode" -> repo.setDebugMode(enabled)
                LOG_SANITIZATION_ENABLED_KEY -> repo.setLogSanitizationEnabled(enabled)
                "show_all_events" -> repo.setShowAllEvents(enabled)
                "start_foreground" -> repo.setIsStartForeground(enabled)
                "start_push_as_foreground_service" -> repo.setStartPushAsForegroundService(enabled)
                KEEPALIVE_PREF_OOM_ADJ -> repo.setKeepAliveOomAdj(enabled)
                KEEPALIVE_PREF_ANTI_KILL -> repo.setKeepAliveAntiKill(enabled)
                KEEPALIVE_PREF_STANDBY_BYPASS -> repo.setKeepAliveStandbyBypass(enabled)
                KEEPALIVE_PREF_DOZE_BYPASS -> repo.setKeepAliveDozeBypass(enabled)
                ISLAND_PREF_ENABLED -> repo.setIslandEnabled(enabled)
                ISLAND_PREF_FIRST_FLOAT -> repo.setIslandFirstFloat(enabled)
                ISLAND_PREF_ENABLE_FLOAT -> repo.setIslandEnableFloat(enabled)
                ISLAND_PREF_SHOW_NOTIFICATION -> repo.setIslandShowNotification(enabled)
                ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION -> repo.setIslandShowOriginalNotification(enabled)
                ISLAND_PREF_FOCUS_NOTIF -> repo.setIslandFocusNotification(enabled)
                else -> error("unreachable runtime boolean key=$key")
            }
        }
        runCatching {
            context.sendBroadcast(Intent(ACTION_PREF_CHANGED))
        }
        logI("set_runtime_boolean key=$key value=$enabled")
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_SET_RUNTIME_BOOLEAN_OK)
    }

    private fun setRuntimeInt(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val key = request.argument.trim()
        if (key !in ALLOWED_RUNTIME_INT_KEYS) {
            return failed(request.requestId, ManagerProtocol.WRITE_DETAIL_SET_RUNTIME_INT_UNKNOWN_KEY)
        }
        val value = request.intArgument
        runBlocking {
            val repo = PreferenceRepository()
            when (key) {
                ISLAND_PREF_TIMEOUT -> repo.setIslandTimeout(value)
                else -> error("unreachable runtime int key=$key")
            }
        }
        runCatching { context.sendBroadcast(Intent(ACTION_PREF_CHANGED)) }
        logI("set_runtime_int key=$key value=$value")
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_SET_RUNTIME_INT_OK)
    }



    private fun relaunchManager(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val route = request.argument.trim().ifBlank { "settings" }
        // Sanitize route for shell: only allow simple path-like tokens.
        val safeRoute = route.filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == '/' }
            .ifBlank { "settings" }
        val pkg = io.github.magisk317.mipush.platform.support.LegacyComponentNames.MANAGER_PACKAGE
        val activity = io.github.magisk317.mipush.platform.support.LegacyComponentNames.MAIN_ACTIVITY
        val tabFlag = if (
            safeRoute == "settings" ||
            safeRoute.startsWith("settings") ||
            safeRoute == "status_bar_icon_settings" ||
            safeRoute == "connection_status"
        ) {
            " --es extra_start_tab settings"
        } else {
            ""
        }
        // Root am start survives manager process death and bypasses BAL that blocked AlarmManager PI.
        // Delay so manager can finishAndRemoveTask + kill first.
        Handler(Looper.getMainLooper()).postDelayed({
            val cmd = buildString {
                append("am start -n ")
                append(pkg)
                append('/')
                append(activity)
                append(" -f 0x14208000")
                append(" --es extra_start_route ")
                append(safeRoute)
                append(tabFlag)
            }
            val result = io.github.magisk317.mipush.platform.support.AppRootAccessFacade
                .runRootCommand(cmd, timeoutMs = 5_000L)
            logI("relaunch_manager cmd=$cmd ok=${result.isSuccess} out=${result.stdoutText.trim()}")
        }, 650L)
        logI("relaunch_manager scheduled route=$safeRoute")
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_RELAUNCH_MANAGER_OK)
    }

    private fun rebootDevice(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        if (!io.github.magisk317.mipush.platform.support.PermissionUtils.ensureRootAccess()) {
            return failed(request.requestId, ManagerProtocol.WRITE_DETAIL_REBOOT_DEVICE_ROOT_MISSING)
        }
        // Return success first so Binder can complete before the device reboots.
        Handler(Looper.getMainLooper()).postDelayed({
            // Prefer shell reboot; fall back to svc.
            val ok = io.github.magisk317.mipush.platform.support.AppRootAccessFacade
                .runRootCommand("reboot", timeoutMs = 3_000L)
                .isSuccess
            if (!ok) {
                io.github.magisk317.mipush.platform.support.AppRootAccessFacade
                    .runRootCommand("svc power reboot", timeoutMs = 3_000L)
            }
            logI("reboot_device shell issued ok=$ok")
        }, 400L)
        logI("reboot_device scheduled")
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_REBOOT_DEVICE_OK)
    }

    private fun restartRuntime(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        // Return success first; kill after a short delay so Binder can complete.
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { Process.killProcess(Process.myPid()) }
        }, 250L)
        logI("restart_runtime scheduled")
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_RESTART_RUNTIME_OK)
    }


    private fun countEventsByDay(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val counts = runBlocking { eventGateway.countEventsByDay() }
        val encoded = counts.joinToString("\n") { "${it.day}:${it.count}" }
        val details = encoded.take(ManagerProtocol.MAX_LOG_EXPORT_DETAILS_LENGTH)
        return success(
            requestId = request.requestId,
            details = details.ifBlank { ManagerProtocol.WRITE_DETAIL_COUNT_EVENTS_BY_DAY_OK },
            resultLong = counts.size.toLong(),
        )
    }

    private fun clearLogFolders(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val result = logGateway.clearLogFolders(context)
        return if (result.success) {
            success(
                requestId = request.requestId,
                details = result.details.ifBlank { ManagerProtocol.WRITE_DETAIL_CLEAR_LOG_FOLDERS_OK }
                    .take(ManagerProtocol.MAX_LOG_EXPORT_DETAILS_LENGTH),
            )
        } else {
            failed(
                request.requestId,
                result.details.ifBlank { ManagerProtocol.WRITE_DETAIL_CLEAR_LOG_FOLDERS_FAILED }
                    .take(ManagerProtocol.MAX_LOG_EXPORT_DETAILS_LENGTH),
            )
        }
    }

    private fun deleteNotificationChannel(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val packageName = request.packageName.trim()
        val channelId = request.argument.trim()
        if (packageName.isEmpty() || channelId.isEmpty()) {
            return failed(request.requestId, "missing_package_or_channel")
        }
        notificationGateway.deleteNotificationChannel(packageName, channelId)
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_DELETE_NOTIFICATION_CHANNEL_OK)
    }

    private fun zygiskIsEnabled(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val enabled = zygiskConfigGateway.isZygiskModuleEnabled()
        return success(
            requestId = request.requestId,
            details = ManagerProtocol.WRITE_DETAIL_ZYGISK_OK,
            resultLong = if (enabled) 1L else 0L,
        )
    }

    private fun zygiskGetConfig(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val content = zygiskConfigGateway.getZygiskConfig().toFileContent()
        return success(
            requestId = request.requestId,
            details = content.take(ManagerProtocol.MAX_LOG_EXPORT_DETAILS_LENGTH),
            resultLong = content.length.toLong(),
        )
    }

    private fun zygiskSaveConfig(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        // Magisk needs an interactive grant; ensureRootAccess() prompts when not yet authorized.
        if (!io.github.magisk317.mipush.platform.support.PermissionUtils.ensureRootAccess()) {
            return failed(request.requestId, ManagerProtocol.WRITE_DETAIL_ZYGISK_ROOT_MISSING)
        }
        val config = ZygiskConfig.parse(request.argument)
        val ok = zygiskConfigGateway.saveZygiskConfig(config)
        return if (ok) {
            success(request.requestId, ManagerProtocol.WRITE_DETAIL_ZYGISK_OK)
        } else {
            failed(request.requestId, ManagerProtocol.WRITE_DETAIL_ZYGISK_FAILED)
        }
    }

    private fun zygiskForceStop(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val packageName = request.packageName.trim().ifBlank { request.argument.trim() }
        if (packageName.isEmpty()) {
            return failed(request.requestId, "missing_package")
        }
        zygiskConfigGateway.forceStopApp(packageName)
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_ZYGISK_OK)
    }

    private fun repairXSpace(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val result = permissionGateway.repairXSpaceUserSupport()
        val details = when (result.stage) {
            ManagerXSpaceRepairStage.ROOT_MISSING -> ManagerProtocol.WRITE_DETAIL_DUAL_APP_ROOT_MISSING
            ManagerXSpaceRepairStage.XSPACE_USER_NOT_FOUND -> ManagerProtocol.WRITE_DETAIL_DUAL_APP_XSPACE_MISSING
            ManagerXSpaceRepairStage.COMPLETED -> ManagerProtocol.WRITE_DETAIL_DUAL_APP_COMPLETED
            ManagerXSpaceRepairStage.PARTIAL_FAILED -> ManagerProtocol.WRITE_DETAIL_DUAL_APP_PARTIAL_FAILED
        }
        val status = if (result.stage == ManagerXSpaceRepairStage.COMPLETED) {
            ManagerProtocol.WRITE_STATUS_SUCCESS
        } else {
            ManagerProtocol.WRITE_STATUS_FAILED
        }
        return ManagerWriteResultDto(
            requestId = request.requestId,
            status = status,
            details = result.details.ifBlank { details }.take(ManagerProtocol.MAX_LOG_EXPORT_DETAILS_LENGTH),
            resultLong = if (result.xmsfInstalled) 1L else 0L,
        )
    }

    private fun resetTopActivityCache(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        runtimeActions.resetTopActivityCache()
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_RESET_TOP_ACTIVITY_CACHE_OK)
    }

    private fun getEventContent(request: ManagerWriteRequestDto): ManagerWriteResultDto {
        val eventId = request.eventId ?: return failed(request.requestId, "missing_event_id")
        val content = eventGateway.getContent(
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
        return success(
            requestId = request.requestId,
            details = content.take(ManagerProtocol.MAX_LOG_EXPORT_DETAILS_LENGTH),
            resultLong = content.length.toLong(),
        )
    }

    private companion object {
        val ALLOWED_RUNTIME_BOOLEAN_KEYS = setOf(
            COLOR_STATUS_BAR_ICON_KEY,
            COLOR_STATUS_BAR_ICON_GLOBAL_KEY,
            "debug_mode",
            LOG_SANITIZATION_ENABLED_KEY,
            "show_all_events",
            "start_foreground",
            "start_push_as_foreground_service",
            KEEPALIVE_PREF_OOM_ADJ,
            KEEPALIVE_PREF_ANTI_KILL,
            KEEPALIVE_PREF_STANDBY_BYPASS,
            KEEPALIVE_PREF_DOZE_BYPASS,
            ISLAND_PREF_ENABLED,
            ISLAND_PREF_FIRST_FLOAT,
            ISLAND_PREF_ENABLE_FLOAT,
            ISLAND_PREF_SHOW_NOTIFICATION,
            ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION,
            ISLAND_PREF_FOCUS_NOTIF,
        )

        val ALLOWED_RUNTIME_INT_KEYS = setOf(
            ISLAND_PREF_TIMEOUT,
        )
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

internal fun resolveRootAccess(
    requestAuthorization: Boolean,
    refreshAccess: () -> Boolean,
    requestAccess: () -> Boolean,
): Boolean = refreshAccess() || (requestAuthorization && requestAccess())
