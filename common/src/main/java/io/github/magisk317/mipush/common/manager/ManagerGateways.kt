package io.github.magisk317.mipush.common.manager

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.mipush.utils.ConfigDocumentContent
import io.github.magisk317.mipush.utils.ConfigListItem
import io.github.magisk317.mipush.utils.LocalConfigSummary
import io.github.magisk317.mipush.utils.RemoteConfigFile
import java.io.File

interface ManagerApplicationGateway {
    fun loadApplications(context: Context, query: String = "", filterMode: Int = 0): ManagerApplications
    fun getApplication(context: Context, packageName: String, ignoreNotRegistered: Boolean = false): ManagerApplication?
    fun updateApplication(application: ManagerApplication)
    fun updateAllNotificationOnRegister(enabled: Boolean): Int
    fun getDiagnostics(packageName: String, registeredType: Int): ManagerApplicationDiagnostics
    fun loadIntegrationTypeReason(context: Context, packageName: String): String
    suspend fun launchTargetAppAndForceRegister(context: Context, packageName: String, registeredType: Int): String
    suspend fun isNotificationOnRegisterEnabled(): Boolean
}

interface ManagerNotificationGateway {
    val isHooked: Boolean
    fun getNotificationChannels(packageName: String): List<NotificationChannel>
    fun getNotificationChannelGroups(packageName: String): List<NotificationChannelGroup>
    fun deleteNotificationChannel(packageName: String, channelId: String)
    fun isNotificationChannelEnabled(channel: NotificationChannel): Boolean
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
    suspend fun importDocuments(treeUri: Uri, uris: List<Uri>): Int
    suspend fun saveLocal(treeUri: Uri, path: String, content: String): LocalConfigSummary
    suspend fun resetToRemote(treeUri: Uri, path: String): LocalConfigSummary
    suspend fun openForPackage(packageName: String)
}

interface ManagerEventGateway {
    fun getEventsById(lastId: Long?, size: Int, packageName: String, query: String): List<ManagerEvent>
    fun startManagePermissions(packageName: String, ignoreNotRegistered: Boolean = false)
    suspend fun startConfigPreview(packageName: String)
    fun copyToClipboard(content: String)
    fun mockMessage(event: ManagerEvent): Boolean
    fun getJson(event: ManagerEvent): String?
    fun getContent(event: ManagerEvent): String
    suspend fun deleteEvent(event: ManagerEvent): Boolean
    suspend fun restoreEvent(event: ManagerEvent): ManagerEvent?
}

interface ManagerLogGateway {
    fun setRetentionDays(days: Int)
    fun summarizeFiles(context: Context): ManagerRuntimeLogFileSummary
    fun readLogFile(context: Context, fileName: String): ManagerRuntimeLogFileContent?
    fun buildLogBundle(context: Context): ManagerLogExportResult
    fun buildShareIntent(context: Context, file: File): Intent
    fun clearLogFolders(context: Context): ManagerLogClearResult
}

interface ManagerPermissionGateway {
    fun hasCachedRootAccess(): Boolean
    fun refreshRootAccessIfGranted(): Boolean
    fun requestRootAccess(): Boolean
    fun launchAppOps(context: Context, permission: String, tips: CharSequence): Boolean
    fun isUsageStatsAllowedByRoot(packageName: String): Boolean
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean
    fun grantNotificationPermission(context: Context): Boolean
}
