package com.xiaomi.slim

import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.mipush.sdk.Constants
import com.xiaomi.push.protobuf.ChannelMessage
import com.xiaomi.push.service.RC4Cryption
import com.xiaomi.smack.packet.Packet
import com.xiaomi.smack.util.StringUtils
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer

open class Blob {
    private var mPayloadType: Short = PAYLOAD_BINARY.toShort()
    private var mPayload: ByteArray = EMPTY
    var mPackageName: String? = null
    private var mHeader: ChannelMessage.ClientHeader = ChannelMessage.ClientHeader()

    constructor()

    internal constructor(
        clientHeader: ChannelMessage.ClientHeader,
        payloadType: Short,
        payload: ByteArray,
    ) {
        mHeader = clientHeader
        mPayloadType = payloadType
        mPayload = payload
    }

    val channelId: Int
        get() = mHeader.chid

    val cmd: String
        get() = mHeader.cmd

    val errCode: Int
        get() = mHeader.errCode

    val errStr: String
        get() = mHeader.errStr

    val subcmd: String
        get() = mHeader.subcmd

    val payloadType: Short
        get() = mPayloadType

    open val payload: ByteArray
        get() = mPayload

    /** Backwards-compatible alias for [from]. */
    val fullUserName: String?
        get() = from

    /** Backwards-compatible accessor for [mPackageName]. */
    val packageName: String?
        get() = mPackageName

    var from: String?
        get() {
            if (!mHeader.hasUuid()) return null
            return "${mHeader.uuid}@${mHeader.server}/${mHeader.resource}"
        }
        set(value) {
            if (value.isNullOrEmpty()) return
            val iIndexOf = value.indexOf("@")
            try {
                val uuid = value.substring(0, iIndexOf).toLong()
                val iIndexOf2 = value.indexOf("/", iIndexOf)
                val server = value.substring(iIndexOf + 1, iIndexOf2)
                val resource = value.substring(iIndexOf2 + 1)
                mHeader.uuid = uuid
                mHeader.server = server
                mHeader.resource = resource
            } catch (e: Exception) {
                MyLog.w("Blob parse user err ${e.message}")
            }
        }

    var packetID: String?
        get() {
            val id = mHeader.id
            if (ID_NOT_AVAILABLE == id) return null
            if (!mHeader.hasId()) {
                val nextId = nextID()
                mHeader.id = nextId
                return nextId
            }
            return id
        }
        set(value) {
            mHeader.id = value
        }

    open val serializedSize: Int
        get() = mHeader.serializedSize + HEADER_SIZE + mPayload.size

    fun hasErr(): Boolean = mHeader.hasErrCode()

    fun getDecryptedPayload(security: String?): ByteArray {
        return when (mHeader.cipher) {
            CIPHER_RC4 -> RC4Cryption.encrypt(
                RC4Cryption.generateKeyForRC4(security!!, packetID!!),
                mPayload,
            )
            CIPHER_NONE -> mPayload
            else -> {
                MyLog.w("unknow cipher = ${mHeader.cipher}")
                mPayload
            }
        }
    }

    fun setFrom(
        uuid: Long,
        server: String,
        resource: String?,
    ) {
        if (uuid != 0L) {
            mHeader.uuid = uuid
        }
        if (!TextUtils.isEmpty(server)) {
            mHeader.server = server
        }
        if (resource.isNullOrEmpty()) return
        mHeader.resource = resource
    }

    fun setCmd(
        cmd: String,
        subcmd: String?,
    ) {
        if (TextUtils.isEmpty(cmd)) {
            throw IllegalArgumentException("command should not be empty")
        }
        mHeader.cmd = cmd
        mHeader.clearSubcmd()
        if (subcmd.isNullOrEmpty()) return
        mHeader.subcmd = subcmd
    }

    fun setPayload(
        payload: ByteArray,
        security: String?,
    ) {
        if (security.isNullOrEmpty()) {
            mHeader.cipher = CIPHER_NONE
            mPayload = payload
        } else {
            mHeader.cipher = CIPHER_RC4
            mPayload = RC4Cryption.encrypt(
                RC4Cryption.generateKeyForRC4(security, packetID!!),
                payload,
            )
        }
    }

    fun setPayloadType(type: Short) {
        mPayloadType = type
    }

    fun setChannelId(channelId: Int) {
        mHeader.chid = channelId
    }

    fun setPackageName(packageName: String?) {
        mPackageName = packageName
    }

    open fun toByteArray(byteBuffer: ByteBuffer? = null): ByteBuffer {
        val buffer = byteBuffer ?: ByteBuffer.allocate(serializedSize)
        buffer.putShort(mPayloadType)
        buffer.putShort(mHeader.cachedSize.toShort())
        buffer.putInt(mPayload.size)
        val position = buffer.position()
        mHeader.toByteArray(buffer.array(), buffer.arrayOffset() + position, mHeader.cachedSize)
        buffer.position(mHeader.cachedSize + position)
        buffer.put(mPayload)
        return buffer
    }

    override fun toString(): String {
        return "Blob [chid=$channelId; Id=$packetID; cmd=$cmd; type=${payloadType.toInt()}; from=$from ]"
    }

    companion object {
        const val MAGIC: Short = -15618
        const val VERSION: Short = 5
        const val VERSION5: Short = 4
        const val MAX_BLOB_SIZE = 32768
        const val HEADER_SIZE = 8
        const val CHCKSUM_SIZE = 4
        const val CIPHER_NONE = 0
        const val CIPHER_RC4 = 1
        const val CIPHER_AES = 2
        const val PAYLOAD_THRIFT: Short = 1
        const val PAYLOAD_BINARY: Short = 2
        const val PAYLOAD_XML: Short = 3
        const val PAYLOAD_PROTO: Short = 2

        const val CMD_BIND = "BIND"
        const val CMD_UNBIND = "UBND"
        const val CMD_CONN = "CONN"
        const val CMD_KICK = "KICK"
        const val CMD_NOTIFY = "NOTIFY"
        const val CMD_PING = "PING"
        const val CMD_SECMSG = "SECMSG"
        const val CMD_SYNC = "SYNC"
        const val CMD_CLOSE = "CLOSE"
        const val CMD_XMLMSG = "XMLMSG"
        const val SUBCMD_CONF = "CONF"

        const val CLIENT_PING_ID = "0"
        const val SERVER_PING_ID = "1"
        const val ID_NOT_AVAILABLE = "ID_NOT_AVAILABLE"
        const val XIAOMI_SERVER = "xiaomi.com"

        const val ERROR_HOST_UNKNOWN = 100
        const val ERROR_PARSE_CONN = 102
        const val ERROR_FIRST_PACKET_NOT_CONN = 103
        const val ERROR_UNSUPPORTED_VERSION = 101
        const val ERROR_INVALID_CHID = 200
        const val ERROR_INVALID_FROM = 201
        const val ERROR_UNEXPECTED_REQUEST = 202
        const val ERROR_USR_NOT_BIND = 203
        const val ERROR_PARSE_REQUEST = 204
        const val ERROR_INVALID_XML = 205
        const val ERROR_ALLOCATE_SESSION_FAILED = 206
        const val ERROR_BIND_TIMEOUT = 206
        const val ERROR_SEND_TO_IMS_FAILED = 300
        const val ERROR_SEND_TO_XMQ_FAILED = 301
        const val ERROR_SEND_TO_GATEWAY_FAILED = 302
        const val ERROR_FE_TRANSPORT_CLOSED = 303

        private const val MAGIC_OFFSET: Byte = 0
        private const val VERSION_OFFSET: Byte = 2
        private const val HEAD_LEN_OFFSET: Byte = 2
        private const val LENGTH_OFFSET: Byte = 4
        private const val PAYLOAD_TYPE_OFFSET: Byte = 0
        private const val PAYLOAD_LEN_OFFSET: Byte = 4
        private const val SUB_HEADER_OFFSET: Byte = 8

        private val EMPTY = ByteArray(0)

        private var prefix: String = StringUtils.randomString(5) + Constants.ACCEPT_TIME_SEPARATOR_SERVER
        private var id: Long = 0

        @JvmStatic
        fun nextID(): String {
            synchronized(Blob::class.java) {
                val sb = StringBuilder()
                sb.append(prefix)
                val j = id
                id = 1 + j
                sb.append(j.toString())
                return sb.toString()
            }
        }

        @JvmStatic
        fun from(
            packet: Packet,
            security: String?,
        ): Blob {
            val blob = Blob()
            val chId = try {
                packet.channelId.toInt()
            } catch (e: Exception) {
                MyLog.w("Blob parse chid err ${e.message}")
                1
            }
            blob.setChannelId(chId)
            blob.packetID = packet.packetID
            blob.from = packet.from
            blob.mPackageName = packet.packageName
            blob.setCmd(CMD_XMLMSG, null)
            try {
                blob.setPayload(packet.toXML().toByteArray(charset("utf8")), security)
                blob.mPayloadType = if (security.isNullOrEmpty()) {
                    PAYLOAD_XML
                } else {
                    blob.setCmd(CMD_SECMSG, null)
                    PAYLOAD_BINARY
                }
            } catch (e: UnsupportedEncodingException) {
                MyLog.w("Blob setPayload err: ${e.message}")
            }
            return blob
        }

        @JvmStatic
        @Throws(IOException::class)
        fun from(byteBuffer: ByteBuffer): Blob {
            return try {
                val byteBufferSlice = byteBuffer.slice()
                val s = byteBufferSlice.getShort(0)
                val s2 = byteBufferSlice.getShort(2)
                val i = byteBufferSlice.getInt(4)
                val clientHeader = ChannelMessage.ClientHeader()
                clientHeader.mergeFrom(
                    byteBufferSlice.array(),
                    byteBufferSlice.arrayOffset() + 8,
                    s2.toInt(),
                )
                val bArr = ByteArray(i)
                byteBufferSlice.position((s2 + 8).toInt())
                byteBufferSlice.get(bArr, 0, i)
                Blob(clientHeader, s, bArr)
            } catch (e: Exception) {
                MyLog.w("read Blob err :${e.message}")
                throw IOException("Malformed Input")
            }
        }
    }
}
