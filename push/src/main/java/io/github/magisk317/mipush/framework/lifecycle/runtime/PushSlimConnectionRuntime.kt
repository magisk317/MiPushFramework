package io.github.magisk317.mipush.framework.lifecycle.runtime

import com.xiaomi.slim.Blob
import io.github.magisk317.mipush.runtime.PushConnectionState

enum class PushSlimInboundAction {
    None,
    PingReceived,
    CloseReceived
}

data class PushSlimInboundPlan(
    val action: PushSlimInboundAction,
    val eventAction: String? = null,
    val shouldUpdateLastReceived: Boolean = false,
    val connectionState: PushConnectionState? = null,
    val connectionReason: String? = null,
    val disconnectReasonCode: Int? = null
)

data class PushSlimPingPlan(
    val eventAction: String
)

object PushSlimConnectionRuntime {
    @JvmStatic
    fun planInboundBlob(
        channelId: Int,
        cmd: String?
    ): PushSlimInboundPlan {
        if (channelId != 0) {
            return PushSlimInboundPlan(action = PushSlimInboundAction.None)
        }
        return when (cmd) {
            Blob.CMD_PING -> PushSlimInboundPlan(
                action = PushSlimInboundAction.PingReceived,
                eventAction = "slim_ping_received",
                shouldUpdateLastReceived = true
            )
            Blob.CMD_CLOSE -> PushSlimInboundPlan(
                action = PushSlimInboundAction.CloseReceived,
                eventAction = "slim_close_received",
                connectionState = PushConnectionState.Disconnected,
                connectionReason = "server_close_blob",
                disconnectReasonCode = 13
            )
            else -> PushSlimInboundPlan(action = PushSlimInboundAction.None)
        }
    }

    @JvmStatic
    fun planSendPing(): PushSlimPingPlan {
        return PushSlimPingPlan(eventAction = "slim_ping_sent")
    }
}
