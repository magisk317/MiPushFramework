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
    val frameworkRegistered: Boolean = false,
    val timerClassName: String? = null,
    val exactAlarmAvailable: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = false,
    val deviceIdle: Boolean = false,
    val lastHealthCycleAtMs: Long = 0L,
    val lastHealthCycleAction: String? = null,
    val alarmAlive: Boolean = false,
    val alarmMode: String? = null,
    val alarmFallbackReason: String? = null,
    val alarmRegisteredAtMs: Long = 0L,
    val nextTimerAtMs: Long = 0L,
    val lastTimerCallbackAtMs: Long = 0L,
    val lastTimerCallbackDelayMs: Long = 0L,
    val deviceIdleWhitelistXmsf: Boolean = false,
    val checkedPackageName: String = "com.xiaomi.xmsf",
    val lastPingSentAtMs: Long = 0L,
    val lastReadAliveAtMs: Long = 0L,
    val lastPingTimeoutAtMs: Long = 0L,
    val lastDisconnectReason: Int? = null,
    val lastReconnectStartedAtMs: Long = 0L,
    val lastReconnectConnectedAtMs: Long = 0L,
    val lastReconnectLatencyMs: Long = 0L,
    val lastDisconnectToReconnectLatencyMs: Long = 0L,
    val lastReconnectToConnectedLatencyMs: Long = 0L,
)

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
