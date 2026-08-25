package io.github.magisk317.mipush.app

import android.content.Context
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.platform.support.BoundedShellResult
import io.github.magisk317.mipush.platform.support.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import io.github.magisk317.xposed.logging.MagiskOtel

object XSpaceXmsfInstallKeeper {
    private const val XSPACE_USER_ID = 999
    private const val MIN_CHECK_INTERVAL_MS = 60_000L
    private const val USER_LIST_TIMEOUT_MS = 5_000L
    private const val PACKAGE_LIST_TIMEOUT_MS = 5_000L
    private const val INSTALL_TIMEOUT_MS = 15_000L
    private const val UNINSTALL_TIMEOUT_MS = 15_000L
    private const val ROOT_RETRY_ATTEMPTS = 4
    private const val ROOT_RETRY_DELAY_MS = 15_000L

    private val running = AtomicBoolean(false)
    private val lastCheckAtMs = AtomicLong(0L)

    fun scheduleForced(context: Context, source: String) {
        schedule(context, source, forced = true)
    }

    fun schedule(context: Context, source: String) {
        schedule(context, source, forced = false)
    }

    private fun schedule(context: Context, source: String, forced: Boolean) {
        val appContext = context.applicationContext ?: context
        val currentUserId = Utils.myUserId()
        if (!canManageXSpaceFromUser(currentUserId)) {
            logD(
                "skip XSpace xmsf install keeper source=$source pkg=${appContext.packageName} " +
                    "userId=$currentUserId reason=primary_user_required",
            )
            return
        }
        if (forced) {
            lastCheckAtMs.set(0L)
        }
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
                // Boot/update can race Magisk grant. Retry a few times so a disabled dual-app
                // toggle still purges zombie user-999 packages after reboot/reinstall.
                var attempt = 0
                var result: RepairResult
                while (true) {
                    attempt += 1
                    val isDualAppEnabled = readDualAppEnabled(appContext)
                    result = repairNow(
                        hasRootAccess = { AppRootAccessFacade.refreshRootAccessIfGranted() },
                        runRootCommand = { command, timeoutMs ->
                            AppRootAccessFacade.runRootCommand(command, timeoutMs = timeoutMs)
                        },
                        isDualAppEnabled = isDualAppEnabled,
                        currentUserId = currentUserId,
                    )
                    val shouldRetry = result.stage == Stage.ROOT_MISSING && attempt < ROOT_RETRY_ATTEMPTS
                    if (!shouldRetry) break
                    logW(
                        "XSpace xmsf install keeper root missing, retry $attempt/$ROOT_RETRY_ATTEMPTS " +
                            "source=$source",
                    )
                    delay(ROOT_RETRY_DELAY_MS)
                }
                logResult(appContext, source, result)
                val failed = result.stage.name.contains("FAILED") || result.stage == Stage.ROOT_MISSING
                MagiskOtel.event(
                    name = "push.xspace",
                    attributes = mapOf(
                        "result" to if (failed) "error" else "ok",
                        "duration_ms" to "0",
                        "process" to "xmsf",
                        "stage" to "repair",
                        "reason" to result.stage.name.lowercase(),
                        "source" to source,
                    ),
                    statusOk = !failed,
                )
            } finally {
                running.set(false)
            }
        }
    }

    private fun readDualAppEnabled(context: Context): Boolean {
        return runCatching {
            val repository = PreferenceRepository(context.dataStore)
            runBlocking { repository.dualAppEnabled.first() }
        }.getOrDefault(false)
    }

    internal fun repairNow(
        hasRootAccess: () -> Boolean,
        runRootCommand: (String, Long) -> BoundedShellResult,
        isDualAppEnabled: Boolean = false,
        currentUserId: Int = PermissionUtils.USER_PRIMARY,
    ): RepairResult {
        if (!canManageXSpaceFromUser(currentUserId)) {
            return RepairResult(Stage.PRIMARY_USER_REQUIRED)
        }
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

        val packageNames = listOf(
            Constants.SERVICE_APP_NAME,
            Constants.MANAGER_APP_NAME,
            Constants.XMSF_KEEPER_APP_NAME,
        )
        val installedPackages = packageNames.filter { packageName ->
            isPackageListed(
                runRootCommand(listPackageCommand(packageName), PACKAGE_LIST_TIMEOUT_MS),
                packageName,
            )
        }

        if (!isDualAppEnabled) {
            // Toggle is OFF: uninstall all synchronized XSpace packages if present.
            if (installedPackages.isEmpty()) {
                return RepairResult(Stage.ALREADY_SYNCHRONIZED)
            }
            installedPackages.forEach { packageName ->
                runRootCommand(uninstallCommand(packageName), UNINSTALL_TIMEOUT_MS)
            }
            val allRemoved = packageNames.none { packageName ->
                isPackageListed(
                    runRootCommand(listPackageCommand(packageName), PACKAGE_LIST_TIMEOUT_MS),
                    packageName,
                )
            }
            return if (allRemoved) {
                RepairResult(Stage.UNINSTALL_SUCCEEDED)
            } else {
                RepairResult(Stage.UNINSTALL_VERIFY_FAILED)
            }
        }

        // Toggle is ON: install all synchronized XSpace packages if absent.
        val missingPackages = packageNames.filterNot { it in installedPackages }
        if (missingPackages.isEmpty()) {
            return RepairResult(Stage.ALREADY_SYNCHRONIZED)
        }
        missingPackages.forEach { packageName ->
            runRootCommand(installExistingCommand(packageName), INSTALL_TIMEOUT_MS)
        }

        val allInstalled = packageNames.all { packageName ->
            isPackageListed(
                runRootCommand(listPackageCommand(packageName), PACKAGE_LIST_TIMEOUT_MS),
                packageName,
            )
        }
        return if (allInstalled) {
            RepairResult(Stage.INSTALL_EXISTING_SUCCEEDED)
        } else {
            RepairResult(Stage.VERIFY_FAILED)
        }
    }

    internal fun hasXSpaceUser(output: String): Boolean =
        output.lineSequence().any { it.contains("UserInfo{$XSPACE_USER_ID:") }

    internal fun isPackageListed(result: BoundedShellResult, packageName: String): Boolean =
        result.isSuccess && result.stdoutText.lineSequence().any { it.trim() == "package:$packageName" }

    internal fun installExistingCommand(packageName: String = Constants.SERVICE_APP_NAME): String =
        "cmd package install-existing --user $XSPACE_USER_ID --wait $packageName"

    internal fun uninstallCommand(packageName: String = Constants.SERVICE_APP_NAME): String =
        "cmd package uninstall --user $XSPACE_USER_ID $packageName"

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
            Stage.PRIMARY_USER_REQUIRED,
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
        PRIMARY_USER_REQUIRED,
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

    internal fun canManageXSpaceFromUser(userId: Int): Boolean =
        userId == PermissionUtils.USER_PRIMARY
}
