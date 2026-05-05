package com.xiaomi.slim

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.DebugUtils
import com.xiaomi.push.protobuf.ChannelMessage
import com.xiaomi.push.service.*
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.Adler32

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/pa/d.java
 * Stock class name is obfuscated as pa.d; this file keeps the deobfuscated com.xiaomi.slim.BlobReader API.
 */
internal class BlobReader(
    inputStream: InputStream,
    private val mConnection: SlimConnection,
) {
    private val mInputStream: InputStream = BufferedInputStream(inputStream)
    private val mPacketParser = PacketParser()
    private var mBuffer: ByteBuffer = ByteBuffer.allocate(MIN_BLOB_SIZE)
    private val mCRCBuf: ByteBuffer = ByteBuffer.allocate(4)
    private val mChecksumTool = Adler32()
    private var mKey: ByteArray? = null
    @Volatile
    private var mDone: Boolean = false

    private fun loop() {
        mDone = false
        var valid = false
        val blob = read()
        if (Blob.CMD_CONN == blob.cmd) {
            val from = ChannelMessage.XMMsgConnResp.parseFrom(blob.payload)
            val observer = XMPushServiceProxy.get()?.runtimeObserver
            val hasChallenge = from.hasChallenge()
            val hasConfigMessage = from.hasPsc()
            val handshakePlan = observer?.planSlimHandshake(hasChallenge, hasConfigMessage) ?: PushSlimHandshakePlan(true, "slim_handshake_sent", false)
            MyLog.w("[slim] ${handshakePlan.eventAction}")
            valid = handshakePlan.valid
            if (handshakePlan.valid) {
                 // Challenge is now handled via notification or direct field if needed
                 // mConnection.challenge is usually set during bind or from CONN resp
            }
            if (handshakePlan.shouldEmitConfigBlob) {
                val psc = from.psc
                val blob2 = Blob().apply {
                    setCmd(Blob.CMD_SYNC, Blob.SUBCMD_CONF)
                    setPayload(psc.toByteArray(), null)
                }
                mConnection.notifyDataArrived(blob2)
            }
            MyLog.w("[Slim] CONN: host = ${from.host}")
            mConnection.notifyDataArrived(blob)
        }
        if (!valid) {
            MyLog.w("[Slim] Invalid CONN")
            throw IOException("Invalid Connection")
        }
        mKey = mConnection.key
        while (!mDone) {
            val blob3 = read()
            mConnection.setReadAlive()
            val observer = XMPushServiceProxy.get()?.runtimeObserver
            val payloadPlan = observer?.resolveSlimInboundPlan(blob3.channelId, blob3.cmd) ?: PushSlimInboundPlan(PushSlimInboundAction.None)
            
            payloadPlan.eventAction?.let { MyLog.w("[slim] $it") }
            
            when (payloadPlan.action) {
                PushSlimInboundAction.DeliverBlob -> {
                    mConnection.notifyDataArrived(blob3)
                }
                PushSlimInboundAction.ParseSecurePacket -> {
                    val packageName = blob3.packageName
                    try {
                        val key = mConnection.key
                        if (key != null) {
                            RC4Cryption.encrypt(key, blob3.payload, true, 0, blob3.payload.size)
                            mConnection.notifyDataArrived(mPacketParser.parse(blob3.payload, mConnection))
                        } else {
                            mConnection.notifyDataArrived(mPacketParser.parse(blob3.getDecryptedPayload(mConnection.challenge), mConnection))
                        }
                    } catch (e: Exception) {
                        MyLog.e("fail to decrypt blob from $packageName. chid=${blob3.channelId} $e")
                    }
                }
                PushSlimInboundAction.ParsePacket -> {
                    try {
                        mConnection.notifyDataArrived(mPacketParser.parse(blob3.payload, mConnection))
                    } catch (e: Exception) {
                        MyLog.e("fail to parse blob chid=${blob3.channelId} $e")
                    }
                }
                else -> {
                    if (payloadPlan.shouldLogUnknownType) {
                        MyLog.w("unknown blob type chid=${blob3.channelId}")
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun read(
        byteBuffer: ByteBuffer,
        length: Int,
    ) {
        var remaining = length
        var position = byteBuffer.position()
        do {
            val read = mInputStream.read(byteBuffer.array(), position, remaining)
            if (read == -1) {
                throw EOFException()
            }
            remaining -= read
            position += read
        } while (remaining > 0)
        byteBuffer.position(position)
    }

    @Throws(IOException::class)
    private fun readOnePacket(): ByteBuffer {
        mBuffer.clear()
        read(mBuffer, 8)
        val s = mBuffer.getShort(0)
        val s2 = mBuffer.getShort(2)
        if (s != Blob.MAGIC || s2 != Blob.VERSION) {
            throw IOException("Malformed Input")
        }
        val i = mBuffer.getInt(4)
        val position = mBuffer.position()
        if (i > Blob.MAX_BLOB_SIZE) {
            throw IOException("Blob size too large")
        }
        if (i + 4 > mBuffer.remaining()) {
            val newBuffer = ByteBuffer.allocate(i + MIN_BLOB_SIZE)
            newBuffer.put(mBuffer.array(), 0, mBuffer.position())
            mBuffer = newBuffer
        } else if (mBuffer.capacity() > MAX_BLOB_THRSHOLD && i < MIN_BLOB_SIZE) {
            val newBuffer = ByteBuffer.allocate(MIN_BLOB_SIZE)
            newBuffer.put(mBuffer.array(), 0, mBuffer.position())
            mBuffer = newBuffer
        }
        read(mBuffer, i)
        mCRCBuf.clear()
        read(mCRCBuf, 4)
        mCRCBuf.position(0)
        val crcValue = mCRCBuf.int
        mChecksumTool.reset()
        mChecksumTool.update(mBuffer.array(), 0, mBuffer.position())
        if (crcValue == mChecksumTool.value.toInt()) {
            val key = mKey
            if (key != null) {
                RC4Cryption.encrypt(key, mBuffer.array(), true, position, i)
            }
            return mBuffer
        }
        MyLog.w("CRC = ${mChecksumTool.value.toInt()} and $crcValue")
        throw IOException("Corrupted Blob bad CRC")
    }

    @Throws(IOException::class)
    fun read(): Blob {
        var readLen = 0
        return try {
            val buffer = readOnePacket()
            readLen = buffer.position()
            buffer.flip()
            buffer.position(8)
            val blob = if (readLen == 8) Ping() else Blob.from(buffer.slice())
            MyLog.v("[Slim] Read {cmd=${blob.cmd};chid=${blob.channelId};len=$readLen}")
            blob
        } catch (e: IOException) {
            var len = if (readLen == 0) mBuffer.position() else readLen
            if (len > 128) len = 128
            MyLog.w("[Slim] read Blob [${DebugUtils.bytes2Hex(mBuffer.array(), 0, len)}] Err:${e.message}")
            throw e
        }
    }

    fun shutdown() {
        mDone = true
    }

    @Throws(IOException::class)
    fun start() {
        try {
            loop()
        } catch (e: IOException) {
            if (!mDone) throw e
        }
    }

    private companion object {
        private const val MAX_BLOB_THRSHOLD = 4096
        private const val MIN_BLOB_SIZE = 2048
    }
}
