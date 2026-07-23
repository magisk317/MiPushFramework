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

    /**
     * 应用事件记录的保留天数并立即触发一次清理。
     * 保留天数本身持久化在 DataStore(见 PreferenceRepository.eventRetentionDays),
     * 这里负责把新值即时反映到清理器并跑一次,避免用户改小后要等下次入库才生效。
     */
    fun applyEventRetentionDays(days: Int)
}
