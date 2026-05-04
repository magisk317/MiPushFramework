package com.xiaomi.slim
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.protobuf.ChannelMessage
import com.xiaomi.push.service.*
import com.xiaomi.smack.Connection
import io.github.magisk317.mipush.protocol.model.*
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.Adler32

internal class BlobWriter(
    outputStream: OutputStream,
    private val mConnection: Connection,
) {
    private val mOutputStream: OutputStream = BufferedOutputStream(outputStream)
    private var mBuffer: ByteBuffer = ByteBuffer.allocate(2048)
    private val mCRCBuf: ByteBuffer = ByteBuffer.allocate(4)
    private val mChecksumTool = Adler32()

    @Throws(IOException::class)
    fun openStream() {
        val observer = XMPushServiceProxy.get()?.runtimeObserver
        val handshakePlan = observer?.planSlimHandshake(hasChallenge = false, hasConfigMessage = false) ?: PushSlimHandshakePlan(true, "slim_handshake_sent", false)
        
        val connReq = ChannelMessage.XMMsgConn().apply {
            setVersion(Blob.VERSION.toInt())
            setModel(android.os.Build.MODEL)
            setOs(android.os.Build.VERSION.RELEASE)
            setSdk(38) // Example value
        }
        
        val blob = Blob().apply {
            setCmd(Blob.CMD_CONN, null)
            setPayload(connReq.toByteArray(), null)
        }
        write(blob)
    }

    @Throws(IOException::class)
    fun write(blob: Blob): Int {
        val serializedSize = blob.serializedSize
        
        val observer = XMPushServiceProxy.get()?.runtimeObserver
        val writePlan = observer?.planSlimWrite(Blob.CMD_PING == blob.cmd) ?: PushSlimWritePlan(if (Blob.CMD_PING == blob.cmd) "slim_ping_sent" else null)
        
        writePlan.eventAction?.let { MyLog.w("[slim] $it") }
        
        if (writePlan.shouldDrop) {
            MyLog.w("Blob size=$serializedSize should be less than ${Blob.MAX_BLOB_SIZE} Drop blob chid=${blob.channelId} id=${blob.packetID}")
            return 0
        }
        
        if (writePlan.requiredCapacity > 0) {
            if (writePlan.requiredCapacity > mBuffer.capacity()) {
                mBuffer = ByteBuffer.allocate(writePlan.requiredCapacity)
            }
        }

        mBuffer.clear()
        mBuffer.putShort(Blob.MAGIC)
        mBuffer.putShort(Blob.VERSION)
        mBuffer.putInt(serializedSize)
        val position = mBuffer.position()
        
        blob.toByteArray(mBuffer)
        
        if (writePlan.shouldEncrypt) {
            val key = mConnection.key
            if (key != null) {
                RC4Cryption.encrypt(key, mBuffer.array(), true, position, serializedSize)
            }
        }
        
        mChecksumTool.reset()
        mChecksumTool.update(mBuffer.array(), 0, mBuffer.position())
        val crcValue = mChecksumTool.value.toInt()
        mCRCBuf.clear()
        mCRCBuf.putInt(crcValue)
        mOutputStream.write(mBuffer.array(), 0, mBuffer.position())
        mOutputStream.write(mCRCBuf.array(), 0, 4)
        mOutputStream.flush()
        
        val bytes = mBuffer.position() + 4
        MyLog.v("[Slim] Send {cmd=${blob.cmd};chid=${blob.channelId};len=$bytes}")
        return bytes
    }

    @Throws(IOException::class)
    fun write(connResp: ChannelMessage.XMMsgConnResp) {
        val bytes = connResp.toByteArray()
        val blob = Blob().apply {
            setCmd(Blob.CMD_CONN, null)
            setPayload(bytes, null)
        }
        write(blob)
        MyLog.w("[Slim] write CONN Resp: host = ${connResp.host}")
    }

    @Throws(IOException::class)
    fun shutdown() {
        val blob = Blob().apply {
            setCmd(Blob.CMD_CLOSE, null)
        }
        write(blob)
        mOutputStream.close()
    }
}
