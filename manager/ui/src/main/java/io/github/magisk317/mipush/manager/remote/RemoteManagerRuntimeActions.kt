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

class RemoteManagerRuntimeActions(
    private val client: ManagerRuntimeClient,
) : ManagerRuntimeActions {
    private val connectionSource = RemoteConnectionSnapshotSource(client)

    override suspend fun clearHistory() {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
            ),
            operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
        )
    }

    override suspend fun startMiPushServiceAsForegroundService(context: Context) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_START_FOREGROUND,
            ),
            operation = ManagerProtocol.WRITE_OP_START_FOREGROUND,
        )
    }

    override suspend fun resetTopActivityCache() {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_RESET_TOP_ACTIVITY_CACHE,
            ),
            operation = ManagerProtocol.WRITE_OP_RESET_TOP_ACTIVITY_CACHE,
        )
    }

    override suspend fun sendXmppReconnectRequest(context: Context): Boolean =
        RemoteWriteSupport.isSuccess(
            RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_XMPP_RECONNECT,
            ),
        )

    override suspend fun setXmppServer(context: Context, newHost: String) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_SET_XMPP_SERVER,
                argument = newHost,
            ),
            operation = ManagerProtocol.WRITE_OP_SET_XMPP_SERVER,
        )
    }

    override suspend fun getRuntimeEnvironmentSnapshot(context: Context): ManagerRuntimeEnvironmentSnapshot {
        return when (val result = client.getRuntimeEnvironmentSnapshot()) {
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeResult.Success ->
                ManagerRuntimeEnvironmentSnapshot(
                    isMiui = result.value.isMiui,
                    imei = result.value.imei,
                    macAddress = result.value.macAddress,
                    xmppServerHost = result.value.xmppServerHost,
                )
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeResult.Unsupported -> {
                logW("getRuntimeEnvironmentSnapshot unavailable status=unsupported")
                throw RuntimeReadUnavailableException("unsupported", "getRuntimeEnvironmentSnapshot")
            }
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeResult.Unavailable -> {
                logW("getRuntimeEnvironmentSnapshot unavailable status=${result.availability}")
                throw RuntimeReadUnavailableException(
                    result.availability.toString(),
                    "getRuntimeEnvironmentSnapshot",
                )
            }
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeResult.Failed -> {
                logW("getRuntimeEnvironmentSnapshot unavailable status=${result.reason}")
                throw RuntimeReadUnavailableException(result.reason, "getRuntimeEnvironmentSnapshot")
            }
        }
    }

    override suspend fun getConnectionSnapshot(): ManagerConnectionSnapshot {
        return when (val result = connectionSource.load()) {
            is ConnectionSnapshotSourceResult.Available -> result.snapshot
            is ConnectionSnapshotSourceResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("getConnectionSnapshot", result.status)
                throw RuntimeReadUnavailableException(
                    status = result.status.name,
                    operation = "getConnectionSnapshot",
                )
            }
        }
    }

    override fun observeNotificationEvent(packageName: String, action: String, source: String) = Unit

    override suspend fun setRuntimeLogRetentionDays(days: Int) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_SET_RUNTIME_LOG_RETENTION,
                intArgument = days.coerceAtLeast(1),
            ),
            operation = ManagerProtocol.WRITE_OP_SET_RUNTIME_LOG_RETENTION,
        )
    }

    override suspend fun applyEventRetentionDays(days: Int) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_APPLY_EVENT_RETENTION,
                intArgument = days.coerceAtLeast(1),
            ),
            operation = ManagerProtocol.WRITE_OP_APPLY_EVENT_RETENTION,
        )
    }

}
