package com.xiaomi.smack.filter;

import com.xiaomi.smack.packet.Packet;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/filter/PacketFilter.class */
public interface PacketFilter {
    boolean accept(Packet packet);
}
