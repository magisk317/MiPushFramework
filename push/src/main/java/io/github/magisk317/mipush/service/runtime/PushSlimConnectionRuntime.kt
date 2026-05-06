package io.github.magisk317.mipush.service.runtime
import com.xiaomi.push.service.*
import com.xiaomi.slim.*

object PushSlimConnectionRuntime {
    @JvmStatic
    fun planInboundBlob(
        channelId: Int,
        cmd: String?
    ): PushSlimInboundPlan {
        if (channelId != 0) {
            return PushSlimInboundPlan(action = PushSlimInboundAction.DeliverBlob)
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
            Blob.CMD_CONN -> PushSlimInboundPlan(
                action = PushSlimInboundAction.ChallengeReceived,
                eventAction = "slim_challenge_received"
            )
            else -> PushSlimInboundPlan(action = PushSlimInboundAction.None)
        }
    }

    @JvmStatic
    fun planSendPing(): PushSlimPingPlan {
        return PushSlimPingPlan(eventAction = "slim_ping_sent")
    }
}
