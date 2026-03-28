package com.xiaomi.slim;

import java.nio.ByteBuffer;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/slim/Ping.class */
public final class Ping extends Blob {
    public Ping() {
        setCmd(Blob.CMD_PING, null);
        setPacketID(Blob.CLIENT_PING_ID);
        setChannelId(0);
    }

    @Override // com.xiaomi.slim.Blob
    public int getSerializedSize() {
        if (getPayload().length == 0) {
            return 0;
        }
        return super.getSerializedSize();
    }

    @Override // com.xiaomi.slim.Blob
    ByteBuffer toByteArray(ByteBuffer byteBuffer) {
        return getPayload().length == 0 ? byteBuffer : super.toByteArray(byteBuffer);
    }
}
