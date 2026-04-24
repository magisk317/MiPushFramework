package com.xiaomi.smack
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.string.MD5
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.measite.smack.AndroidDebugger
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.XMPushServiceProxy
import io.github.magisk317.mipush.protocol.model.PushConnectionStatusPlan
import io.github.magisk317.mipush.protocol.model.PushConnectionListenerEvent
import com.xiaomi.push.service.PushConstants
import com.xiaomi.slim.Blob
import com.xiaomi.smack.debugger.SmackDebugger
import com.xiaomi.smack.filter.PacketFilter
import com.xiaomi.smack.packet.Packet
import java.io.Reader
import java.io.Writer
import java.util.LinkedList
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

abstract class Connection(
    protected val mPushAction: IPushServiceAction,
    protected val mContext: android.content.Context,
    open var config: ConnectionConfiguration,
) {
    var connectionCounterValue: Int = connectionCounter.getAndIncrement()
    protected val connectionListeners = CopyOnWriteArrayList<ConnectionListener>()
    protected val recvListeners = LinkedHashMap<PacketListener, ListenerWrapper>()
    protected val sendListeners = LinkedHashMap<PacketListener, ListenerWrapper>()
    protected var debugger: SmackDebugger? = null
    protected var reader: Reader? = null
    protected var writer: Writer? = null
    
    var challenge: String? = null
    var connectStatus = 0
    var errorCode = 0
    var exc: Exception? = null
    
    var readAlive: Long = 0L
    var writeAlive: Long = 0L

    open val isConnected: Boolean get() = false
    open val isConnecting: Boolean get() = false

    fun addConnectionListener(connectionListener: ConnectionListener) {
        if (!connectionListeners.contains(connectionListener)) {
            connectionListeners.add(connectionListener)
        }
    }

    fun removeConnectionListener(connectionListener: ConnectionListener) {
        connectionListeners.remove(connectionListener)
    }

    fun addPacketListener(packetListener: PacketListener, packetFilter: PacketFilter?) {
        val listenerWrapper = ListenerWrapper(packetListener, packetFilter)
        synchronized(recvListeners) {
            recvListeners.put(packetListener, listenerWrapper)
        }
    }

    fun removePacketListener(packetListener: PacketListener) {
        synchronized(recvListeners) {
            recvListeners.remove(packetListener)
        }
    }

    fun addPacketSendingListener(packetListener: PacketListener, packetFilter: PacketFilter?) {
        val listenerWrapper = ListenerWrapper(packetListener, packetFilter)
        synchronized(sendListeners) {
            sendListeners.put(packetListener, listenerWrapper)
        }
    }

    fun removePacketSendingListener(packetListener: PacketListener) {
        synchronized(sendListeners) {
            sendListeners.remove(packetListener)
        }
    }

    abstract fun connect()
    abstract fun disconnect(reason: Int, error: Exception?)
    abstract fun isBinaryConnection(): Boolean
    abstract fun sendPacket(packet: Packet)
    abstract fun batchSendPacket(packetArr: Array<Packet>)
    
    abstract fun bind(clientLoginInfo: PushClientsManager.ClientLoginInfo)
    abstract fun unbind(chid: String, userId: String)

    open fun send(blob: Blob) {}
    open fun batchSend(blobArray: Array<Blob>) {}
    open fun sendPing(isServerPing: Boolean) {}

    open fun notifyConnectionError(reason: Int, exc: Exception?) {}

    open val key: ByteArray? get() = null
    open fun getHost(): String? = null

    open fun getConnTryTimes(): Int = 0
    open fun getLastPingRecv(): Long = 0L

    fun setReadAlive() {
        readAlive = System.currentTimeMillis()
    }

    fun setWriteAlive() {
        writeAlive = System.currentTimeMillis()
    }

    fun isReadAlive(thresholdMs: Long): Boolean {
        return (System.currentTimeMillis() - readAlive) < thresholdMs
    }

    fun notifyDataArrived(packet: Packet) {
        synchronized(recvListeners) {
            for (wrapper in recvListeners.values) {
                wrapper.notifyListener(packet)
            }
        }
    }

    fun notifyDataArrived(blob: Blob) {
        synchronized(recvListeners) {
            for (wrapper in recvListeners.values) {
                wrapper.notifyListener(blob)
            }
        }
    }

    protected fun notifyPacketSent(packet: Packet) {
        synchronized(sendListeners) {
            for (wrapper in sendListeners.values) {
                wrapper.notifyListener(packet)
            }
        }
    }

    protected fun notifyPacketSent(blob: Blob) {
        synchronized(sendListeners) {
            for (wrapper in sendListeners.values) {
                wrapper.notifyListener(blob)
            }
        }
    }

    fun notifyConnectionEvent(event: PushConnectionListenerEvent) {
        val plan = mPushAction.runtimeObserver.planConnectionEvent(event)
        MyLog.w("[Connection] Event ${event.name}: ${plan.eventAction}")
    }

    protected open fun shutdown(reason: Int, error: Exception?) {
        connectStatus = 2
        errorCode = reason
        exc = error
        notifyConnectionEvent(PushConnectionListenerEvent.ConnectionClosed)
        for (listener in connectionListeners) {
            listener.connectionClosed(this, reason, error)
        }
    }

    protected fun notifyConnectionStarted() {
        notifyConnectionEvent(PushConnectionListenerEvent.Connected)
        for (listener in connectionListeners) {
            listener.connectionStarted(this)
        }
    }

    protected fun notifyReconnectionSuccessful() {
        notifyConnectionEvent(PushConnectionListenerEvent.Connected)
        for (listener in connectionListeners) {
            listener.reconnectionSuccessful(this)
        }
    }

    protected fun notifyReconnectionFailed(error: Exception) {
        notifyConnectionEvent(PushConnectionListenerEvent.ReconnectionFailed)
        for (listener in connectionListeners) {
            listener.reconnectionFailed(this, error)
        }
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

        const val ERR_TCP_TIMEOUT = 1
        const val ERR_TCP_CONNRESET = 2
        const val ERR_TCP_BROKEN_PIPE = 3
        const val ERR_TCP_OTHER = 4
        const val ERR_BOSH = 5
        const val ERR_TCP_UKNOWNHOST = 6
        const val ERR_XMPP = 7
        const val ERR_UNKNOWN = 8
        const val ERR_TCP_NETUNREACH = 9
        const val ERR_TCP_CONNREFUSED = 10
        const val ERR_TCP_NOACCESS = 11
        const val ERR_TCP_NOROUTETOHOST = 12
        const val ERR_TCP_INVALARG = 13
    }
}
