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

class XmsfZygiskConfigGateway : io.github.magisk317.mipush.manager.application.ZygiskConfigGateway {
    companion object {
        private const val ZYGISK_CONFIG_PATH = "/data/adb/mipush_zygisk/app.conf"
    }

    private fun hasExistingRootForZygisk(): Boolean =
        io.github.magisk317.mipush.platform.support.PermissionUtils.hasCachedRootAccess() ||
            io.github.magisk317.mipush.platform.support.PermissionUtils.refreshRootAccessIfGranted()

    override suspend fun isZygiskModuleEnabled(): io.github.magisk317.mipush.manager.application.ZygiskModuleReadResult {
        if (!hasExistingRootForZygisk()) {
            return io.github.magisk317.mipush.manager.application.ZygiskModuleReadResult.Unavailable("zygisk_root_missing")
        }
        return try {
            val getPropMethod = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java, String::class.java)
            val result = getPropMethod.invoke(null, "mipush.zygisk.enabled", "false") as String
            io.github.magisk317.mipush.manager.application.ZygiskModuleReadResult.Available(result == "true")
        } catch (_: Exception) {
            io.github.magisk317.mipush.manager.application.ZygiskModuleReadResult.Unavailable("zygisk_status_read_failed")
        }
    }

    override fun getZygiskConfigPath(): String = ZYGISK_CONFIG_PATH

    override suspend fun getZygiskConfig(): io.github.magisk317.mipush.manager.application.ZygiskConfigReadResult {
        if (!hasExistingRootForZygisk()) {
            return io.github.magisk317.mipush.manager.application.ZygiskConfigReadResult.Unavailable("zygisk_root_missing")
        }
        val result = io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand(
            "cat $ZYGISK_CONFIG_PATH",
            timeoutMs = 5_000L,
        )
        if (!result.isSuccess) {
            return io.github.magisk317.mipush.manager.application.ZygiskConfigReadResult.Unavailable(
                result.stderr.joinToString(" ").ifBlank { "zygisk_config_read_failed" },
            )
        }
        return io.github.magisk317.mipush.manager.application.ZygiskConfigReadResult.Available(
            ZygiskConfig.parse(result.stdout.joinToString("\n")),
        )
    }

    override suspend fun saveZygiskConfig(config: ZygiskConfig): Boolean {
        if (!hasExistingRootForZygisk()) return false
        val content = config.toFileContent()
        val command = listOf(
            "mkdir -p /data/adb/mipush_zygisk",
            "chmod 700 /data/adb/mipush_zygisk",
            "printf %s ${shellQuote(content)} > $ZYGISK_CONFIG_PATH",
            "chmod 600 $ZYGISK_CONFIG_PATH",
        ).joinToString(" && ")
        val result = io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand(
            command,
            timeoutMs = 5_000L,
        )
        return result.isSuccess
    }

    override suspend fun forceStopApp(packageName: String): Boolean {
        if (!hasExistingRootForZygisk()) return false
        return io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand(
            "am force-stop ${shellQuote(packageName)}",
            timeoutMs = 5_000L,
        ).isSuccess
    }

    override suspend fun scanZygiskPackages(): io.github.magisk317.mipush.manager.application.ZygiskPackageScanResult {
        if (!hasExistingRootForZygisk()) {
            return io.github.magisk317.mipush.manager.application.ZygiskPackageScanResult.Unavailable("zygisk_root_missing")
        }
        val result = io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand(
            "/system/bin/sh /data/adb/modules/mipush_zygisk/bin/mipushctl scan",
            timeoutMs = 30_000L,
        )
        return if (result.isSuccess) {
            io.github.magisk317.mipush.manager.application.ZygiskPackageScanResult.Available(
                result.stdout.joinToString("\n"),
            )
        } else {
            io.github.magisk317.mipush.manager.application.ZygiskPackageScanResult.Unavailable(
                result.stderr.joinToString(" ").ifBlank { "zygisk_scan_failed" },
            )
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
