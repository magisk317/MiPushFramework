package io.github.magisk317.mipush.app.di

import android.content.Context
import android.net.Uri
import android.os.Process
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.app.MiPushFrameworkApp
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.core.zygisk.ZygiskConfig
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.manager.application.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.manager.application.ManagerApplicationGateway
import io.github.magisk317.mipush.utils.DiagnosticExportModes
import io.github.magisk317.mipush.manager.application.ManagerForceRegisterResult
import io.github.magisk317.mipush.manager.application.ManagerApplications
import io.github.magisk317.mipush.manager.application.ManagerConfigEditorSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigGateway
import io.github.magisk317.mipush.manager.application.ManagerConfigListSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigSyncGateway
import io.github.magisk317.mipush.manager.application.ManagerDualAppInstallationResult
import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.application.EventDebugJson
import io.github.magisk317.mipush.manager.application.ManagerEventGateway
import io.github.magisk317.mipush.manager.application.ManagerEventResult
import io.github.magisk317.mipush.manager.application.ManagerEventType
import io.github.magisk317.mipush.manager.application.ManagerLogClearResult
import io.github.magisk317.mipush.manager.application.ManagerLogExportResult
import io.github.magisk317.mipush.manager.application.ManagerLogGateway
import io.github.magisk317.mipush.manager.application.ManagerNotificationChannelCommandGateway
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.application.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRootAccessState
import io.github.magisk317.mipush.manager.application.ManagerRootSubjectStatus
import io.github.magisk317.mipush.manager.application.ManagerRootTarget
import io.github.magisk317.mipush.manager.application.ManagerRuntimeActions
import io.github.magisk317.mipush.manager.application.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairResult
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.manager.application.MockReplayOutcome
import io.github.magisk317.mipush.config.ConfigNavigationHelper
import io.github.magisk317.mipush.configuration.ConfigEditorSnapshot
import io.github.magisk317.mipush.configuration.ConfigSyncRepository
import io.github.magisk317.mipush.configuration.toSummary
import io.github.magisk317.mipush.common.utils.ElapsedTimer
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.compat.RegistrationStateCompat
import io.github.magisk317.mipush.compat.RegistrationStateStore
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.PermissionUtils
import io.github.magisk317.mipush.platform.support.ShellUtils
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.data.EventRepository
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.db.EventRetentionManager
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.github.magisk317.mipush.runtime.store.kmp.EventRowType
import io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.adapter.container
import io.github.magisk317.mipush.runtime.store.event.type.NotificationType
import io.github.magisk317.mipush.runtime.store.event.type.TypeFactory
import io.github.magisk317.mipush.manager.runtime.read.AndroidManagerApplicationReadSource
import io.github.magisk317.mipush.manager.runtime.read.InstalledApplicationSnapshot
import io.github.magisk317.mipush.manager.runtime.read.ManagerApplicationReadPolicy
import io.github.magisk317.mipush.manager.runtime.read.RegistrationEventSnapshot
import io.github.magisk317.mipush.manager.runtime.read.toManagerApplication
import io.github.magisk317.mipush.manager.runtime.read.toStoredApplicationSnapshot
import io.github.magisk317.mipush.service.runtime.RuntimeSettingsAdapter
import io.github.magisk317.mipush.utils.RegSecUtils
import io.github.magisk317.mipush.utils.LogBundleExporter
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.mipush.utils.RegistrationHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Date
import java.util.Locale

class XmsfManagerPermissionGateway : ManagerPermissionGateway {
    companion object {
        private const val XSPACE_USER_ID = 999
        private val XSPACE_SUPPORT_PACKAGES = listOf(
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.android.externalstorage",
            "com.android.providers.downloads",
            "com.android.providers.media.module",
            "com.android.providers.media",
            "com.android.mtp",
        )
    }

    override suspend fun getRootAccessSnapshot(refresh: Boolean): ManagerRootAccessSnapshot {
        val granted = if (refresh) {
            PermissionUtils.refreshRootAccessIfGranted()
        } else {
            PermissionUtils.hasCachedRootAccess()
        }
        return rootSnapshot(
            runtimeState = if (granted) {
                ManagerRootAccessState.GRANTED
            } else {
                ManagerRootAccessState.NOT_GRANTED
            },
        )
    }

    override suspend fun requestRootAccess(target: ManagerRootTarget): ManagerRootAccessSnapshot {
        val runtimeState = if (target == ManagerRootTarget.RUNTIME && PermissionUtils.requestRootAccess()) {
            ManagerRootAccessState.GRANTED
        } else if (PermissionUtils.refreshRootAccessIfGranted()) {
            ManagerRootAccessState.GRANTED
        } else {
            ManagerRootAccessState.NOT_GRANTED
        }
        return rootSnapshot(runtimeState)
    }

    override suspend fun hasCachedRootAccess(): Boolean = PermissionUtils.hasCachedRootAccess()

    override suspend fun refreshRootAccessIfGranted(): Boolean = PermissionUtils.refreshRootAccessIfGranted()

    override suspend fun requestRootAccess(): Boolean = PermissionUtils.requestRootAccess()

    override suspend fun repairXSpaceUserSupport(): ManagerXSpaceRepairResult {
        if (!canManageDualAppFromUser(Utils.myUserId())) {
            return ManagerXSpaceRepairResult(
                stage = ManagerXSpaceRepairStage.PRIMARY_USER_REQUIRED,
                details = "currentUserId=${Utils.myUserId()}",
            )
        }
        if (!PermissionUtils.refreshRootAccessIfGranted()) {
            return ManagerXSpaceRepairResult(stage = ManagerXSpaceRepairStage.ROOT_MISSING)
        }
        val users = runRootCommand("cmd user list", timeoutMs = 5_000L)
        if (!users.isSuccess || !users.output.contains("{${XSPACE_USER_ID}:")) {
            return ManagerXSpaceRepairResult(
                stage = ManagerXSpaceRepairStage.XSPACE_USER_NOT_FOUND,
                details = users.output.ifBlank { users.stderrText },
            )
        }

        val managerInstalled = isPackageInstalledForUser(Constants.MANAGER_APP_NAME)
        if (!managerInstalled) {
            if (isPackageInstalledForUser(Constants.SERVICE_APP_NAME)) {
                uninstallForUser(Constants.SERVICE_APP_NAME)
            }
            val xmsfInstalled = isPackageInstalledForUser(Constants.SERVICE_APP_NAME)
            return ManagerXSpaceRepairResult(
                stage = if (!xmsfInstalled) {
                    ManagerXSpaceRepairStage.COMPLETED
                } else {
                    ManagerXSpaceRepairStage.PARTIAL_FAILED
                },
                xmsfInstalled = xmsfInstalled,
                documentsUiAvailable = false,
                details = "managerInstalled=false, xmsfInstalled=$xmsfInstalled",
            )
        }

        installExistingForUser(Constants.SERVICE_APP_NAME)
        XSPACE_SUPPORT_PACKAGES.forEach(::installExistingForUser)
        val xmsfInstalled = isPackageInstalledForUser(Constants.SERVICE_APP_NAME)
        val documentsUiAvailable = canResolveDocumentTreePicker()
        val details = buildString {
            append("managerInstalled=")
            append(managerInstalled)
            append(", ")
            append("xmsfInstalled=")
            append(xmsfInstalled)
            append(", documentsUiAvailable=")
            append(documentsUiAvailable)
        }
        return ManagerXSpaceRepairResult(
            stage = if (xmsfInstalled && documentsUiAvailable) {
                ManagerXSpaceRepairStage.COMPLETED
            } else {
                ManagerXSpaceRepairStage.PARTIAL_FAILED
            },
            xmsfInstalled = xmsfInstalled,
            documentsUiAvailable = documentsUiAvailable,
            details = details,
        )
    }

    override suspend fun setDualAppEnabled(enabled: Boolean): ManagerXSpaceRepairResult {
        if (!canManageDualAppFromUser(Utils.myUserId())) {
            return ManagerXSpaceRepairResult(
                stage = ManagerXSpaceRepairStage.PRIMARY_USER_REQUIRED,
                details = "currentUserId=${Utils.myUserId()}",
            )
        }
        // refreshRootAccessIfGranted() returns false while Shell grant state is still null
        // (unknown). requestRootAccess() forces Shell init + probe so Magisk-granted apps work.
        if (!PermissionUtils.refreshRootAccessIfGranted() && !PermissionUtils.requestRootAccess()) {
            return ManagerXSpaceRepairResult(stage = ManagerXSpaceRepairStage.ROOT_MISSING)
        }
        val users = runRootCommand("cmd user list", timeoutMs = 5_000L)
        if (!users.isSuccess || !users.output.contains("{${XSPACE_USER_ID}:")) {
            return ManagerXSpaceRepairResult(
                stage = ManagerXSpaceRepairStage.XSPACE_USER_NOT_FOUND,
                details = users.output.ifBlank { users.stderrText },
            )
        }

        if (enabled) {
            installExistingForUser(Constants.SERVICE_APP_NAME)
            installExistingForUser(Constants.MANAGER_APP_NAME)
            installExistingForUser(Constants.XMSF_KEEPER_APP_NAME)
        } else {
            uninstallForUser(Constants.SERVICE_APP_NAME)
            uninstallForUser(Constants.MANAGER_APP_NAME)
            uninstallForUser(Constants.XMSF_KEEPER_APP_NAME)
        }

        val xmsfInstalled = isPackageInstalledForUser(Constants.SERVICE_APP_NAME)
        val managerInstalled = isPackageInstalledForUser(Constants.MANAGER_APP_NAME)
        val keeperInstalled = isPackageInstalledForUser(Constants.XMSF_KEEPER_APP_NAME)
        val expectedInstalled = enabled
        val succeeded = xmsfInstalled == expectedInstalled &&
            managerInstalled == expectedInstalled &&
            keeperInstalled == expectedInstalled
        if (succeeded) {
            // Persist runtime-owned dual_app_enabled so XSpaceXmsfInstallKeeper can keep packages in sync.
            try {
                val context = Utils.context?.applicationContext ?: return ManagerXSpaceRepairResult(
                    stage = ManagerXSpaceRepairStage.PARTIAL_FAILED,
                    details = "application_context_unavailable",
                )
                PreferenceRepository(context.dataStore).setDualAppEnabled(enabled)
                // Notify SystemUI / system_server IslandPreferences caches immediately.
                context.sendBroadcast(android.content.Intent(ACTION_PREF_CHANGED))
            } catch (_: RuntimeException) {
                // Package state remains authoritative even if the local mirror cannot update.
            }
            if (enabled) {
                // Root-grant silent permissions for primary + dual-space (xmsf + manager).
                PermissionUtils.grantSilentPermissionsForFramework(
                    userId = PermissionUtils.USER_AUTO,
                )
            } else {
                // Still re-assert main-user silent grants after dual-app teardown.
                PermissionUtils.grantSilentPermissionsForFramework(
                    userId = PermissionUtils.USER_PRIMARY,
                )
            }
        }

        return ManagerXSpaceRepairResult(
            stage = if (succeeded) {
                ManagerXSpaceRepairStage.COMPLETED
            } else {
                ManagerXSpaceRepairStage.PARTIAL_FAILED
            },
            xmsfInstalled = xmsfInstalled,
            details = "enabled=$enabled, xmsfInstalled=$xmsfInstalled, " +
                "managerInstalled=$managerInstalled, keeperInstalled=$keeperInstalled",
        )
    }

    override suspend fun getDualAppInstallation(): ManagerDualAppInstallationResult {
        if (!canManageDualAppFromUser(Utils.myUserId())) {
            return ManagerDualAppInstallationResult.Unavailable("primary_user_required")
        }
        if (!PermissionUtils.hasCachedRootAccess() &&
            !PermissionUtils.refreshRootAccessIfGranted()
        ) {
            return ManagerDualAppInstallationResult.Unavailable("root_unavailable")
        }
        val users = runRootCommand("cmd user list", timeoutMs = 5_000L)
        if (!users.isSuccess) {
            return ManagerDualAppInstallationResult.Unavailable("user_list_unavailable")
        }
        if (!users.output.contains("{${XSPACE_USER_ID}:")) {
            return ManagerDualAppInstallationResult.NotInstalled
        }
        return if (isPackageInstalledForUser(Constants.SERVICE_APP_NAME) &&
            isPackageInstalledForUser(Constants.MANAGER_APP_NAME) &&
            isPackageInstalledForUser(Constants.XMSF_KEEPER_APP_NAME)
        ) {
            ManagerDualAppInstallationResult.Installed
        } else {
            ManagerDualAppInstallationResult.NotInstalled
        }
    }

    override suspend fun launchAppOps(context: Context, permission: String, tips: CharSequence): Boolean =
        PermissionUtils.lunchAppOps(context, permission, tips)

    override suspend fun isUsageStatsAllowedByRoot(packageName: String): Boolean {
        if (!PermissionUtils.hasCachedRootAccess()) return false
        val commands = listOf(
            "appops get $packageName GET_USAGE_STATS",
            "appops get $packageName android:get_usage_stats",
            "cmd appops get $packageName GET_USAGE_STATS",
        )
        return commands.any { command ->
            val result = ShellUtils.execCmd(command, true, true)
            val output = buildString {
                append(result.successMsg.orEmpty())
                append('\n')
                append(result.errorMsg.orEmpty())
            }
            isUsageStatsAppOpAllowed(output)
        }
    }

    override suspend fun requestIgnoreBatteryOptimizations(context: Context): Boolean =
        PermissionUtils.requestIgnoreBatteryOptimizations(context)

    override suspend fun grantNotificationPermission(context: Context): Boolean =
        PermissionUtils.grantNotificationPermission(context)

    private fun rootSnapshot(runtimeState: ManagerRootAccessState): ManagerRootAccessSnapshot {
        val userId = Utils.myUserId()
        val managerUid = Utils.context?.packageManager?.let { packageManager ->
            runCatching { packageManager.getPackageUid(Constants.MANAGER_APP_NAME, 0) }.getOrNull()
        }
        return ManagerRootAccessSnapshot(
            userId = userId,
            manager = ManagerRootSubjectStatus(
                target = ManagerRootTarget.MANAGER,
                packageName = Constants.MANAGER_APP_NAME,
                userId = userId,
                uid = managerUid,
                state = ManagerRootAccessState.UNAVAILABLE,
            ),
            runtime = ManagerRootSubjectStatus(
                target = ManagerRootTarget.RUNTIME,
                packageName = Constants.SERVICE_APP_NAME,
                userId = userId,
                uid = Process.myUid(),
                state = runtimeState,
            ),
        )
    }

    private fun installExistingForUser(packageName: String) {
        runRootCommand(
            command = "cmd package install-existing --user $XSPACE_USER_ID --wait $packageName",
            timeoutMs = 15_000L,
        )
    }

    private fun uninstallForUser(packageName: String) {
        runRootCommand(
            command = "cmd package uninstall --user $XSPACE_USER_ID $packageName",
            timeoutMs = 15_000L,
        )
    }

    private fun isPackageInstalledForUser(packageName: String): Boolean {
        val result = runRootCommand(
            "cmd package list packages --user $XSPACE_USER_ID $packageName",
            timeoutMs = 5_000L,
        )
        return result.isSuccess && result.stdoutText.lineSequence().any { it.trim() == "package:$packageName" }
    }

    private fun canResolveDocumentTreePicker(): Boolean {
        val result = runRootCommand(
            command = "cmd package resolve-activity --brief --user $XSPACE_USER_ID -a android.intent.action.OPEN_DOCUMENT_TREE",
            timeoutMs = 5_000L,
        )
        return result.isSuccess && result.stdout.none { it.contains("No activity found", ignoreCase = true) } &&
            result.stdoutText.contains("/")
    }

    private fun runRootCommand(command: String, timeoutMs: Long) =
        io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand(
            command,
            timeoutMs = timeoutMs,
        )

    private val io.github.magisk317.mipush.platform.support.BoundedShellResult.output: String
        get() = buildString {
            append(stdoutText)
            if (stderrText.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(stderrText)
            }
        }
}

internal fun isUsageStatsAppOpAllowed(output: String): Boolean = output.lineSequence().any { line ->
    val normalized = line.trim().lowercase()
    val isUsageStatsLine = normalized.contains("get_usage_stats:") ||
        normalized.contains("android:get_usage_stats:")
    isUsageStatsLine && listOf("allow", "foreground", "default").any { mode ->
        normalized.contains(": $mode")
    }
}

internal fun canManageDualAppFromUser(userId: Int): Boolean = userId == PermissionUtils.USER_PRIMARY
