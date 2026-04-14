package com.xiaomi.push.service

import com.xiaomi.xmsf.runtime.PushConnectionState

data class PushSlimInboundPlan(
    val action: PushSlimInboundAction,
    val eventAction: String? = null,
    val shouldUpdateLastReceived: Boolean = false,
    val connectionState: PushConnectionState? = null,
    val connectionReason: String? = null,
    val disconnectReasonCode: Int? = null,
)

data class PushSlimPingPlan(
    val eventAction: String,
)

enum class PushSlimInboundAction {
    None,
    PingReceived,
    CloseReceived,
}
