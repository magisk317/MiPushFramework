package com.xiaomi.smack

import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet

interface PacketListener {
    fun process(blob: Blob)

    fun processPacket(packet: Packet)
}
