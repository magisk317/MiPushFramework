package io.github.magisk317.mipush.service.runtime

import com.xiaomi.push.service.*

// Connection plan models and decisions live in runtime-core commonMain.
// This facade preserves the existing Android/JVM API and maps vendor-only failure reasons.
object PushServiceConnectionRuntime {
    @JvmStatic
    fun planConnect(
        isConnecting: Boolean,
        isConnected: Boolean,
    ): PushConnectionAttemptPlan =
        PushConnectionPlanFactory.planConnect(isConnecting, isConnected)

    @JvmStatic
    fun planCheckAlive(
        isConnected: Boolean,
        hasNetwork: Boolean,
    ): PushCheckAlivePlan =
        PushConnectionPlanFactory.planCheckAlive(isConnected, hasNetwork)

    @JvmStatic
    fun planReconnectionFailure(shouldFalldown: Boolean): PushReconnectionFailurePlan =
        PushConnectionPlanFactory.planReconnectionFailure(shouldFalldown)

    @JvmStatic
    fun planReconnectionSuccess(
        alarmAlive: Boolean,
        shouldFalldown: Boolean,
    ): PushReconnectionSuccessPlan =
        PushConnectionPlanFactory.planReconnectionSuccess(alarmAlive, shouldFalldown)

    @JvmStatic
    fun planConnectionClosed(
        shouldFalldown: Boolean,
        reason: Int = 0,
        error: Exception? = null,
    ): PushConnectionClosedPlan = PushConnectionPlanFactory.planConnectionClosed(
        shouldFalldown = shouldFalldown,
        failedConnection = error != null ||
            reason == PushConstants.ERROR_READ_ERROR ||
            reason == PushConstants.ERROR_PING_TIMEOUT,
    )

    @JvmStatic
    fun planNetworkChanged(
        hasNetwork: Boolean,
        isNetworkDeferred: Boolean,
        isConnected: Boolean,
        isConnecting: Boolean,
        shouldResetOnWifi: Boolean,
        shouldCheckAlive: Boolean,
    ): PushNetworkChangedPlan = PushConnectionPlanFactory.planNetworkChanged(
        hasNetwork,
        isNetworkDeferred,
        isConnected,
        isConnecting,
        shouldResetOnWifi,
        shouldCheckAlive,
    )

    @JvmStatic
    fun planScreenState(
        isScreenOn: Boolean,
        shouldFalldown: Boolean,
        alarmAlive: Boolean,
        isConnected: Boolean,
        isConnecting: Boolean,
    ): PushScreenStatePlan = PushConnectionPlanFactory.planScreenState(
        isScreenOn,
        shouldFalldown,
        alarmAlive,
        isConnected,
        isConnecting,
    )

    @JvmStatic
    fun planTimer(
        shouldFalldown: Boolean,
        alarmAlive: Boolean,
        isConnected: Boolean,
        isConnecting: Boolean,
        shouldCheckAlive: Boolean,
    ): PushTimerPlan = PushConnectionPlanFactory.planTimer(
        shouldFalldown,
        alarmAlive,
        isConnected,
        isConnecting,
        shouldCheckAlive,
    )

    @JvmStatic
    fun planClientChange(
        activeClientCount: Int,
        shouldUpdateAlarm: Boolean,
    ): PushClientChangePlan =
        PushConnectionPlanFactory.planClientChange(activeClientCount, shouldUpdateAlarm)

    @JvmStatic
    fun planPowerModeChanged(
        isExtremePowerMode: Boolean,
        isSuperPowerMode: Boolean,
        isConnected: Boolean,
    ): PushPowerModePlan = PushConnectionPlanFactory.planPowerModeChanged(
        isExtremePowerMode,
        isSuperPowerMode,
        isConnected,
    )

    @JvmStatic
    fun planShouldReconnect(
        hasNetwork: Boolean,
        activeClientCount: Int,
        pushDisabled: Boolean,
        pushEnabled: Boolean,
        superPowerMode: Boolean,
        extremePowerMode: Boolean,
    ): PushShouldReconnectPlan = PushConnectionPlanFactory.planShouldReconnect(
        hasNetwork,
        activeClientCount,
        pushDisabled,
        pushEnabled,
        superPowerMode,
        extremePowerMode,
    )
}
