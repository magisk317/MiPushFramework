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
    private const val PACKAGE_LIST_TIMEOUT_MS = 5_000L
    private const val INSTALL_TIMEOUT_MS = 15_000L
    private const val UNINSTALL_TIMEOUT_MS = 15_000L

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

        val moduleList = runRootCommand(listPackageCommand(Constants.MANAGER_APP_NAME), PACKAGE_LIST_TIMEOUT_MS)
        val moduleInstalled = isPackageListed(moduleList, Constants.MANAGER_APP_NAME)
        val beforeList = runRootCommand(listPackageCommand(Constants.SERVICE_APP_NAME), PACKAGE_LIST_TIMEOUT_MS)

        if (!moduleInstalled) {
            if (!isPackageListed(beforeList, Constants.SERVICE_APP_NAME)) {
                return RepairResult(Stage.MODULE_ABSENT_XMSF_ABSENT)
            }
            val uninstall = runRootCommand(uninstallCommand(), UNINSTALL_TIMEOUT_MS)
            if (!uninstall.isSuccess) {
                return RepairResult(Stage.UNINSTALL_FAILED, exitCode = uninstall.exitCode)
            }
            val afterList = runRootCommand(listPackageCommand(Constants.SERVICE_APP_NAME), PACKAGE_LIST_TIMEOUT_MS)
            return if (!isPackageListed(afterList, Constants.SERVICE_APP_NAME)) {
                RepairResult(Stage.UNINSTALL_SUCCEEDED)
            } else {
                RepairResult(Stage.UNINSTALL_VERIFY_FAILED, exitCode = afterList.exitCode)
            }
        }

        if (isPackageListed(beforeList, Constants.SERVICE_APP_NAME)) {
            return RepairResult(Stage.ALREADY_SYNCHRONIZED)
        }

        val install = runRootCommand(installExistingCommand(), INSTALL_TIMEOUT_MS)
        if (!install.isSuccess) {
            return RepairResult(Stage.INSTALL_EXISTING_FAILED, exitCode = install.exitCode)
        }

        val afterList = runRootCommand(listPackageCommand(Constants.SERVICE_APP_NAME), PACKAGE_LIST_TIMEOUT_MS)
        return if (isPackageListed(afterList, Constants.SERVICE_APP_NAME)) {
            RepairResult(Stage.INSTALL_EXISTING_SUCCEEDED)
        } else {
            RepairResult(Stage.VERIFY_FAILED, exitCode = afterList.exitCode)
        }
    }

    internal fun hasXSpaceUser(output: String): Boolean =
        output.lineSequence().any { it.contains("UserInfo{$XSPACE_USER_ID:") }

    internal fun isPackageListed(result: BoundedShellResult, packageName: String): Boolean =
        result.isSuccess && result.stdoutText.lineSequence().any { it.trim() == "package:$packageName" }

    internal fun installExistingCommand(): String =
        "cmd package install-existing --user $XSPACE_USER_ID --wait ${Constants.SERVICE_APP_NAME}"

    internal fun uninstallCommand(): String =
        "cmd package uninstall --user $XSPACE_USER_ID ${Constants.SERVICE_APP_NAME}"

    internal fun listPackageCommand(packageName: String = Constants.SERVICE_APP_NAME): String =
        "cmd package list packages --user $XSPACE_USER_ID $packageName"

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
            Stage.ALREADY_SYNCHRONIZED,
            Stage.XSPACE_USER_NOT_FOUND,
            Stage.MODULE_ABSENT_XMSF_ABSENT,
            Stage.ROOT_MISSING -> logD(message)
            Stage.INSTALL_EXISTING_SUCCEEDED,
            Stage.UNINSTALL_SUCCEEDED -> logI(message)
            Stage.USER_LIST_FAILED,
            Stage.INSTALL_EXISTING_FAILED,
            Stage.VERIFY_FAILED,
            Stage.UNINSTALL_FAILED,
            Stage.UNINSTALL_VERIFY_FAILED -> logW(message)
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
        MODULE_ABSENT_XMSF_ABSENT,
        ALREADY_SYNCHRONIZED,
        INSTALL_EXISTING_SUCCEEDED,
        INSTALL_EXISTING_FAILED,
        VERIFY_FAILED,
        UNINSTALL_SUCCEEDED,
        UNINSTALL_FAILED,
        UNINSTALL_VERIFY_FAILED,
    }
}
