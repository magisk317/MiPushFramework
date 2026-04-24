package com.xiaomi.smack
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet

interface PacketListener {
    fun process(blob: Blob)

    fun processPacket(packet: Packet)
}
