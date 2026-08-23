package com.xiaomi.slim

import co.touchlab.kermit.Logger
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
                Logger.w { "[Slim] delay bind chid=${clientLoginInfo.chid} as challenge is missing" }
                return
            }
            Logger.w {
                "[Slim] bind request instance=${hashCode()} chid=${clientLoginInfo.chid} " +
                    "status=${clientLoginInfo.status} shuttingDown=$isShuttingDown host=$host"
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
                        val c = challenge
                        val challengeTail = c.substring(c.length / 2)
                        val deviceUuidTail = deviceUuid.substring(deviceUuid.length / 2)
                        mDerivedKey = RC4Cryption.encrypt(
                            c.toByteArray(),
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
            val wasShuttingDown = isShuttingDown
            // Reconnection reuses the service-owned SlimConnection instance. Reset the shutdown
            // guard here so a previously closed connection can send bind/register blobs again.
            isShuttingDown = false
            Logger.w {
                "[Slim] initConnection instance=${hashCode()} " +
                    "reusedAfterShutdown=$wasShuttingDown socket=${socket?.hashCode()} host=$host"
            }
            initReaderAndWriter()
            mWriter?.openStream()
        }
    }

    override val isBinaryConnection: Boolean
        get() = true

    internal fun notifyDataArrived(blob: Blob?) {
        if (blob == null) return
        if (blob.hasErr) {
            Logger.w { "[Slim] RCV blob chid=${blob.channelId}; id=${blob.packetID}; errCode=${blob.errCode}; err=${blob.errStr}" }
        }
        val inboundPlan = mPushAction.runtimeObserver.resolveSlimInboundPlan(blob.channelId, blob.cmd)
        if (inboundPlan.action == PushSlimInboundAction.PingReceived) {
            Logger.w { "[Slim] RCV ping id=${blob.packetID}" }
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
        super.notifyDataArrived(blob)
    }

    internal fun notifyDataArrived(packet: Packet?) {
        if (packet == null) return
        super.notifyDataArrived(packet)
    }

    internal fun onChallengeReceived(receivedChallenge: String?, source: String) {
        synchronized(this) {
            if (receivedChallenge.isNullOrEmpty()) {
                Logger.w { "[Slim] RCV challenge missing in CONN response from $source" }
                return
            }
            if (isConnecting) {
                mDerivedKey = null
            }
            setChallenge(receivedChallenge)
            if (challenge == receivedChallenge) {
                Logger.w { "[Slim] RCV challenge accepted from $source" }
            }
        }
    }

    override fun send(blob: Blob) {
        if (isShuttingDown) {
            Logger.w {
                "[Slim] skip sending blob as connection is shutting down " +
                    "instance=${hashCode()} cmd=${blob.cmd} chid=${blob.channelId} packetId=${blob.packetID}"
            }
            return
        }
        val writer = mWriter
        if (writer == null) {
            Logger.w {
                "[Slim] skip sending blob because writer is null " +
                    "instance=${hashCode()} cmd=${blob.cmd} chid=${blob.channelId} packetId=${blob.packetID}"
            }
            notifyConnectionError(10, IOException("the writer is null."))
            return
        }
        try {
            Logger.w {
                "[Slim] send blob instance=${hashCode()} cmd=${blob.cmd} " +
                    "chid=${blob.channelId} packetId=${blob.packetID} host=$host"
            }
            val bytesWritten = writer.write(blob)
            setWriteAlive()
            val packageName = blob.packageName
            if (!packageName.isNullOrEmpty()) {
                try {
                    TrafficUtils.distributionTraffic(
                        mContext,
                        packageName,
                        bytesWritten.toLong(),
                        false,
                        true,
                        System.currentTimeMillis(),
                    )
                } catch (th: Throwable) {
                    Logger.e(th) { "Traffic distribution failed" }
                }
            }
            notifyPacketSent(blob)
        } catch (e: Exception) {
            Logger.w(e) { "[Slim] send blob failed: $e" }
            notifyConnectionError(10, e)
        }
    }

    override fun sendPacket(packet: Packet) {
        @Suppress("DEPRECATION")
        send(Blob.from(packet, null))
    }

    @Throws(XMPPException::class)
    override fun sendPingInternal(isServerPing: Boolean) {
        val ping = getPing(isServerPing)
        val pingPlan = mPushAction.runtimeObserver.resolveSlimSendPingPlan()
        Logger.w { "[Slim] SND ping id=${ping.packetID}" }
        mPushAction.runtimeObserver.onChannelEvent(null, pingPlan.eventAction, "SlimConnection.sendPing")
        send(ping)
    }

    override fun shutdown(
        reason: Int,
        error: Exception?,
    ) {
        synchronized(this) {
            if (isShuttingDown) return
            Logger.w {
                "[Slim] shutdown instance=${hashCode()} reason=$reason " +
                    "error=${error?.javaClass?.simpleName}:${error?.message}"
            }
            isShuttingDown = true
            mReader?.let {
                it.shutdown()
                mReader = null
            }
            mWriter?.let {
                try {
                    it.shutdown()
                } catch (e: Exception) {
                    Logger.e(e) { "Writer shutdown failed" }
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
