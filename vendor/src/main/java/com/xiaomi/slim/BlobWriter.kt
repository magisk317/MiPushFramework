package com.xiaomi.slim

import android.os.Build
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.DateTimeHelper
import com.xiaomi.push.protobuf.ChannelMessage
import com.xiaomi.push.service.*
import com.xiaomi.smack.Connection
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.Locale
import java.util.TimeZone
import java.util.zip.Adler32

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/pa/e.java
 * Stock class name is obfuscated as pa.e; this file keeps the deobfuscated com.xiaomi.slim.BlobWriter API.
 */
internal class BlobWriter(
    outputStream: OutputStream,
    private val mConnection: Connection,
) {
    private val mOutputStream: OutputStream = BufferedOutputStream(outputStream)
    private var mBuffer: ByteBuffer = ByteBuffer.allocate(2048)
    private val mCRCBuf: ByteBuffer = ByteBuffer.allocate(4)
    private val mChecksumTool = Adler32()
    private var mKey: ByteArray? = null
    private val mTimeZone: Int
    private val mDSTSavings: Int

    init {
        val timeZone = TimeZone.getDefault()
        mTimeZone = timeZone.rawOffset / DateTimeHelper.HOUR_IN_MS
        mDSTSavings = if (timeZone.useDaylightTime()) 1 else 0
    }

    @Throws(IOException::class)
    fun openStream() {
        val deviceUuid = ServiceConfig.getDeviceUUID()
        val connReq = ChannelMessage.XMMsgConn().apply {
            setVersion(Connection.ERR_TCP_INVALARG)
            setModel(Build.MODEL)
            setOs(SystemUtils.getManufacturerOSVersion())
            if (!deviceUuid.isNullOrEmpty()) {
                setUdid(deviceUuid)
            }
            mConnection.config.connectionPoint?.let(::setConnpt)
            mConnection.host?.let(::setHost)
            setLocale(Locale.getDefault().toString())
            setAndver(Build.VERSION.SDK_INT)
            setSdk(41)
            mConnection.config.getConnectionBlob()?.let {
                setPsc(ChannelMessage.PushServiceConfigMsg.parseFrom(it))
            }
        }

        val blob = Blob().apply {
            setChannelId(0)
            setCmd(Blob.CMD_CONN, null)
            setFrom(0L, Blob.XIAOMI_SERVER, null)
            setPayload(connReq.toByteArray(), null)
        }
        write(blob)
        MyLog.w(
            "[slim] open conn: andver=${Build.VERSION.SDK_INT} sdk=41 hash=$deviceUuid " +
                "tz=$mTimeZone:$mDSTSavings Model=${Build.MODEL} os=${Build.VERSION.INCREMENTAL}"
        )
    }

    @Throws(IOException::class)
    fun write(blob: Blob): Int {
        val serializedSize = blob.serializedSize
        
        val observer = XMPushServiceProxy.get()?.runtimeObserver
        val writePlan = observer?.planSlimWrite(serializedSize, blob.cmd, mBuffer.capacity())
            ?: fallbackWritePlan(serializedSize, blob.cmd, mBuffer.capacity())
        
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
            if (mKey == null) {
                mKey = mConnection.key
            }
            mKey?.let { key ->
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

    private fun fallbackWritePlan(serializedSize: Int, cmd: String?, currentCapacity: Int): PushSlimWritePlan {
        if (serializedSize > Blob.MAX_BLOB_SIZE) {
            return PushSlimWritePlan(
                eventAction = "slim_write_drop",
                shouldDrop = true,
                requiredCapacity = currentCapacity,
                shouldEncrypt = false
            )
        }
        val requiredCapacity = serializedSize + Blob.HEADER_SIZE + Blob.CHCKSUM_SIZE
        return PushSlimWritePlan(
            eventAction = if (Blob.CMD_PING == cmd) "slim_ping_sent" else "slim_write",
            requiredCapacity = if (requiredCapacity > currentCapacity || currentCapacity > 4096) {
                requiredCapacity
            } else {
                currentCapacity.coerceAtLeast(2048)
            },
            shouldEncrypt = Blob.CMD_CONN != cmd
        )
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
