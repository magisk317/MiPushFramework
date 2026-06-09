package io.github.magisk317.mipush.app

import android.content.Context
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.platform.support.BoundedShellResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object XSpaceXmsfInstallKeeper {
    private const val XSPACE_USER_ID = 999
    private const val MIN_CHECK_INTERVAL_MS = 60_000L
    private const val USER_LIST_TIMEOUT_MS = 5_000L
    private const val PM_PATH_TIMEOUT_MS = 5_000L
    private const val INSTALL_TIMEOUT_MS = 15_000L

    private val running = AtomicBoolean(false)
    private val lastCheckAtMs = AtomicLong(0L)

    fun schedule(context: Context, source: String) {
        val appContext = context.applicationContext ?: context
        val nowMs = System.currentTimeMillis()
        if (!markCheckAllowed(nowMs)) {
            logD("skip XSpace xmsf install keeper source=$source pkg=${appContext.packageName} reason=rate_limited")
            return
        }
        if (!running.compareAndSet(false, true)) {
            logD("skip XSpace xmsf install keeper source=$source pkg=${appContext.packageName} reason=already_running")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = repairNow(
                    hasRootAccess = { AppRootAccessFacade.refreshRootAccessIfGranted() },
                    runRootCommand = { command, timeoutMs ->
                        AppRootAccessFacade.runRootCommand(command, timeoutMs = timeoutMs)
                    },
                )
                logResult(appContext, source, result)
            } finally {
                running.set(false)
            }
        }
    }

    internal fun repairNow(
        hasRootAccess: () -> Boolean,
        runRootCommand: (String, Long) -> BoundedShellResult,
    ): RepairResult {
        if (!hasRootAccess()) {
            return RepairResult(Stage.ROOT_MISSING)
        }

        val users = runRootCommand("cmd user list", USER_LIST_TIMEOUT_MS)
        if (!users.isSuccess) {
            return RepairResult(Stage.USER_LIST_FAILED, exitCode = users.exitCode)
        }
        if (!hasXSpaceUser(users.stdoutText)) {
            return RepairResult(Stage.XSPACE_USER_NOT_FOUND)
        }

        val beforePath = runRootCommand(pmPathCommand(), PM_PATH_TIMEOUT_MS)
        if (beforePath.isSuccess && isDataAppPath(beforePath.stdoutText)) {
            return RepairResult(Stage.ALREADY_DATA_APP)
        }

        val install = runRootCommand(installExistingCommand(), INSTALL_TIMEOUT_MS)
        if (!install.isSuccess) {
            return RepairResult(Stage.INSTALL_EXISTING_FAILED, exitCode = install.exitCode)
        }

        val afterPath = runRootCommand(pmPathCommand(), PM_PATH_TIMEOUT_MS)
        return if (afterPath.isSuccess && isDataAppPath(afterPath.stdoutText)) {
            RepairResult(Stage.INSTALL_EXISTING_SUCCEEDED)
        } else {
            RepairResult(Stage.VERIFY_FAILED, exitCode = afterPath.exitCode)
        }
    }

    internal fun hasXSpaceUser(output: String): Boolean =
        output.lineSequence().any { it.contains("UserInfo{$XSPACE_USER_ID:") }

    internal fun isDataAppPath(output: String): Boolean =
        output.lineSequence()
            .map { it.trim() }
            .any { it.startsWith("package:/data/app/") }

    internal fun installExistingCommand(): String =
        "cmd package install-existing --user $XSPACE_USER_ID --wait ${Constants.SERVICE_APP_NAME}"

    internal fun pmPathCommand(): String =
        "pm path --user $XSPACE_USER_ID ${Constants.SERVICE_APP_NAME}"

    private fun markCheckAllowed(nowMs: Long): Boolean {
        val previous = lastCheckAtMs.get()
        if (previous > 0 && nowMs - previous in 0 until MIN_CHECK_INTERVAL_MS) {
            return false
        }
        return lastCheckAtMs.compareAndSet(previous, nowMs)
    }

    private fun logResult(context: Context, source: String, result: RepairResult) {
        val message =
            "XSpace xmsf install keeper source=$source pkg=${context.packageName} " +
                "stage=${result.stage} exitCode=${result.exitCode}"
        when (result.stage) {
            Stage.ALREADY_DATA_APP,
            Stage.XSPACE_USER_NOT_FOUND,
            Stage.ROOT_MISSING -> logD(message)
            Stage.INSTALL_EXISTING_SUCCEEDED -> logI(message)
            Stage.USER_LIST_FAILED,
            Stage.INSTALL_EXISTING_FAILED,
            Stage.VERIFY_FAILED -> logW(message)
        }
    }

    data class RepairResult(
        val stage: Stage,
        val exitCode: Int? = null,
    )

    enum class Stage {
        ROOT_MISSING,
        USER_LIST_FAILED,
        XSPACE_USER_NOT_FOUND,
        ALREADY_DATA_APP,
        INSTALL_EXISTING_SUCCEEDED,
        INSTALL_EXISTING_FAILED,
        VERIFY_FAILED,
    }
}
