package com.xiaomi.push.service;

import com.xiaomi.slim.Blob;
import com.xiaomi.smack.PacketListener;
import com.xiaomi.smack.packet.Packet;

final class XMPushServiceInboundPacketListener implements PacketListener {
    private final XMPushService service;

    XMPushServiceInboundPacketListener(XMPushService xMPushService) {
        this.service = xMPushService;
    }

    @Override
    public void process(Blob blob) {
        this.service.executeJob(new BlobReceiveJob(this.service, blob));
    }

    @Override
    public void processPacket(Packet packet) {
        this.service.executeJob(new PacketReceiveJob(this.service, packet));
    }
}
