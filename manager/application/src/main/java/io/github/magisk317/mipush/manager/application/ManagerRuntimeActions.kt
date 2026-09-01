package io.github.magisk317.mipush.manager.application

import android.content.Context
import android.net.Uri

interface ManagerConfigGateway {
    suspend fun getXmppServer(): String?
    suspend fun setXmppServer(host: String): Boolean
    suspend fun getConfigurationDirectory(): Uri?
    suspend fun setConfigurationDirectory(uri: Uri): Boolean
    suspend fun loadConfigurations(context: Context)
}

interface ManagerRuntimeActions {
    suspend fun clearHistory()
    suspend fun startMiPushServiceAsForegroundService(context: Context)
    suspend fun resetTopActivityCache()
    suspend fun sendXmppReconnectRequest(context: Context): Boolean
    suspend fun setXmppServer(context: Context, newHost: String)
    suspend fun getRuntimeEnvironmentSnapshot(context: Context): ManagerRuntimeEnvironmentSnapshot
    suspend fun getConnectionSnapshot(): ManagerConnectionSnapshot
    fun observeNotificationEvent(packageName: String, action: String, source: String)
    suspend fun setRuntimeLogRetentionDays(days: Int)

    /**
     * 应用事件记录的保留天数并立即触发一次清理。
     * 保留天数本身持久化在 DataStore(见 PreferenceRepository.eventRetentionDays),
     * 这里负责把新值即时反映到清理器并跑一次,避免用户改小后要等下次入库才生效。
     */
    suspend fun applyEventRetentionDays(days: Int)
}
