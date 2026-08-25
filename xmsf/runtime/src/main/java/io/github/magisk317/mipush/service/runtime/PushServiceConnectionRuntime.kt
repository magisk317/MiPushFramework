package io.github.magisk317.mipush.service.runtime
import com.xiaomi.push.service.*

// Plan data classes are in vendor PushRuntimeModels.kt.
// This object provides xmsf-specific plan methods and delegates stock behavior to vendor.

object PushServiceConnectionRuntime {
    @JvmStatic
    fun planConnect(
        isConnecting: Boolean,
        isConnected: Boolean
    ): PushConnectionAttemptPlan {
        return when {
            isConnecting -> PushConnectionAttemptPlan(
                action = PushConnectionAttemptAction.SkipConnecting,
                eventAction = "connect_skip_connecting"
            )
            isConnected -> PushConnectionAttemptPlan(
                action = PushConnectionAttemptAction.SkipConnected,
                eventAction = "connect_skip_connected"
            )
            else -> PushConnectionAttemptPlan(
                action = PushConnectionAttemptAction.Connect,
                eventAction = "connect_start"
            )
        }
    }

    @JvmStatic
    fun planCheckAlive(
        isConnected: Boolean,
        hasNetwork: Boolean
    ): PushCheckAlivePlan {
        return when {
            !isConnected -> PushCheckAlivePlan(
                action = PushCheckAliveAction.ScheduleConnect,
                eventAction = "checkalive_schedule_connect"
            )
            hasNetwork -> PushCheckAlivePlan(
                action = PushCheckAliveAction.Ping,
                eventAction = "checkalive_ping"
            )
            else -> PushCheckAlivePlan(
                action = PushCheckAliveAction.DisconnectAndReconnect,
                eventAction = "checkalive_disconnect_reconnect"
            )
        }
    }

    // Delegates to vendor PushConnectionPlanFactory for stock behavior.
    // xmsf can override these by implementing IPushRuntimeObserver methods.

    @JvmStatic
    fun planReconnectionFailure(shouldFalldown: Boolean): PushReconnectionFailurePlan =
        PushConnectionPlanFactory.planReconnectionFailure(shouldFalldown)

    @JvmStatic
    fun planReconnectionSuccess(alarmAlive: Boolean, shouldFalldown: Boolean): PushReconnectionSuccessPlan =
        PushConnectionPlanFactory.planReconnectionSuccess(alarmAlive, shouldFalldown)

    @JvmStatic
    fun planConnectionClosed(shouldFalldown: Boolean, reason: Int = 0, error: Exception? = null): PushConnectionClosedPlan =
        PushConnectionPlanFactory.planConnectionClosed(shouldFalldown, reason, error)

    @JvmStatic
    fun planNetworkChanged(
        hasNetwork: Boolean,
        isNetworkDeferred: Boolean,
        isConnected: Boolean,
        isConnecting: Boolean,
        shouldResetOnWifi: Boolean,
        shouldCheckAlive: Boolean,
    ): PushNetworkChangedPlan =
        PushConnectionPlanFactory.planNetworkChanged(hasNetwork, isNetworkDeferred, isConnected, isConnecting, shouldResetOnWifi, shouldCheckAlive)

    @JvmStatic
    fun planScreenState(isScreenOn: Boolean, shouldFalldown: Boolean, alarmAlive: Boolean, isConnected: Boolean, isConnecting: Boolean): PushScreenStatePlan =
        PushConnectionPlanFactory.planScreenState(isScreenOn, shouldFalldown, alarmAlive, isConnected, isConnecting)

    @JvmStatic
    fun planTimer(shouldFalldown: Boolean, alarmAlive: Boolean, isConnected: Boolean, isConnecting: Boolean, shouldCheckAlive: Boolean): PushTimerPlan =
        PushConnectionPlanFactory.planTimer(shouldFalldown, alarmAlive, isConnected, isConnecting, shouldCheckAlive)

    @JvmStatic
    fun planClientChange(activeClientCount: Int, shouldUpdateAlarm: Boolean): PushClientChangePlan =
        PushConnectionPlanFactory.planClientChange(activeClientCount, shouldUpdateAlarm)

    @JvmStatic
    fun planPowerModeChanged(isExtremePowerMode: Boolean, isSuperPowerMode: Boolean, isConnected: Boolean): PushPowerModePlan =
        PushConnectionPlanFactory.planPowerModeChanged(isExtremePowerMode, isSuperPowerMode, isConnected)

    @JvmStatic
    fun planShouldReconnect(
        hasNetwork: Boolean,
        activeClientCount: Int,
        pushDisabled: Boolean,
        pushEnabled: Boolean,
        superPowerMode: Boolean,
        extremePowerMode: Boolean,
    ): PushShouldReconnectPlan = PushConnectionPlanFactory.planShouldReconnect(
        hasNetwork, activeClientCount, pushDisabled, pushEnabled, superPowerMode, extremePowerMode,
    )
}
