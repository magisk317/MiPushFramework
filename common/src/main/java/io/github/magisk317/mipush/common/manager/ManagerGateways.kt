package io.github.magisk317.mipush.common.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.mipush.utils.ConfigDocumentContent
import io.github.magisk317.mipush.utils.ConfigListItem
import io.github.magisk317.mipush.utils.LocalConfigSummary
import io.github.magisk317.mipush.utils.RemoteConfigFile
import io.github.magisk317.mipush.common.fakedevice.ZygiskConfig
import io.github.magisk317.mipush.common.notification.MockReplayOutcome
import java.io.File

interface ManagerApplicationGateway {
    suspend fun loadApplications(context: Context, query: String = "", filterMode: Int = 0, includeSystemApps: Boolean = false): ManagerApplications
    suspend fun getApplication(context: Context, packageName: String, ignoreNotRegistered: Boolean = false): ManagerApplication?
    suspend fun updateApplication(application: ManagerApplication)
    suspend fun getDiagnostics(packageName: String, registeredType: Int): ManagerApplicationDiagnostics
    suspend fun launchTargetAppAndForceRegister(
        context: Context,
        packageName: String,
        registeredType: Int,
    ): ManagerForceRegisterResult
}

data class ManagerForceRegisterResult(
    val succeeded: Boolean,
    val message: String,
)

interface ManagerNotificationChannelCommandGateway {
    fun deleteNotificationChannel(packageName: String, channelId: String): Boolean
}

data class ManagerConfigListSnapshot(
    val items: List<ConfigListItem> = emptyList(),
    val remoteError: String? = null,
)

data class ManagerConfigEditorSnapshot(
    val path: String,
    val local: ConfigDocumentContent? = null,
    val remote: ConfigDocumentContent? = null,
    val remoteMeta: RemoteConfigFile? = null,
    val localMeta: LocalConfigSummary? = null,
    val remoteError: String? = null,
)

interface ManagerConfigSyncGateway {
    suspend fun loadLocalSnapshot(treeUri: Uri?): ManagerConfigListSnapshot
    suspend fun loadRemoteSnapshot(treeUri: Uri?): ManagerConfigListSnapshot
    suspend fun readLocalEditorSnapshot(treeUri: Uri?, path: String): ManagerConfigEditorSnapshot
    suspend fun readRemoteEditorSnapshot(treeUri: Uri?, path: String): ManagerConfigEditorSnapshot
    suspend fun pullAll(treeUri: Uri, onProgress: ((current: Int, total: Int, path: String) -> Unit)? = null): Int
    suspend fun importDocuments(treeUri: Uri, uris: List<Uri>, isIcon: Boolean = false): Int
    suspend fun saveLocal(treeUri: Uri, path: String, content: String): LocalConfigSummary
    suspend fun resetToRemote(treeUri: Uri, path: String): LocalConfigSummary
    suspend fun openForPackage(packageName: String)
}

interface ManagerEventGateway {
    suspend fun getEventsById(lastId: Long?, size: Int, packageName: String, query: String): List<ManagerEvent>
    fun startManagePermissions(packageName: String, ignoreNotRegistered: Boolean = false)
    suspend fun startConfigPreview(packageName: String)
    fun copyToClipboard(content: String)
    suspend fun mockMessage(event: ManagerEvent): MockReplayOutcome
    suspend fun getJson(event: ManagerEvent): String?
    suspend fun getContent(event: ManagerEvent): String?
    suspend fun deleteEvent(event: ManagerEvent): Boolean
    suspend fun restoreEvent(event: ManagerEvent): ManagerEvent?

    /** 按本地日历日聚合可清理事件条数(排除注册态),供日历清理界面高亮与计数。 */
    suspend fun countEventsByDay(): List<ManagerDayCount>

    /** 清理某个时间点之前的可清理事件(排除注册态),供"清理此日期及之前"使用;返回删除条数。 */
    suspend fun clearHistoryBefore(cutoffMillis: Long): Int

    /** 清理某个时间区间 [start, end) 内的可清理事件(排除注册态),供"仅清理当天"使用;返回删除条数。 */
    suspend fun clearHistoryInRange(startMillis: Long, endMillis: Long): Int
}

/** 单个本地日历日的可清理事件计数;day 形如 "2026-07-13"(设备时区)。 */
data class ManagerDayCount(
    val day: String,
    val count: Int,
)

interface ManagerLogGateway {
    suspend fun setRetentionDays(days: Int)
    suspend fun buildLogBundle(context: Context): ManagerLogExportResult
    fun buildShareIntent(context: Context, file: File): Intent
    suspend fun clearLogFolders(context: Context): ManagerLogClearResult
}

interface ManagerPermissionGateway {
    suspend fun getRootAccessSnapshot(refresh: Boolean = false): ManagerRootAccessSnapshot
    suspend fun requestRootAccess(target: ManagerRootTarget): ManagerRootAccessSnapshot
    suspend fun hasCachedRootAccess(): Boolean
    suspend fun refreshRootAccessIfGranted(): Boolean
    suspend fun requestRootAccess(): Boolean
    suspend fun repairXSpaceUserSupport(): ManagerXSpaceRepairResult
    suspend fun setDualAppEnabled(enabled: Boolean): ManagerXSpaceRepairResult
    suspend fun getDualAppInstallation(): ManagerDualAppInstallationResult
    suspend fun launchAppOps(context: Context, permission: String, tips: CharSequence): Boolean
    suspend fun isUsageStatsAllowedByRoot(packageName: String): Boolean
    suspend fun requestIgnoreBatteryOptimizations(context: Context): Boolean
    suspend fun grantNotificationPermission(context: Context): Boolean
}

sealed interface ManagerDualAppInstallationResult {
    data object Installed : ManagerDualAppInstallationResult
    data object NotInstalled : ManagerDualAppInstallationResult
    data class Unavailable(val reason: String) : ManagerDualAppInstallationResult
}

enum class ManagerRootTarget {
    MANAGER,
    RUNTIME,
}

enum class ManagerRootAccessState {
    GRANTED,
    NOT_GRANTED,
    UNAVAILABLE,
}

data class ManagerRootSubjectStatus(
    val target: ManagerRootTarget,
    val packageName: String,
    val userId: Int,
    val uid: Int? = null,
    val state: ManagerRootAccessState = ManagerRootAccessState.UNAVAILABLE,
) {
    val isGranted: Boolean
        get() = state == ManagerRootAccessState.GRANTED
}

data class ManagerRootAccessSnapshot(
    val userId: Int,
    val manager: ManagerRootSubjectStatus,
    val runtime: ManagerRootSubjectStatus,
) {
    fun status(target: ManagerRootTarget): ManagerRootSubjectStatus = when (target) {
        ManagerRootTarget.MANAGER -> manager
        ManagerRootTarget.RUNTIME -> runtime
    }
}

enum class ManagerXSpaceRepairStage {
    ROOT_MISSING,
    PRIMARY_USER_REQUIRED,
    XSPACE_USER_NOT_FOUND,
    COMPLETED,
    PARTIAL_FAILED,
}

data class ManagerXSpaceRepairResult(
    val stage: ManagerXSpaceRepairStage,
    val xmsfInstalled: Boolean = false,
    val documentsUiAvailable: Boolean = false,
    val details: String = "",
)

sealed interface ZygiskConfigReadResult {
    data class Available(val config: io.github.magisk317.mipush.common.fakedevice.ZygiskConfig) : ZygiskConfigReadResult

    data class Unavailable(val reason: String) : ZygiskConfigReadResult
}

sealed interface ZygiskModuleReadResult {
    data class Available(val enabled: Boolean) : ZygiskModuleReadResult

    data class Unavailable(val reason: String) : ZygiskModuleReadResult
}

sealed interface ZygiskPackageScanResult {
    data class Available(val output: String) : ZygiskPackageScanResult

    data class Unavailable(val reason: String) : ZygiskPackageScanResult
}

interface ZygiskConfigGateway {
    suspend fun isZygiskModuleEnabled(): ZygiskModuleReadResult
    fun getZygiskConfigPath(): String
    suspend fun getZygiskConfig(): ZygiskConfigReadResult
    suspend fun saveZygiskConfig(config: ZygiskConfig): Boolean
    suspend fun forceStopApp(packageName: String): Boolean
    suspend fun scanZygiskPackages(): ZygiskPackageScanResult
}
