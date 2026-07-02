package io.github.magisk317.mipush.common.manager

import android.content.Context
import android.net.Uri

interface ManagerConfigGateway {
    suspend fun getXmppServer(): String?
    suspend fun getConfigurationDirectory(): Uri?
    suspend fun setConfigurationDirectory(uri: Uri): Boolean
    fun loadConfigurations(context: Context)
}

data class ManagerRuntimeEnvironmentSnapshot(
    val isMiui: Int,
    val imei: String?,
    val macAddress: String?,
    val xmppServerHost: String,
)

data class ManagerConnectionSnapshot(
    val connectionState: String,
    val connectedAtMs: Long,
    val lastDisconnectedAtMs: Long,
    val connectionSessionCount: Long,
    val serverHost: String?,
    val serverIp: String?,
    val keepAliveIntervalMs: Int,
    val pingIntervalMs: Int,
    val downstreamMessageCount: Long,
    val deliveredToAppCount: Long,
    val duplicateMessageCount: Long,
    val ackMessageCount: Long,
    val registeredPackageCount: Int,
    val trackedChannelCount: Int,
    val boundChannelCount: Int,
)

interface ManagerRuntimeActions {
    suspend fun clearHistory()
    fun startMiPushServiceAsForegroundService(context: Context)
    fun resetTopActivityCache()
    fun sendXmppReconnectRequest(context: Context)
    fun setXmppServer(context: Context, newHost: String)
    fun getXmppServerHint(): String
    fun getRuntimeEnvironmentSnapshot(context: Context): ManagerRuntimeEnvironmentSnapshot
    fun getConnectionSnapshot(): ManagerConnectionSnapshot
    fun observeNotificationEvent(packageName: String, action: String, source: String)
    fun setRuntimeLogRetentionDays(days: Int)
}
