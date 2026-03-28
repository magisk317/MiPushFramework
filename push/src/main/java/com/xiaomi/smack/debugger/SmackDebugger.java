package com.xiaomi.smack.debugger;

import com.xiaomi.smack.PacketListener;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/debugger/SmackDebugger.class */
public interface SmackDebugger {
    PacketListener getReaderListener();

    PacketListener getWriterListener();
}
