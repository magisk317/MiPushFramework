package com.xiaomi.slim

import java.nio.ByteBuffer

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
