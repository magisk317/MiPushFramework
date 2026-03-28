package com.xiaomi.push.service

enum class PushConnectionListenerEvent {
    None,
    ConnectionStarted,
    ReconnectionSuccessful,
    ReconnectionFailed,
    ConnectionClosed
}

data class PushConnectionStatusPlan(
    val shouldRemoveConnectingTimeout: Boolean,
    val listenerEvent: PushConnectionListenerEvent,
    val warningMessage: String?
)

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
                shouldRemoveConnectingTimeout = true,
                listenerEvent = PushConnectionListenerEvent.ReconnectionSuccessful,
                warningMessage = if (currentStatus != STATUS_CONNECTING) {
                    "try set connected while not connecting."
                } else {
                    null
                }
            )
            STATUS_CONNECTING -> PushConnectionStatusPlan(
                shouldRemoveConnectingTimeout = false,
                listenerEvent = PushConnectionListenerEvent.ConnectionStarted,
                warningMessage = if (currentStatus != STATUS_DISCONNECTED) {
                    "try set connecting while not disconnected."
                } else {
                    null
                }
            )
            STATUS_DISCONNECTED -> PushConnectionStatusPlan(
                shouldRemoveConnectingTimeout = true,
                listenerEvent = when (currentStatus) {
                    STATUS_CONNECTING -> PushConnectionListenerEvent.ReconnectionFailed
                    STATUS_CONNECTED -> PushConnectionListenerEvent.ConnectionClosed
                    else -> PushConnectionListenerEvent.None
                },
                warningMessage = null
            )
            else -> PushConnectionStatusPlan(
                shouldRemoveConnectingTimeout = false,
                listenerEvent = PushConnectionListenerEvent.None,
                warningMessage = null
            )
        }
    }
}
