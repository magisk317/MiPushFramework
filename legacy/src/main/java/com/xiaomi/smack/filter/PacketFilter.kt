package com.xiaomi.smack.filter
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.smack.packet.Packet

fun interface PacketFilter {
    fun accept(packet: Packet): Boolean
}
