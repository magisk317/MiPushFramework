package com.xiaomi.slim

import java.nio.ByteBuffer

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/pa/h.java
 * Stock class name is obfuscated as pa.h; this file keeps the deobfuscated com.xiaomi.slim.Ping API.
 */
class Ping : Blob() {
    init {
        setCmd(Blob.CMD_PING, null)
        packetID = Blob.CLIENT_PING_ID
        setChannelId(0)
    }

    override val serializedSize: Int
        get() = if (payload.isEmpty()) 0 else super.serializedSize

    override fun toByteArray(byteBuffer: ByteBuffer?): ByteBuffer {
        return if (payload.isEmpty()) {
            byteBuffer ?: ByteBuffer.allocate(0)
        } else {
            super.toByteArray(byteBuffer)
        }
    }
}
