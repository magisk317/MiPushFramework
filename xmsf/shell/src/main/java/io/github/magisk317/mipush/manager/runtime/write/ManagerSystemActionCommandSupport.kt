package io.github.magisk317.mipush.manager.runtime.write

import android.os.Handler
import android.os.Looper
import android.os.Process
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.platform.support.LegacyComponentNames
import io.github.magisk317.mipush.platform.support.PermissionUtils

/** Shell-local delayed system action handlers; Binder responses remain completed before side effects. */
internal object ManagerSystemActionCommandSupport {
    fun relaunchManager(
        request: ManagerWriteRequestDto,
        logInfo: (String) -> Unit,
    ): ManagerWriteResultDto {
        val route = request.argument.trim().ifBlank { "settings" }
        val safeRoute = route.filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == '/' }
            .ifBlank { "settings" }
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
        Handler(Looper.getMainLooper()).postDelayed({
            val command = buildString {
                append("am start -n ")
                append(LegacyComponentNames.MANAGER_PACKAGE)
                append('/')
                append(LegacyComponentNames.MAIN_ACTIVITY)
                append(" -f 0x14208000")
                append(" --es extra_start_route ")
                append(safeRoute)
                append(tabFlag)
            }
            val result = AppRootAccessFacade.runRootCommand(command, timeoutMs = 5_000L)
            logInfo("relaunch_manager cmd=$command ok=${result.isSuccess} out=${result.stdoutText.trim()}")
        }, 650L)
        logInfo("relaunch_manager scheduled route=$safeRoute")
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_RELAUNCH_MANAGER_OK)
    }

    fun rebootDevice(
        request: ManagerWriteRequestDto,
        logInfo: (String) -> Unit,
    ): ManagerWriteResultDto {
        if (!PermissionUtils.requestRootAccess()) {
            return failed(request.requestId, ManagerProtocol.WRITE_DETAIL_REBOOT_DEVICE_ROOT_MISSING)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            val rebooted = AppRootAccessFacade.runRootCommand("reboot", timeoutMs = 3_000L).isSuccess
            if (!rebooted) {
                AppRootAccessFacade.runRootCommand("svc power reboot", timeoutMs = 3_000L)
            }
            logInfo("reboot_device shell issued ok=$rebooted")
        }, 400L)
        logInfo("reboot_device scheduled")
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_REBOOT_DEVICE_OK)
    }

    fun restartRuntime(
        request: ManagerWriteRequestDto,
        logInfo: (String) -> Unit,
    ): ManagerWriteResultDto {
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { Process.killProcess(Process.myPid()) }
        }, 250L)
        logInfo("restart_runtime scheduled")
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_RESTART_RUNTIME_OK)
    }

    private fun success(requestId: String, details: String) = ManagerWriteResultDto(
        requestId = requestId,
        status = ManagerProtocol.WRITE_STATUS_SUCCESS,
        details = details,
    )

    private fun failed(requestId: String, details: String) = ManagerWriteResultDto(
        requestId = requestId,
        status = ManagerProtocol.WRITE_STATUS_FAILED,
        details = details,
    )
}
