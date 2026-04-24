package io.github.magisk317.mipush.service.runtime
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.push.service.*

object PushConnectionStatusRuntime {
    private const val STATUS_CONNECTING = 0
    private const val STATUS_CONNECTED = 1
    private const val STATUS_DISCONNECTED = 2

    @JvmStatic
    fun planStatusChange(
        currentStatus: Int,
        newStatus: Int
    ): PushConnectionStatusPlan {
        return when (newStatus) {
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
    }
}
