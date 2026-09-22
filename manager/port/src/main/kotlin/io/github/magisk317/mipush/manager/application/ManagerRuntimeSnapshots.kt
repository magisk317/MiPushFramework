package io.github.magisk317.mipush.manager.application

/** Runtime environment values shared by Android hosts without Android API ownership. */
data class ManagerRuntimeEnvironmentSnapshot(
    val isMiui: Int,
    val imei: String?,
    val macAddress: String?,
    val xmppServerHost: String,
)

/** Connection and health values shared by manager hosts and the XMSF runtime adapter. */
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
    /**
     * True when the Xposed notification takeover hooks are installed in the runtime
     * (`NotificationManagerEx.isHooked`). This is the manager-visible "module active" signal.
     */
    val moduleHooked: Boolean = false,
    /** True when the notification identity bridge is hooked (`NotificationIdentityBridge.isHooked`). */
    val identityHooked: Boolean = false,
)
