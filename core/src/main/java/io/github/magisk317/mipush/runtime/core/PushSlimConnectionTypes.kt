package io.github.magisk317.mipush.runtime.core

import io.github.magisk317.mipush.runtime.core.PushConnectionState

data class PushSlimInboundPlan(
    val action: PushSlimInboundAction,
    val eventAction: String? = null,
    val shouldUpdateLastReceived: Boolean = false,
    val connectionState: PushConnectionState? = null,
    val connectionReason: String? = null,
    val disconnectReasonCode: Int? = null,
    val shouldLogUnknownType: Boolean = false
)

data class PushSlimPingPlan(
    val eventAction: String,
)

enum class PushSlimInboundAction {
    None,
    PingReceived,
    CloseReceived,
    ChallengeReceived,
    DeliverBlob,
    ParseSecurePacket,
    ParsePacket,
    IgnoreUnknown
}
