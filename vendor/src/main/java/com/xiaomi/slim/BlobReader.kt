package com.xiaomi.slim

import co.touchlab.kermit.Logger
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
        val blob = read()
        val connectionResponse = if (Blob.CMD_CONN == blob.cmd) {
            ChannelMessage.XMMsgConnResp.parseFrom(blob.payload)
        } else {
            null
        }
        val hasChallenge = connectionResponse?.let { response ->
            response.hasChallenge() && response.challenge.isNotEmpty()
        } == true
        val hasConfigMessage = connectionResponse?.hasPsc() == true
        val observer = XMPushServiceProxy.get()?.runtimeObserver
        val handshakePlan = observer?.planSlimHandshake(hasChallenge, hasConfigMessage)
            ?: io.github.magisk317.mipush.runtime.core.PushSlimStreamPlanFactory.planHandshake(
                hasChallenge,
                hasConfigMessage,
            )
        if (handshakePlan.valid) {
            mConnection.onChallengeReceived(connectionResponse!!.challenge, "BlobReader.loop")
        }
        if (handshakePlan.shouldEmitConfigBlob && connectionResponse?.hasPsc() == true) {
            val blob2 = Blob().apply {
                setCmd(Blob.CMD_SYNC, Blob.SUBCMD_CONF)
                setPayload(connectionResponse.psc.toByteArray(), null)
            }
            mConnection.notifyDataArrived(blob2)
        }
        connectionResponse?.let { response -> Logger.w { "[Slim] CONN: host = ${response.host}" } }
        if (!handshakePlan.valid) {
            Logger.w { "[Slim] Invalid CONN" }
            throw IOException(handshakePlan.failureReason ?: "Invalid Connection")
        }
        mKey = mConnection.key
        while (!mDone) {
            val blob3 = read()
            mConnection.setReadAlive()
            val payloadPlan = observer?.planSlimPayloadDispatch(
                payloadType = blob3.payloadType.toInt(),
                cmd = blob3.cmd,
                channelId = blob3.channelId,
                subcmd = blob3.subcmd,
            ) ?: io.github.magisk317.mipush.runtime.core.PushSlimStreamPlanFactory.planPayloadDispatch(
                payloadType = blob3.payloadType.toInt(),
                command = mapSlimCommand(blob3.cmd),
                channelId = blob3.channelId,
                hasSubcommand = blob3.subcmd.isNotEmpty(),
            )

            payloadPlan.eventAction?.let { Logger.w { "[slim] $it" } }

            when (payloadPlan.action) {
                PushSlimPayloadAction.DeliverBlob -> {
                    mConnection.notifyDataArrived(blob3)
                }
                PushSlimPayloadAction.ParseSecurePacket -> {
                    try {
                        val clientLoginInfo = PushClientsManager.getInstance()
                            .getClientLoginInfoByChidAndUserId(
                                blob3.channelId.toString(),
                                blob3.fullUserName
                            ) ?: error("missing client info")
                        mConnection.notifyDataArrived(
                            mPacketParser.parse(blob3.getDecryptedPayload(clientLoginInfo.security), mConnection)
                        )
                    } catch (e: Exception) {
                        Logger.w(e) { "[Slim] Parse packet from Blob chid=${blob3.channelId}; Id=${blob3.packetID} failure:${e.message}" }
                    }
                }
                PushSlimPayloadAction.ParsePacket -> {
                    try {
                        mConnection.notifyDataArrived(mPacketParser.parse(blob3.payload, mConnection))
                    } catch (e: Exception) {
                        Logger.w(e) { "[Slim] Parse packet from Blob chid=${blob3.channelId}; Id=${blob3.packetID} failure:${e.message}" }
                    }
                }
                else -> {
                    if (payloadPlan.shouldLogUnknownType) {
                        Logger.w { "unknown blob type chid=${blob3.channelId}" }
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
        Logger.w { "CRC = ${mChecksumTool.value.toInt()} and $crcValue" }
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
            Logger.v { "[Slim] Read {cmd=${blob.cmd};chid=${blob.channelId};len=$readLen}" }
            blob
        } catch (e: IOException) {
            var len = if (readLen == 0) mBuffer.position() else readLen
            if (len > 128) len = 128
            Logger.w(e) { "[Slim] read Blob [${DebugUtils.bytes2Hex(mBuffer.array(), 0, len)}] Err:${e.message}" }
            throw e
        }
    }

    private fun mapSlimCommand(cmd: String?): io.github.magisk317.mipush.runtime.core.PushSlimCommand = when (cmd) {
        Blob.CMD_PING -> io.github.magisk317.mipush.runtime.core.PushSlimCommand.Ping
        Blob.CMD_CLOSE -> io.github.magisk317.mipush.runtime.core.PushSlimCommand.Close
        Blob.CMD_CONN -> io.github.magisk317.mipush.runtime.core.PushSlimCommand.Connection
        Blob.CMD_SECMSG -> io.github.magisk317.mipush.runtime.core.PushSlimCommand.SecureMessage
        else -> io.github.magisk317.mipush.runtime.core.PushSlimCommand.Other
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
