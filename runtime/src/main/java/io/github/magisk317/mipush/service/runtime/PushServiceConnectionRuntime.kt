package io.github.magisk317.mipush.service.runtime
import io.github.magisk317.mipush.protocol.model.*
import com.xiaomi.push.service.*

data class PushReconnectionFailurePlan(
    val shouldBroadcastUnavailable: Boolean,
    val shouldScheduleReconnect: Boolean,
    val eventAction: String
)

data class PushReconnectionSuccessPlan(
    val shouldBroadcastAvailable: Boolean,
    val shouldResetReconnectState: Boolean,
    val shouldRegisterAlarm: Boolean,
    val shouldBindAllClients: Boolean,
    val eventAction: String
)

data class PushConnectionClosedPlan(
    val shouldScheduleReconnect: Boolean,
    val eventAction: String
)

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

    @JvmStatic
    fun planReconnectionFailure(shouldFalldown: Boolean): PushReconnectionFailurePlan {
        return PushReconnectionFailurePlan(
            shouldBroadcastUnavailable = true,
            shouldScheduleReconnect = !shouldFalldown,
            eventAction = if (shouldFalldown) {
                "reconnect_failed_falldown"
            } else {
                "reconnect_failed_schedule"
            }
        )
    }

    @JvmStatic
    fun planReconnectionSuccess(
        alarmAlive: Boolean,
        shouldFalldown: Boolean
    ): PushReconnectionSuccessPlan {
        val shouldRegisterAlarm = !alarmAlive && !shouldFalldown
        return PushReconnectionSuccessPlan(
            shouldBroadcastAvailable = true,
            shouldResetReconnectState = true,
            shouldRegisterAlarm = shouldRegisterAlarm,
            shouldBindAllClients = true,
            eventAction = if (shouldRegisterAlarm) {
                "reconnect_success_alarm_reactivated"
            } else {
                "reconnect_success"
            }
        )
    }

    @JvmStatic
    fun planConnectionClosed(shouldFalldown: Boolean): PushConnectionClosedPlan {
        return PushConnectionClosedPlan(
            shouldScheduleReconnect = !shouldFalldown,
            eventAction = if (shouldFalldown) {
                "connection_closed_falldown"
            } else {
                "connection_closed_schedule_reconnect"
            }
        )
    }
}
