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
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.xposed.logging.MagiskOtel

class RemoteManagerPermissionGateway(
    private val context: Context,
    private val client: ManagerRuntimeClient,
    private val managerRootAccess: ManagerRootAccess,
) : ManagerPermissionGateway {
    @Volatile
    private var runtimeRootState: ManagerRootAccessState = ManagerRootAccessState.UNAVAILABLE

    override suspend fun getRootAccessSnapshot(refresh: Boolean): ManagerRootAccessSnapshot {
        val managerGranted = if (refresh) {
            managerRootAccess.refreshRootAccessIfGranted()
        } else {
            managerRootAccess.cachedGrantState()
        }
        val runtimeState = if (refresh) queryRootState(requestAuthorization = false) else runtimeRootState
        return rootSnapshot(
            managerState = managerGranted.toRootAccessState(),
            runtimeState = runtimeState,
        )
    }

    override suspend fun requestRootAccess(target: ManagerRootTarget): ManagerRootAccessSnapshot {
        val managerState = when (target) {
            ManagerRootTarget.MANAGER -> managerRootAccess.requestRootAccess()
            ManagerRootTarget.RUNTIME -> managerRootAccess.refreshRootAccessIfGranted()
        }.toRootAccessState()
        val runtimeState = queryRootState(
            requestAuthorization = target == ManagerRootTarget.RUNTIME,
        )
        return rootSnapshot(managerState = managerState, runtimeState = runtimeState)
    }

    override suspend fun hasCachedRootAccess(): Boolean = runtimeRootState == ManagerRootAccessState.GRANTED

    override suspend fun refreshRootAccessIfGranted(): Boolean {
        return queryRootState(requestAuthorization = false) == ManagerRootAccessState.GRANTED
    }

    override suspend fun requestRootAccess(): Boolean {
        return queryRootState(requestAuthorization = true) == ManagerRootAccessState.GRANTED
    }

    override suspend fun repairXSpaceUserSupport(): ManagerXSpaceRepairResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_REPAIR_XSPACE,
        ) ?: return ManagerXSpaceRepairResult(
            stage = ManagerXSpaceRepairStage.PARTIAL_FAILED,
            details = "runtime_write_unavailable",
        )
        val stage = when (result.details) {
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_COMPLETED -> ManagerXSpaceRepairStage.COMPLETED
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_ROOT_MISSING -> ManagerXSpaceRepairStage.ROOT_MISSING
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_PRIMARY_USER_REQUIRED ->
                ManagerXSpaceRepairStage.PRIMARY_USER_REQUIRED
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_XSPACE_MISSING -> ManagerXSpaceRepairStage.XSPACE_USER_NOT_FOUND
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_PARTIAL_FAILED -> ManagerXSpaceRepairStage.PARTIAL_FAILED
            else -> if (RemoteWriteSupport.isSuccess(result)) {
                ManagerXSpaceRepairStage.COMPLETED
            } else {
                ManagerXSpaceRepairStage.PARTIAL_FAILED
            }
        }
        return ManagerXSpaceRepairResult(
            stage = stage,
            xmsfInstalled = result.resultLong == 1L,
            details = result.details,
        )
    }

    override suspend fun setDualAppEnabled(enabled: Boolean): ManagerXSpaceRepairResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_SET_DUAL_APP,
            booleanArgument = enabled,
        ) ?: return ManagerXSpaceRepairResult(
            stage = ManagerXSpaceRepairStage.PARTIAL_FAILED,
            details = "runtime_write_unavailable",
        )
        val stage = when (result.details) {
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_COMPLETED -> ManagerXSpaceRepairStage.COMPLETED
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_ROOT_MISSING -> ManagerXSpaceRepairStage.ROOT_MISSING
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_PRIMARY_USER_REQUIRED ->
                ManagerXSpaceRepairStage.PRIMARY_USER_REQUIRED
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_XSPACE_MISSING -> ManagerXSpaceRepairStage.XSPACE_USER_NOT_FOUND
            ManagerProtocol.WRITE_DETAIL_DUAL_APP_PARTIAL_FAILED -> ManagerXSpaceRepairStage.PARTIAL_FAILED
            else -> if (RemoteWriteSupport.isSuccess(result)) {
                ManagerXSpaceRepairStage.COMPLETED
            } else {
                ManagerXSpaceRepairStage.PARTIAL_FAILED
            }
        }
        // Dual-app enable already grants silent perms on runtime; re-assert from manager as well.
        if (stage == ManagerXSpaceRepairStage.COMPLETED && enabled) {
            grantSilentPermissions(userId = ManagerProtocol.GRANT_USER_AUTO, packageName = "", op = "all")
        }
        return ManagerXSpaceRepairResult(stage = stage, details = result.details)
    }

    override suspend fun getDualAppInstallation(): ManagerDualAppInstallationResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_QUERY_DUAL_APP,
        ) ?: return ManagerDualAppInstallationResult.Unavailable("runtime_unavailable")
        if (!RemoteWriteSupport.isSuccess(result)) {
            return ManagerDualAppInstallationResult.Unavailable(
                result.details.ifBlank { result.status },
            )
        }
        return if (result.details == ManagerProtocol.WRITE_DETAIL_DUAL_APP_INSTALLED || result.resultLong == 1L) {
            ManagerDualAppInstallationResult.Installed
        } else {
            ManagerDualAppInstallationResult.NotInstalled
        }
    }

    override suspend fun launchAppOps(context: Context, permission: String, tips: CharSequence): Boolean {
        // Grant the requested appop for both packages, primary + dual-space.
        return grantSilentPermissions(
            userId = ManagerProtocol.GRANT_USER_AUTO,
            packageName = "",
            op = permission,
        )
    }

    override suspend fun isUsageStatsAllowedByRoot(packageName: String): Boolean {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_QUERY_USAGE_STATS,
            packageName = packageName,
        )
        return resolveUsageStatsAllowed(result)
    }

    override suspend fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        return grantSilentPermissions(
            userId = ManagerProtocol.GRANT_USER_AUTO,
            packageName = "",
            op = "deviceidle",
        )
    }

    override suspend fun grantNotificationPermission(context: Context): Boolean {
        return grantSilentPermissions(
            userId = ManagerProtocol.GRANT_USER_AUTO,
            packageName = "",
            op = "all",
        )
    }

    private suspend fun queryRootState(requestAuthorization: Boolean): ManagerRootAccessState {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_QUERY_ROOT,
            booleanArgument = requestAuthorization,
        )
        return resolveRuntimeRootAccessState(result).also { runtimeRootState = it }
    }

    private fun rootSnapshot(
        managerState: ManagerRootAccessState,
        runtimeState: ManagerRootAccessState,
    ): ManagerRootAccessSnapshot {
        val userId = Process.myUid() / PER_USER_RANGE
        return ManagerRootAccessSnapshot(
            userId = userId,
            manager = ManagerRootSubjectStatus(
                target = ManagerRootTarget.MANAGER,
                packageName = ManagerProtocol.MANAGER_PACKAGE,
                userId = userId,
                uid = Process.myUid(),
                state = managerState,
            ),
            runtime = ManagerRootSubjectStatus(
                target = ManagerRootTarget.RUNTIME,
                packageName = ManagerProtocol.RUNTIME_PACKAGE,
                userId = userId,
                uid = context.packageUidOrNull(ManagerProtocol.RUNTIME_PACKAGE),
                state = runtimeState,
            ),
        )
    }

    private suspend fun grantSilentPermissions(userId: Int, packageName: String, op: String): Boolean {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_GRANT_SILENT_PERMISSIONS,
            packageName = packageName,
            intArgument = userId,
            argument = op,
        ) ?: return false
        return RemoteWriteSupport.isSuccess(result) ||
            result.details == ManagerProtocol.WRITE_DETAIL_GRANT_SILENT_OK
    }
}

internal fun resolveRuntimeRootAccessState(result: ManagerWriteResultDto?): ManagerRootAccessState {
    if (result == null) return ManagerRootAccessState.UNAVAILABLE
    if (result.resultLong == 1L || result.details == ManagerProtocol.WRITE_DETAIL_ROOT_AVAILABLE) {
        return ManagerRootAccessState.GRANTED
    }
    if (RemoteWriteSupport.isSuccess(result) && result.details == ManagerProtocol.WRITE_DETAIL_ROOT_MISSING) {
        return ManagerRootAccessState.NOT_GRANTED
    }
    return ManagerRootAccessState.UNAVAILABLE
}

internal fun resolveUsageStatsAllowed(result: ManagerWriteResultDto?): Boolean =
    RemoteWriteSupport.isSuccess(result) && result?.resultLong == 1L

private fun Boolean?.toRootAccessState(): ManagerRootAccessState = when (this) {
    true -> ManagerRootAccessState.GRANTED
    false -> ManagerRootAccessState.NOT_GRANTED
    null -> ManagerRootAccessState.UNAVAILABLE
}

private fun Context.packageUidOrNull(packageName: String): Int? = runCatching {
    packageManager.getPackageUid(packageName, 0)
}.getOrNull()

private const val PER_USER_RANGE = 100_000
