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

class RemoteZygiskConfigGateway(
    private val client: ManagerRuntimeClient,
) : ZygiskConfigGateway {
    override suspend fun isZygiskModuleEnabled(): ZygiskModuleReadResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_IS_ENABLED,
        ) ?: return ZygiskModuleReadResult.Unavailable("runtime_unavailable")
        if (!RemoteWriteSupport.isSuccess(result)) {
            return ZygiskModuleReadResult.Unavailable(result.details.ifBlank { "zygisk_status_unavailable" })
        }
        return ZygiskModuleReadResult.Available(result.resultLong == 1L)
    }

    override fun getZygiskConfigPath(): String = "/data/adb/mipush_zygisk/app.conf"

    override suspend fun getZygiskConfig(): ZygiskConfigReadResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_GET_CONFIG,
        ) ?: return ZygiskConfigReadResult.Unavailable("runtime_unavailable")
        if (!RemoteWriteSupport.isSuccess(result)) {
            return ZygiskConfigReadResult.Unavailable(result.details.ifBlank { "zygisk_config_unavailable" })
        }
        return ZygiskConfigReadResult.Available(ZygiskConfig.parse(result.details))
    }

    override suspend fun saveZygiskConfig(config: ZygiskConfig): Boolean {
        val content = config.toFileContent()
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_SAVE_CONFIG,
            argument = content,
        ) ?: return false
        return RemoteWriteSupport.isSuccess(result)
    }

    override suspend fun forceStopApp(packageName: String): Boolean {
        return RemoteWriteSupport.isSuccess(RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_FORCE_STOP,
            packageName = packageName,
        ))
    }

    override suspend fun scanZygiskPackages(): ZygiskPackageScanResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_SCAN,
        ) ?: return ZygiskPackageScanResult.Unavailable("runtime_unavailable")
        return if (RemoteWriteSupport.isSuccess(result)) {
            ZygiskPackageScanResult.Available(result.details)
        } else {
            ZygiskPackageScanResult.Unavailable(result.details.ifBlank { "zygisk_scan_unavailable" })
        }
    }
}
