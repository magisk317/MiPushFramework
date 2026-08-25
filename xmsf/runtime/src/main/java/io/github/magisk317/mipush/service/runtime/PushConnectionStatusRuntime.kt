package io.github.magisk317.mipush.service.runtime

import com.xiaomi.push.service.*
import io.github.magisk317.xposed.logging.MagiskOtel

object PushConnectionStatusRuntime {
    private const val STATUS_CONNECTING = 0
    private const val STATUS_CONNECTED = 1
    private const val STATUS_DISCONNECTED = 2

    @JvmStatic
    fun planStatusChange(
        currentStatus: Int,
        newStatus: Int
    ): PushConnectionStatusPlan {
        val plan = when (newStatus) {
            STATUS_CONNECTED -> PushConnectionStatusPlan(
                eventAction = "connection_status_connected",
                shouldRemoveConnectingTimeout = true,
                listenerEvent = PushConnectionListenerEvent.ReconnectionSuccessful,
                warningMessage = if (currentStatus != STATUS_CONNECTING) {
                    "try set connected while not connecting."
                } else {
                    null
                }
            )
            STATUS_CONNECTING -> PushConnectionStatusPlan(
                eventAction = "connection_status_connecting",
                shouldRemoveConnectingTimeout = false,
                listenerEvent = PushConnectionListenerEvent.ConnectionStarted,
                warningMessage = if (currentStatus != STATUS_DISCONNECTED) {
                    "try set connecting while not disconnected."
                } else {
                    null
                }
            )
            STATUS_DISCONNECTED -> PushConnectionStatusPlan(
                eventAction = "connection_status_disconnected",
                shouldRemoveConnectingTimeout = true,
                listenerEvent = when (currentStatus) {
                    STATUS_CONNECTING -> PushConnectionListenerEvent.ReconnectionFailed
                    STATUS_CONNECTED -> PushConnectionListenerEvent.ConnectionClosed
                    else -> PushConnectionListenerEvent.None
                },
                warningMessage = null
            )
            else -> PushConnectionStatusPlan(
                eventAction = "connection_status_unknown",
                shouldRemoveConnectingTimeout = false,
                listenerEvent = PushConnectionListenerEvent.None,
                warningMessage = null
            )
        }
        MagiskOtel.event(
            name = "push.lifecycle",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "connection_status",
                "reason" to plan.eventAction,
                "source" to currentStatus.toString(),
            ),
            statusOk = true,
        )
        return plan
    }
}
