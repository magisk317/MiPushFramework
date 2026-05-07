package com.xiaomi.smack.filter

import com.xiaomi.smack.packet.Packet

fun interface PacketFilter {
    fun accept(packet: Packet): Boolean
}
