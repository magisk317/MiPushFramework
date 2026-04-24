package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.slim.Blob
import com.xiaomi.smack.PacketListener
import com.xiaomi.smack.packet.Packet

class XMPushServiceInboundPacketListener(
    private val service: XMPushService,
) : PacketListener {
    override fun process(blob: Blob) {
        service.executeJob(BlobReceiveJob(service, blob))
    }

    override fun processPacket(packet: Packet) {
        service.executeJob(PacketReceiveJob(service, packet))
    }
}
