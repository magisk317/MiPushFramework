package io.github.magisk317.mipush.runtime.core

enum class PushConnectionAttemptAction {
    Connect,
    SkipConnecting,
    SkipConnected,
}

data class PushConnectionAttemptPlan(
    val action: PushConnectionAttemptAction,
    val eventAction: String,
)

enum class PushCheckAliveAction {
    Ignore,
    Ping,
    ScheduleConnect,
    DisconnectAndReconnect,
    ResetConnection,
}

data class PushCheckAlivePlan(
    val action: PushCheckAliveAction,
    val eventAction: String,
)

data class PushNetworkChangedPlan(
    val shouldResetConnection: Boolean,
    val shouldConnect: Boolean,
    val shouldDisconnect: Boolean,
    val shouldCheckAlive: Boolean,
    val shouldUpdateAlarm: Boolean,
    val eventAction: String,
)

data class PushScreenStatePlan(
    val shouldStopAlarm: Boolean,
    val shouldUpdateAlarm: Boolean,
    val shouldConnect: Boolean,
    val eventAction: String,
)

data class PushTimerPlan(
    val shouldStopAlarm: Boolean,
    val shouldRegisterPing: Boolean,
    val shouldConnect: Boolean,
    val shouldCheckAlive: Boolean,
    val eventAction: String,
)

data class PushClientChangePlan(
    val shouldUpdateAlarm: Boolean,
    val shouldDisconnect: Boolean,
    val eventAction: String,
)

data class PushPowerModePlan(
    val shouldDisconnect: Boolean,
    val shouldConnect: Boolean,
    val shouldUpdateAlarm: Boolean,
    val disconnectReason: Int,
    val eventAction: String,
)

data class PushShouldReconnectPlan(
    val shouldReconnect: Boolean,
    val eventAction: String,
)

data class PushReconnectionFailurePlan(
    val shouldBroadcastUnavailable: Boolean,
    val shouldScheduleReconnect: Boolean,
    val eventAction: String,
)

data class PushReconnectionSuccessPlan(
    val shouldBroadcastAvailable: Boolean,
    val shouldResetReconnectState: Boolean,
    val shouldRegisterAlarm: Boolean,
    val shouldBindAllClients: Boolean,
    val eventAction: String,
)

data class PushConnectionClosedPlan(
    val shouldScheduleReconnect: Boolean,
    val eventAction: String,
)

/** Platform-neutral connection lifecycle decisions. Platform adapters execute the returned plans. */
object PushConnectionPlanFactory {
    fun planConnect(
        isConnecting: Boolean,
        isConnected: Boolean,
    ): PushConnectionAttemptPlan = when {
        isConnecting -> PushConnectionAttemptPlan(
            action = PushConnectionAttemptAction.SkipConnecting,
            eventAction = "connect_skip_connecting",
        )
        isConnected -> PushConnectionAttemptPlan(
            action = PushConnectionAttemptAction.SkipConnected,
            eventAction = "connect_skip_connected",
        )
        else -> PushConnectionAttemptPlan(
            action = PushConnectionAttemptAction.Connect,
            eventAction = "connect_start",
        )
    }

    fun planCheckAlive(
        isConnected: Boolean,
        hasNetwork: Boolean,
    ): PushCheckAlivePlan = when {
        !isConnected -> PushCheckAlivePlan(
            action = PushCheckAliveAction.ScheduleConnect,
            eventAction = "checkalive_schedule_connect",
        )
        hasNetwork -> PushCheckAlivePlan(
            action = PushCheckAliveAction.Ping,
            eventAction = "checkalive_ping",
        )
        else -> PushCheckAlivePlan(
            action = PushCheckAliveAction.DisconnectAndReconnect,
            eventAction = "checkalive_disconnect_reconnect",
        )
    }

    fun planShouldReconnect(
        hasNetwork: Boolean,
        activeClientCount: Int,
        pushDisabled: Boolean,
        pushEnabled: Boolean,
        superPowerMode: Boolean,
        extremePowerMode: Boolean,
    ): PushShouldReconnectPlan {
        val shouldReconnect = hasNetwork && activeClientCount > 0 &&
            !pushDisabled && pushEnabled && !superPowerMode && !extremePowerMode
        return PushShouldReconnectPlan(
            shouldReconnect = shouldReconnect,
            eventAction = if (shouldReconnect) "should_reconnect" else "should_not_reconnect",
        )
    }

    fun planNetworkChanged(
        hasNetwork: Boolean,
        isNetworkDeferred: Boolean,
        isConnected: Boolean,
        isConnecting: Boolean,
        shouldResetOnWifi: Boolean,
        shouldCheckAlive: Boolean,
    ): PushNetworkChangedPlan {
        if (isNetworkDeferred) {
            return PushNetworkChangedPlan(
                shouldResetConnection = false,
                shouldConnect = false,
                shouldDisconnect = false,
                shouldCheckAlive = false,
                shouldUpdateAlarm = false,
                eventAction = "network_changed_deferred",
            )
        }
        if (hasNetwork) {
            val resetConnection = shouldResetOnWifi
            val checkAlive = !resetConnection && isConnected && shouldCheckAlive
            val connect = !isConnected && !isConnecting
            return PushNetworkChangedPlan(
                shouldResetConnection = resetConnection,
                shouldConnect = connect,
                shouldDisconnect = false,
                shouldCheckAlive = checkAlive,
                shouldUpdateAlarm = true,
                eventAction = when {
                    resetConnection -> "network_changed_wifi_reset"
                    checkAlive -> "network_changed_check_alive"
                    connect -> "network_changed_connect"
                    else -> "network_changed_noop"
                },
            )
        }
        return PushNetworkChangedPlan(
            shouldResetConnection = false,
            shouldConnect = false,
            shouldDisconnect = true,
            shouldCheckAlive = false,
            shouldUpdateAlarm = true,
            eventAction = "network_changed_disconnect",
        )
    }

    fun planScreenState(
        isScreenOn: Boolean,
        shouldFalldown: Boolean,
        alarmAlive: Boolean,
        isConnected: Boolean,
        isConnecting: Boolean,
    ): PushScreenStatePlan {
        if (!isScreenOn) {
            val stopAlarm = shouldFalldown && alarmAlive
            return PushScreenStatePlan(
                shouldStopAlarm = stopAlarm,
                shouldUpdateAlarm = false,
                shouldConnect = false,
                eventAction = if (stopAlarm) "screen_off_falldown_stop" else "screen_off_noop",
            )
        }
        if (shouldFalldown) {
            return PushScreenStatePlan(
                shouldStopAlarm = false,
                shouldUpdateAlarm = false,
                shouldConnect = false,
                eventAction = "screen_on_falldown_skip",
            )
        }
        val connect = !isConnected && !isConnecting
        return PushScreenStatePlan(
            shouldStopAlarm = false,
            shouldUpdateAlarm = true,
            shouldConnect = connect,
            eventAction = if (connect) "screen_on_connect" else "screen_on_update_alarm",
        )
    }

    fun planTimer(
        shouldFalldown: Boolean,
        alarmAlive: Boolean,
        isConnected: Boolean,
        isConnecting: Boolean,
        shouldCheckAlive: Boolean,
    ): PushTimerPlan {
        if (shouldFalldown) {
            return PushTimerPlan(
                shouldStopAlarm = alarmAlive,
                shouldRegisterPing = false,
                shouldConnect = false,
                shouldCheckAlive = false,
                eventAction = if (alarmAlive) "timer_falldown_stop" else "timer_falldown_noop",
            )
        }
        val connect = !isConnected && !isConnecting
        return PushTimerPlan(
            shouldStopAlarm = false,
            shouldRegisterPing = true,
            shouldConnect = connect,
            shouldCheckAlive = !connect && shouldCheckAlive,
            eventAction = when {
                connect -> "timer_connect"
                shouldCheckAlive -> "timer_check_alive"
                else -> "timer_ping"
            },
        )
    }

    fun planClientChange(
        activeClientCount: Int,
        shouldUpdateAlarm: Boolean,
    ): PushClientChangePlan {
        val disconnect = activeClientCount <= 0
        return PushClientChangePlan(
            shouldUpdateAlarm = shouldUpdateAlarm,
            shouldDisconnect = disconnect,
            eventAction = if (disconnect) "client_change_disconnect" else "client_change_update_alarm",
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun planPowerModeChanged(
        isExtremePowerMode: Boolean,
        isSuperPowerMode: Boolean,
        isConnected: Boolean,
    ): PushPowerModePlan = when {
        isExtremePowerMode -> PushPowerModePlan(true, false, false, 23, "power_mode_extreme_disconnect")
        isSuperPowerMode -> PushPowerModePlan(true, false, true, 24, "power_mode_super_disconnect")
        else -> PushPowerModePlan(false, true, false, 0, "power_mode_off_connect")
    }

    fun planReconnectionFailure(shouldFalldown: Boolean): PushReconnectionFailurePlan =
        PushReconnectionFailurePlan(
            shouldBroadcastUnavailable = true,
            shouldScheduleReconnect = !shouldFalldown,
            eventAction = if (shouldFalldown) "reconnect_failed_falldown" else "reconnect_failed_schedule",
        )

    fun planReconnectionSuccess(
        alarmAlive: Boolean,
        shouldFalldown: Boolean,
    ): PushReconnectionSuccessPlan {
        val registerAlarm = !alarmAlive && !shouldFalldown
        return PushReconnectionSuccessPlan(
            shouldBroadcastAvailable = true,
            shouldResetReconnectState = true,
            shouldRegisterAlarm = registerAlarm,
            shouldBindAllClients = true,
            eventAction = if (registerAlarm) "reconnect_success_alarm_reactivated" else "reconnect_success",
        )
    }

    fun planConnectionClosed(
        shouldFalldown: Boolean,
        failedConnection: Boolean,
    ): PushConnectionClosedPlan = PushConnectionClosedPlan(
        shouldScheduleReconnect = !shouldFalldown || failedConnection,
        eventAction = if (shouldFalldown && !failedConnection) {
            "connection_closed_falldown"
        } else {
            "connection_closed_schedule_reconnect"
        },
    )
}
