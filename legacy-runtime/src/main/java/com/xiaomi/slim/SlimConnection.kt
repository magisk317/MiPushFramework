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

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/pa/i.java
 * Stock class name is obfuscated as pa.i; this file keeps the deobfuscated com.xiaomi.slim.SlimConnection API.
 */
class SlimConnection(
    pushAction: IPushServiceAction,
    context: android.content.Context,
    connectionConfiguration: ConnectionConfiguration,
) : SocketConnection(pushAction, context, connectionConfiguration) {
    private var mReader: BlobReader? = null
    private var mReaderThread: Thread? = null
    private var mWriter: BlobWriter? = null
    private var mDerivedKey: ByteArray? = null
    @Volatile
    private var isShuttingDown = false

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
            if (challenge.isNullOrEmpty()) {
                MyLog.w("[Slim] delay bind chid=${clientLoginInfo.chid} as challenge is missing")
                return
            }
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
        
        if (inboundPlan.action == PushSlimInboundAction.ChallengeReceived) {
            onChallengeReceived(extractChallenge(blob), "SlimConnection.notifyDataArrived")
        }
        
        inboundPlan.eventAction?.let { eventAction ->
            mPushAction.runtimeObserver.onChannelEvent(null, eventAction, "SlimConnection.notifyDataArrived")
        }
        inboundPlan.connectionState?.let { connectionState ->
            mPushAction.runtimeObserver.onConnectionStateChanged(
                connectionState.name,
                "SlimConnection.notifyDataArrived",
                host,
                inboundPlan.connectionReason.orEmpty()
            )
        }
        inboundPlan.disconnectReasonCode?.let { disconnectReasonCode ->
            notifyConnectionError(disconnectReasonCode, null)
        }
    }

    override fun notifyConnectionError(reason: Int, exc: Exception?) {
        if (isShuttingDown) return
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

    private fun extractChallenge(blob: Blob): String? {
        if (blob.cmd == Blob.CMD_CONN) {
            return runCatching {
                ChannelMessage.XMMsgConnResp.parseFrom(blob.payload).challenge
            }.getOrNull()
        }
        return runCatching { String(blob.payload) }.getOrNull()
    }

    internal fun onChallengeReceived(receivedChallenge: String?, source: String) {
        val shouldRebind = synchronized(this) {
            if (receivedChallenge.isNullOrEmpty()) {
                MyLog.w("[Slim] RCV challenge missing in CONN response from $source")
                return
            }
            if (challenge == receivedChallenge) {
                return
            }
            challenge = receivedChallenge
            mDerivedKey = null
            true
        }
        MyLog.w("[Slim] RCV challenge=$receivedChallenge")
        if (shouldRebind) {
            rebindClientsAfterChallenge()
        }
    }

    private fun rebindClientsAfterChallenge() {
        mPushAction.executeJob(object : com.xiaomi.push.service.XMPushServiceJob(0) {
            override fun getDesc(): String = "re-bind after challenge"
            override fun process() {
                PushClientsManager.getInstance().getAllClients().forEach { client ->
                    if (client.status == PushClientsManager.ClientStatus.unbind || client.status == PushClientsManager.ClientStatus.binding) {
                        MyLog.w("[Slim] auto bind chid=${client.chid} after challenge")
                        mPushAction.executeJob(com.xiaomi.push.service.BindJob(mPushAction, client))
                    }
                }
            }
        })
    }

    override fun send(blob: Blob) {
        if (isShuttingDown) {
            MyLog.w("[Slim] skip sending blob as connection is shutting down")
            return
        }
        val writer = mWriter
        if (writer == null) {
            notifyConnectionError(10, IOException("the writer is null."))
            return
        }
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
            MyLog.w("[Slim] send blob failed: $e")
            notifyConnectionError(10, e)
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
            if (isShuttingDown) return
            isShuttingDown = true
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
