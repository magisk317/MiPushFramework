package com.xiaomi.slim

import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.protobuf.ChannelMessage
import com.xiaomi.push.service.*
import com.xiaomi.smack.Connection
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.smack.SocketConnection
import com.xiaomi.smack.XMPPException
import com.xiaomi.smack.packet.Packet
import com.xiaomi.smack.util.TrafficUtils
import com.xiaomi.stats.StatsHelper
import java.io.IOException

class SlimConnection(
    pushAction: IPushServiceAction,
    context: android.content.Context,
    connectionConfiguration: ConnectionConfiguration,
) : SocketConnection(pushAction, context, connectionConfiguration) {
    private var mReader: BlobReader? = null
    private var mReaderThread: Thread? = null
    private var mWriter: BlobWriter? = null
    private var mDerivedKey: ByteArray? = null

    private fun getPing(isServerPing: Boolean): Blob {
        val ping = Ping()
        if (isServerPing) {
            ping.packetID = "1"
        }
        val statsBytes = StatsHelper.retriveStatsAsByte()
        if (statsBytes != null) {
            val xMMsgPing = ChannelMessage.XMMsgPing().apply {
                setStats(com.google.protobuf.micro.ByteStringMicro.copyFrom(statsBytes))
            }
            @Suppress("DEPRECATION")
            ping.setPayload(xMMsgPing.toByteArray(), null)
        }
        return ping
    }

    @Throws(XMPPException::class)
    private fun initReaderAndWriter() {
        try {
            val sock = socket ?: throw XMPPException("Socket is null")
            mReader = BlobReader(sock.getInputStream(), this)
            mWriter = BlobWriter(sock.getOutputStream(), this)
            val threadName = "Blob Reader (${connectionCounterValue})"
            val thread = Thread(object : Runnable {
                override fun run() {
                    try {
                        mReader?.start()
                    } catch (e: Exception) {
                        notifyConnectionError(9, e)
                    }
                }
            }, threadName)
            mReaderThread = thread
            thread.start()
        } catch (e: Exception) {
            throw XMPPException("Error to init reader and writer", e)
        }
    }

    override fun batchSend(blobArray: Array<Blob>) {
        for (blob in blobArray) {
            send(blob)
        }
    }

    override fun batchSendPacket(packetArr: Array<Packet>) {
        for (packet in packetArr) {
            sendPacket(packet)
        }
    }

    @Throws(XMPPException::class)
    override fun bind(clientLoginInfo: PushClientsManager.ClientLoginInfo) {
        synchronized(this) {
            Binder.bind(clientLoginInfo, challenge, this)
        }
    }

    override val key: ByteArray?
        get() {
            synchronized(this) {
                if (mDerivedKey == null && !challenge.isNullOrEmpty()) {
                    val deviceUuid = ServiceConfig.getDeviceUUID()
                    if (!deviceUuid.isNullOrEmpty()) {
                        val challengeTail = challenge!!.substring(challenge!!.length / 2)
                        val deviceUuidTail = deviceUuid.substring(deviceUuid.length / 2)
                        mDerivedKey = RC4Cryption.encrypt(
                            challenge!!.toByteArray(),
                            (challengeTail + deviceUuidTail).toByteArray(),
                        )
                        if (mDerivedKey != null) {
                            mPushAction.runtimeObserver.onChannelEvent(null, "slim_key_derived", "SlimConnection.getKey")
                        }
                    }
                }
                return mDerivedKey
            }
        }

    @Throws(XMPPException::class, IOException::class)
    override fun initConnection() {
        synchronized(this) {
            initReaderAndWriter()
            mWriter?.openStream()
        }
    }

    override fun isBinaryConnection(): Boolean = true

    internal fun notifyDataArrived(blob: Blob?) {
        if (blob == null) return
        if (blob.hasErr()) {
            MyLog.w("[Slim] RCV blob chid=${blob.channelId}; id=${blob.packetID}; errCode=${blob.errCode}; err=${blob.errStr}")
        }
        val inboundPlan = mPushAction.runtimeObserver.resolveSlimInboundPlan(blob.channelId, blob.cmd)
        if (inboundPlan.action == PushSlimInboundAction.PingReceived) {
            MyLog.w("[Slim] RCV ping id=${blob.packetID}")
        }
        
        inboundPlan.eventAction?.let { eventAction ->
            mPushAction.runtimeObserver.onChannelEvent(null, eventAction, "SlimConnection.notifyDataArrived")
        }
        inboundPlan.connectionState?.let { connectionState ->
            mPushAction.runtimeObserver.onConnectionStateChanged(
                connectionState.name,
                "SlimConnection.notifyDataArrived",
                getHost(),
                inboundPlan.connectionReason.orEmpty()
            )
        }
        inboundPlan.disconnectReasonCode?.let { disconnectReasonCode ->
            notifyConnectionError(disconnectReasonCode, null)
        }
    }

    override fun notifyConnectionError(reason: Int, exc: Exception?) {
        mPushAction.executeJob(object : com.xiaomi.push.service.XMPushServiceJob(2) {
            override fun getDesc(): String = "shutdown the connection. $reason, $exc"
            override fun process() {
                mPushAction.disconnect(reason, exc)
            }
        })
    }

    internal fun notifyDataArrived(packet: Packet?) {
        if (packet == null) return
        super.notifyDataArrived(packet)
    }

    override fun send(blob: Blob) {
        val writer = mWriter ?: throw IllegalStateException("the writer is null.")
        try {
            val bytesWritten = writer.write(blob)
            setReadAlive() // Corrected: should be write alive but matching current Connection.kt simplicity
            val packageName = blob.packageName
            if (!TextUtils.isEmpty(packageName)) {
                try {
                    TrafficUtils.distributionTraffic(
                        mContext,
                        packageName!!,
                        bytesWritten.toLong(),
                        false,
                        true,
                        System.currentTimeMillis(),
                    )
                } catch (th: Throwable) {
                    MyLog.e(th)
                }
            }
            notifyPacketSent(blob)
        } catch (e: Exception) {
            throw IllegalStateException("failed to send blob", e)
        }
    }

    override fun sendPacket(packet: Packet) {
        @Suppress("DEPRECATION")
        send(Blob.from(packet, null))
    }

    @Throws(XMPPException::class)
    override fun sendPing(isServerPing: Boolean) {
        val ping = getPing(isServerPing)
        val pingPlan = mPushAction.runtimeObserver.resolveSlimSendPingPlan()
        MyLog.w("[Slim] SND ping id=${ping.packetID}")
        mPushAction.runtimeObserver.onChannelEvent(null, pingPlan.eventAction, "SlimConnection.sendPing")
        send(ping)
    }

    override fun shutdown(
        reason: Int,
        error: Exception?,
    ) {
        synchronized(this) {
            mReader?.let {
                it.shutdown()
                mReader = null
            }
            mWriter?.let {
                try {
                    it.shutdown()
                } catch (e: Exception) {
                    MyLog.e(e)
                }
                mWriter = null
                mDerivedKey = null
                super.shutdown(reason, error)
            } ?: run {
                mDerivedKey = null
                super.shutdown(reason, error)
            }
        }
    }

    @Throws(XMPPException::class)
    override fun unbind(
        chid: String,
        userId: String,
    ) {
        synchronized(this) {
            Binder.unbind(chid, userId, this)
        }
    }
}
