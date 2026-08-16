package com.xiaomi.smack

import android.os.SystemClock
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.network.Fallback
import com.xiaomi.network.Host
import com.xiaomi.network.HostManager
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.push.service.XMPushServiceJob
import com.xiaomi.push.service.heartbeat.HeartbeatStrategyManager
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet
import com.xiaomi.smack.util.TaskExecutor
import java.net.Socket

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/qa/h.java
 * Stock class name is obfuscated as qa.h; this file keeps the deobfuscated com.xiaomi.smack.SocketConnection API.
 */
abstract class SocketConnection(
    pushAction: IPushServiceAction,
    context: android.content.Context,
    connectionConfiguration: ConnectionConfiguration,
) : Connection(pushAction, context, connectionConfiguration) {
    protected var socket: Socket? = null
    private var connectedHost: String? = null
    protected var failedException: Exception? = null
    protected var lastConnectedTime: Long = 0L
    private var currentShortConnectionCount: Int = 0

    @Throws(XMPPException::class)
    override fun connect() {
        synchronized(this) {
            if (isConnected || isConnecting) {
                MyLog.w("WARNING: current xmpp has connected")
                return
            }
            setConnectionStatus(ConnectionConfiguration.CONNECT_STATUS_CONNECTING, 0, null)
            connectInternal()
        }
    }

    @Throws(XMPPException::class, java.io.IOException::class)
    protected abstract fun initConnection()

    @Throws(XMPPException::class)
    protected fun connectInternal() {
        val targetHost = config.getHost()
        val fallback = getFallback(targetHost)
        val candidateHosts = fallback?.getHosts(true).orEmpty().ifEmpty { arrayListOf(targetHost) }
        val initialConnectionPoint = Network.getActiveConnPoint(mContext)
        val failures = StringBuilder()
        failedException = null
        lastConnectedTime = 0L

        for (candidateHost in candidateHosts) {
            val startedAt = System.currentTimeMillis()
            connTimes += 1
            try {
                MyLog.w("begin to connect to $candidateHost")
                socket = createSocket().also { createdSocket ->
                    createdSocket.connect(Host.from(candidateHost, config.port), CONNECTION_TIMEOUT_MS)
                    createdSocket.tcpNoDelay = true
                }
                MyLog.w("tcp connected")
                connectedHost = candidateHost
                initConnection()
                connectTime = System.currentTimeMillis() - startedAt
                fallback?.succeedHost(candidateHost, connectTime, 0L)
                lastConnectedTime = SystemClock.elapsedRealtime()
                persistHostState()
                MyLog.w("connected to $candidateHost in $connectTime")
                return
            } catch (error: Exception) {
                failedException = error
                closeSocketQuietly()
                MyLog.e("SMACK: Could not connect to:$candidateHost")
                failures
                    .append("SMACK: Could not connect to ")
                    .append(candidateHost)
                    .append(" port:")
                    .append(config.port)
                    .append(" err:")
                    .append(error.javaClass.simpleName)
                    .append('\n')
                fallback?.failedHost(
                    candidateHost,
                    System.currentTimeMillis() - startedAt,
                    0L,
                    error,
                )
                if (!TextUtils.equals(initialConnectionPoint, Network.getActiveConnPoint(mContext))) {
                    break
                }
            }
        }

        persistHostState()
        throw XMPPException(
            failures.toString(),
            failedException ?: java.io.IOException("No connection candidate succeeded"),
        )
    }

    override fun disconnect(reason: Int, error: Exception?) {
        shutdown(reason, error)
        if ((error != null || reason == SHORT_CONNECTION_REASON) && lastConnectedTime != 0L) {
            sinkDownHost(error)
        }
    }

    override fun batchSend(blobArray: Array<Blob>) {
        throw XMPPException("Don't support send Blob")
    }

    override fun batchSendPacket(packetArr: Array<Packet>) {
        packetArr.forEach(::sendPacket)
    }

    open fun createSocket(): Socket = Socket()

    override fun notifyConnectionError(reason: Int, exc: Exception?) {
        mPushAction.executeJob(object : XMPushServiceJob(2) {
            override fun getDesc(): String = "shutdown the connection. $reason, $exc"

            override fun process() {
                mPushAction.disconnect(reason, exc)
            }
        })
    }

    final override fun sendPing(isServerPing: Boolean) {
        val sentAtElapsedRealtime = SystemClock.elapsedRealtime()
        val sentAtWallClock = System.currentTimeMillis()
        sendPingInternal(isServerPing)
        if (isServerPing) return

        runCatching { mPushAction.runtimeObserver.onPingSent(sentAtWallClock) }

        // Stock qa.h.p calls v.j() after sending a client ping so timeout learning keys off
        // the net id observed at send time.
        runCatching { HeartbeatStrategyManager.getInstance(mContext).onPingSent() }

        mPushAction.executeJobDelayed(
            object : XMPushServiceJob(PING_TIMEOUT_JOB_TYPE) {
                override fun getDesc(): String = "check the ping-pong.$sentAtWallClock"

                override fun process() {
                    Thread.yield()
                    if (!isConnected || isReadAlive(sentAtElapsedRealtime)) return
                    // Stock qa.h$a calls v.k() before disconnecting on ping-pong timeout so the
                    // stable strategy can learn a short interval after enough failures.
                    runCatching { HeartbeatStrategyManager.getInstance(mContext).onPingTimeout() }
                    runCatching { mPushAction.runtimeObserver.onPingTimeout(System.currentTimeMillis()) }
                    mPushAction.disconnect(PING_TIMEOUT_REASON, null)
                }
            },
            PING_TIMEOUT_MS,
        )
    }

    @Throws(XMPPException::class)
    protected abstract fun sendPingInternal(isServerPing: Boolean)

    override fun shutdown(reason: Int, error: Exception?) {
        synchronized(this) {
            if (connectStatus == ConnectionConfiguration.CONNECT_STATUS_DISCONNECT) return
            super.shutdown(reason, error)
            clearChallenge()
            closeSocketQuietly()
            setReadAliveAtZero()
            writeAlive = 0L
        }
    }

    override val host: String?
        get() = connectedHost

    val resolvedIp: String?
        get() = runCatching { socket?.inetAddress?.hostAddress }.getOrNull()

    private fun getFallback(host: String): Fallback? {
        return runCatching {
            HostManager.getInstance().getFallbacksByHost(host, false)?.also { fallback ->
                if (!fallback.isEffective()) {
                    TaskExecutor.execute {
                        runCatching { HostManager.getInstance().getFallbacksByHost(host, true) }
                    }
                }
            }
        }.getOrElse {
            MyLog.w("get fallback for $host failed: ${it.message}")
            null
        }
    }

    private fun sinkDownHost(error: Exception?) {
        if (SystemClock.elapsedRealtime() - lastConnectedTime >= SHORT_CONNECTION_THRESHOLD_MS) {
            currentShortConnectionCount = 0
            return
        }
        if (!Network.hasNetwork(mContext)) return

        currentShortConnectionCount += 1
        if (currentShortConnectionCount < MAX_SHORT_CONNECTION_COUNT) return

        host?.let { failedHost ->
            MyLog.w("max short conn time reached, sink down current host:$failedHost")
            runCatching {
                HostManager.getInstance()
                    .getFallbacksByHost(ConnectionConfiguration.getXmppServerHost(), false)
                    ?.failedHost(failedHost, 0L, 0L, error)
                HostManager.getInstance().persist()
            }.onFailure { MyLog.w("sink down host failed: ${it.message}") }
        }
        currentShortConnectionCount = 0
    }

    private fun persistHostState() {
        runCatching { HostManager.getInstance().persist() }
    }

    private fun closeSocketQuietly() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun setReadAliveAtZero() {
        resetReadAlive()
    }

    private companion object {
        private const val CONNECTION_TIMEOUT_MS = 8_000
        private const val MAX_SHORT_CONNECTION_COUNT = 2
        private const val PING_TIMEOUT_JOB_TYPE = 13
        private const val PING_TIMEOUT_MS = 10_000L
        private const val PING_TIMEOUT_REASON = 22
        private const val SHORT_CONNECTION_REASON = 18
        private const val SHORT_CONNECTION_THRESHOLD_MS = 300_000L
    }
}
