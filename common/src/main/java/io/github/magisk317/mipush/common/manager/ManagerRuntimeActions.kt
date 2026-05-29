package io.github.magisk317.mipush.common.manager

import android.content.Context
import android.net.Uri
import io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind

interface ManagerConfigGateway {
    suspend fun getXmppServer(): String?
    suspend fun getConfigurationDirectory(): Uri?
    suspend fun setConfigurationDirectory(uri: Uri): Boolean
    fun loadConfigurations(context: Context)
}

interface ManagerRuntimeActions {
    suspend fun clearHistory()
    fun clearLog(context: Context)
    fun startMiPushServiceAsForegroundService(context: Context)
    fun notifyMockNotification(context: Context, kind: MockNotificationKind, packageName: String)
    fun tryForceRegisterAllApplications(context: Context, packageNames: Collection<String>): ForceRegisterOutcome
    fun resetTopActivityCache()
    fun sendXmppReconnectRequest(context: Context)
    fun setXmppServer(context: Context, newHost: String)
    fun getXmppServerHint(): String
    fun observeNotificationEvent(packageName: String, action: String, source: String)
    fun setRuntimeLogRetentionDays(days: Int)
}

enum class ForceRegisterStage {
    ROOT_MISSING,
    ALL_FAILED,
    COMPLETED,
}

data class ForceRegisterOutcome(
    val stage: ForceRegisterStage,
    val successCount: Int,
    val failedCount: Int,
    val unsupportedCount: Int,
) {
    val nonSuccessCount: Int
        get() = failedCount + unsupportedCount
}
