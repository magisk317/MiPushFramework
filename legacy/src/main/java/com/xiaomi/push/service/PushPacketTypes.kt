package com.xiaomi.push.service

import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.smack.packet.Packet

enum class PushPacketRouteAction {
    Ready,
    DropNoChannel,
    DropDisconnected,
    DropUnbound,
    DropInvalidSession
}

data class PushPacketPreparationResult(
    val action: PushPacketRouteAction,
    val packet: Packet?,
    val client: PushClientsManager.ClientLoginInfo?,
    val reason: String
)
