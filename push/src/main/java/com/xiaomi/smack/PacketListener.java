package com.xiaomi.smack;

import com.xiaomi.slim.Blob;
import com.xiaomi.smack.packet.Packet;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/PacketListener.class */
public interface PacketListener {
    void process(Blob blob);

    void processPacket(Packet packet);
}
