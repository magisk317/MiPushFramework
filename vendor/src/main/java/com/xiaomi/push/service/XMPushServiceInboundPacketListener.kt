package com.xiaomi.push.service

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
