package com.xiaomi.smack

import co.touchlab.kermit.Logger
import com.xiaomi.channel.commonutils.string.MD5
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.measite.smack.AndroidDebugger
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.slim.Blob
import com.xiaomi.smack.debugger.SmackDebugger
import com.xiaomi.smack.filter.PacketFilter
import com.xiaomi.smack.packet.Packet
import java.io.Reader
import java.io.Writer
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/qa/b.java
 * Stock class name is obfuscated as qa.b; this file keeps the deobfuscated com.xiaomi.smack.Connection API.
 */
abstract class Connection(
    protected val mPushAction: IPushServiceAction,
    protected val mContext: android.content.Context,
    open var config: ConnectionConfiguration,
) {
    val connectionInstanceId: Int = connectionCounter.getAndIncrement()
    val connectionCounterValue: Int
        get() = connectionInstanceId
    protected val connectionListeners = CopyOnWriteArraySet<ConnectionListener>()
    protected val recvListeners = ConcurrentHashMap<PacketListener, ListenerWrapper>()
    protected val sendListeners = ConcurrentHashMap<PacketListener, ListenerWrapper>()
    protected var debugger: SmackDebugger? = null
    protected var reader: Reader? = null
    protected var writer: Writer? = null
    protected var connTimes: Int = 0
    protected var connectTime: Long = -1L
    protected var connectionPoint: String = ""

    var challenge: String = ""
        private set
    var connectStatus: Int = ConnectionConfiguration.CONNECT_STATUS_DISCONNECT
        private set
    var errorCode: Int = 0
        private set
    var exc: Exception? = null
        private set

    private val cachedStatus = LinkedList<Pair<Int, Long>>()
    private var readAlive: Long = 0L
    protected var writeAlive: Long = 0L

    open val isConnected: Boolean
        get() = connectStatus == ConnectionConfiguration.CONNECT_STATUS_CONNECTED

    open val isConnecting: Boolean
        get() = connectStatus == ConnectionConfiguration.CONNECT_STATUS_CONNECTING

    open val isDisconnected: Boolean
        get() = connectStatus == ConnectionConfiguration.CONNECT_STATUS_DISCONNECT

    abstract val host: String?

    open val connectionId: String?
        get() = challenge

    open val user: String?
        get() = config.getUsername()

    open val serviceName: String?
        get() = config.serviceName

    abstract val isBinaryConnection: Boolean

    abstract fun batchSend(blobArray: Array<Blob>)
    abstract fun batchSendPacket(packetArr: Array<Packet>)
    abstract fun bind(clientLoginInfo: PushClientsManager.ClientLoginInfo)
    abstract fun connect()
    abstract fun initConnection()
    abstract fun send(blob: Blob)
    abstract fun sendPacket(packet: Packet)
    open fun notifyConnectionError(reason: Int, exc: Exception?) {
        mPushAction.disconnect(reason, exc)
    }
    open fun sendPing(isServerPing: Boolean) {
        sendPingInternal(isServerPing)
    }

    abstract fun disconnect(reason: Int = 0, error: Exception? = null)

    protected abstract fun sendPingInternal(isServerPing: Boolean)
    abstract fun unbind(chid: String, userId: String)

    open val key: ByteArray?
        get() = null

    init {
        initDebugger()
    }

    fun addConnectionListener(connectionListener: ConnectionListener) {
        connectionListeners.add(connectionListener)
    }

    fun removeConnectionListener(connectionListener: ConnectionListener) {
        connectionListeners.remove(connectionListener)
    }

    fun addPacketListener(packetListener: PacketListener, packetFilter: PacketFilter?) {
        recvListeners[packetListener] = ListenerWrapper(packetListener, packetFilter)
    }

    fun removePacketListener(packetListener: PacketListener) {
        recvListeners.remove(packetListener)
    }

    fun addPacketSendingListener(packetListener: PacketListener, packetFilter: PacketFilter?) {
        sendListeners[packetListener] = ListenerWrapper(packetListener, packetFilter)
    }

    fun removePacketSendingListener(packetListener: PacketListener) {
        sendListeners.remove(packetListener)
    }

    fun getConnTryTimes(): Int = connTimes

    fun getLastPingRecv(): Long = readAlive

    fun getLastPingSend(): Long = writeAlive

    fun hasCustomPacketListener(packetListener: PacketListener): Boolean =
        recvListeners.containsKey(packetListener)

    fun hasCustomPacketSendingListener(packetListener: PacketListener): Boolean =
        sendListeners.containsKey(packetListener)

    @Synchronized
    fun setReadAlive() {
        readAlive = android.os.SystemClock.elapsedRealtime()
    }

    @Synchronized
    fun setWriteAlive() {
        writeAlive = android.os.SystemClock.elapsedRealtime()
    }

    @Synchronized
    protected fun resetReadAlive() {
        readAlive = 0L
    }

    @Synchronized
    fun isReadAlive(sinceElapsedRealtime: Long): Boolean {
        return readAlive >= sinceElapsedRealtime
    }

    fun clearCachedStatus() {
        synchronized(cachedStatus) {
            cachedStatus.clear()
        }
    }

    fun resetConnTryTimes() {
        connTimes = 0
    }

    fun resetConnectTime() {
        connectTime = -1L
    }

    fun isAlwaysFailed(): Boolean {
        synchronized(cachedStatus) {
            val cutoff = System.currentTimeMillis() - EFFECTIVE_STATUS_MS
            cachedStatus.removeAll { (_, timestamp) -> timestamp < cutoff }
            return cachedStatus.size >= MAX_STATUS_COUNT
        }
    }

    fun notifyDataArrived(packet: Packet) {
        for (wrapper in recvListeners.values) {
            wrapper.notifyListener(packet)
        }
    }

    fun notifyDataArrived(blob: Blob) {
        for (wrapper in recvListeners.values) {
            wrapper.notifyListener(blob)
        }
    }

    protected fun notifyPacketSent(packet: Packet) {
        for (wrapper in sendListeners.values) {
            wrapper.notifyListener(packet)
        }
    }

    protected fun notifyPacketSent(blob: Blob) {
        for (wrapper in sendListeners.values) {
            wrapper.notifyListener(blob)
        }
    }

    @Synchronized
    fun setChallenge(value: String) {
        if (connectStatus == ConnectionConfiguration.CONNECT_STATUS_CONNECTING) {
            val digest = MD5.MD5_32(value)?.take(8).orEmpty()
            Logger.w { "setChallenge hash = $digest" }
            challenge = value
            setConnectionStatus(ConnectionConfiguration.CONNECT_STATUS_CONNECTED, 0, null)
        } else {
            Logger.w { "ignore setChallenge because connection was disconnected" }
        }
    }

    fun setConnectionStatus(status: Int, reason: Int, error: Exception?) {
        val previousStatus = connectStatus
        if (status != previousStatus) {
            Logger.w {
                "update the connection status connectionInstanceId=$connectionInstanceId " +
                    "${statusDescription(previousStatus)} -> ${statusDescription(status)} : $reason"
            }
        }

        if (Network.hasNetwork(mContext)) {
            synchronized(cachedStatus) {
                if (status == ConnectionConfiguration.CONNECT_STATUS_CONNECTED) {
                    cachedStatus.clear()
                } else {
                    cachedStatus.add(status to System.currentTimeMillis())
                    if (cachedStatus.size > MAX_STATUS_COUNT) {
                        cachedStatus.removeFirst()
                    }
                }
            }
        }

        when (status) {
            ConnectionConfiguration.CONNECT_STATUS_CONNECTED -> {
                mPushAction.removeJobs(CONNECTING_TIMEOUT_JOB_TYPE)
                if (previousStatus != ConnectionConfiguration.CONNECT_STATUS_CONNECTING) {
                    Logger.w { "try set connected while not connecting." }
                }
                connectStatus = status
                connectionListeners.forEach { it.reconnectionSuccessful(this) }
            }

            ConnectionConfiguration.CONNECT_STATUS_CONNECTING -> {
                if (previousStatus != ConnectionConfiguration.CONNECT_STATUS_DISCONNECT) {
                    Logger.w { "try set connecting while not disconnected." }
                }
                connectStatus = status
                connectionListeners.forEach { it.connectionStarted(this) }
            }

            ConnectionConfiguration.CONNECT_STATUS_DISCONNECT -> {
                mPushAction.removeJobs(CONNECTING_TIMEOUT_JOB_TYPE)
                when (previousStatus) {
                    ConnectionConfiguration.CONNECT_STATUS_CONNECTING -> {
                        val failure = error
                            ?: CancellationException("disconnect while connecting")
                        connectionListeners.forEach { it.reconnectionFailed(this, failure) }
                    }

                    ConnectionConfiguration.CONNECT_STATUS_CONNECTED -> {
                        connectionListeners.forEach { it.connectionClosed(this, reason, error) }
                    }
                }
                connectStatus = status
            }
        }

        errorCode = reason
        exc = error
    }

    protected fun clearChallenge() {
        challenge = ""
    }

    protected open fun shutdown(reason: Int, error: Exception?) {
        setConnectionStatus(ConnectionConfiguration.CONNECT_STATUS_DISCONNECT, reason, error)
    }

    private fun initDebugger() {
        if (!config.isDebuggerEnabled() || debugger != null) return

        val debuggerClass = runCatching {
            System.getProperty("smack.debuggerClass")?.let { className ->
                Class.forName(className)
            }
        }.getOrNull()
        debugger = if (debuggerClass == null) {
            AndroidDebugger(this)
        } else {
            try {
                debuggerClass
                    .getConstructor(Connection::class.java, Writer::class.java, Reader::class.java)
                    .newInstance(this) as SmackDebugger
            } catch (error: Exception) {
                throw IllegalArgumentException("Can't initialize the configured debugger!", error)
            }
        }
    }

    private fun statusDescription(status: Int): String = when (status) {
        ConnectionConfiguration.CONNECT_STATUS_CONNECTED -> "connected"
        ConnectionConfiguration.CONNECT_STATUS_CONNECTING -> "connecting"
        ConnectionConfiguration.CONNECT_STATUS_DISCONNECT -> "disconnected"
        else -> "unknown"
    }

    class ListenerWrapper(
        private val listener: PacketListener,
        private val filter: PacketFilter?,
    ) {
        fun notifyListener(packet: Packet) {
            if (filter == null || filter.accept(packet)) {
                listener.processPacket(packet)
            }
        }

        fun notifyListener(blob: Blob) {
            listener.process(blob)
        }
    }

    companion object {
        private val connectionCounter = AtomicInteger(0)
        var DEBUG_ENABLED = false

        private const val CONNECTING_TIMEOUT_JOB_TYPE = 10
        private const val EFFECTIVE_STATUS_MS = 1_800_000L
        private const val MAX_STATUS_COUNT = 6

        const val ERR_BOSH = 499
        const val ERR_TCP_BROKEN_PIPE = 110
        const val ERR_TCP_CONNREFUSED = 103
        const val ERR_TCP_CONNRESET = 109
        const val ERR_TCP_INVALARG = 106
        const val ERR_TCP_NETUNREACH = 102
        const val ERR_TCP_NOACCESS = 101
        const val ERR_TCP_NOROUTETOHOST = 104
        const val ERR_TCP_OTHER = 199
        const val ERR_TCP_READ_TIMEOUT = 108
        const val ERR_TCP_TIMEOUT = 105
        const val ERR_TCP_UKNOWNHOST = 107
        const val ERR_UNKNOWN = 0
        const val ERR_XMPP = 399
    }
}
