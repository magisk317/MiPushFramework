package com.xiaomi.smack

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.push.service.XMPushServiceProxy
import com.xiaomi.push.service.PushSocketHostSelectionPlan
import com.xiaomi.push.service.PushShortConnectionPlan
import com.xiaomi.push.service.PushSocketFailurePlan
import com.xiaomi.push.service.PushConnectionListenerEvent
import com.xiaomi.push.service.PushClientsManager
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/qa/h.java
 * Stock class name is obfuscated as qa.h; this file keeps the deobfuscated com.xiaomi.smack.SocketConnection API.
 */
open class SocketConnection(
    pushAction: IPushServiceAction,
    context: android.content.Context,
    connectionConfiguration: ConnectionConfiguration,
) : Connection(pushAction, context, connectionConfiguration) {
    protected var socket: Socket? = null
    protected var mHost: String? = null

    @Throws(XMPPException::class)
    override fun connect() {
        val observer = mPushAction.runtimeObserver
        val planAttempt = observer.resolveConnectionAttemptPlan(isConnected, isConnecting)
        
        MyLog.w("[Socket] Connect attempt plan: ${planAttempt.action} (${planAttempt.eventAction})")
        
        // ... (Logic for connection attempt)
        connectInternal()
    }

    @Throws(XMPPException::class, IOException::class)
    protected open fun initConnection() {
        // To be overridden by SlimConnection
    }

    protected fun connectInternal() {
        val targetHost = config.host ?: "app.chat.xiaomi.net"
        val fallbackHosts = listOf<String>() // Usually from Fallback object
        
        val observer = XMPushServiceProxy.get()?.runtimeObserver
        val plan = observer?.resolveCandidateHosts(targetHost, fallbackHosts)
            ?: PushSocketHostSelectionPlan(listOf(targetHost), "socket_connect_direct_host")

        MyLog.w("[Socket] Candidate hosts plan: ${plan.eventAction}")

        for (curHost in plan.candidateHosts) {
            try {
                MyLog.w("[Socket] Trying to connect to $curHost")
                val start = System.currentTimeMillis()
                val sock = Socket()
                sock.connect(InetSocketAddress(curHost, config.port), 10000)
                sock.tcpNoDelay = true
                socket = sock
                mHost = curHost
                initConnection()
                notifyConnectionStarted()
                MyLog.w("[Socket] Connected to $curHost in ${System.currentTimeMillis() - start}ms")
                return
            } catch (e: Exception) {
                MyLog.e("[Socket] Failed to connect to $curHost: ${e.message}")
                val planFailure = observer?.planFailureRetry(curHost, null)
                    ?: PushSocketFailurePlan(false, "socket_connect_failure_default")
                if (!planFailure.shouldContinue) {
                    throw XMPPException("Failed to connect to all hosts", e)
                }
            }
        }
    }

    override fun disconnect(reason: Int, error: Exception?) {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        shutdown(reason, error)
    }

    override fun isBinaryConnection(): Boolean = false
    override fun sendPacket(packet: com.xiaomi.smack.packet.Packet) {}
    override fun batchSendPacket(packetArr: Array<com.xiaomi.smack.packet.Packet>) {}
    override fun bind(clientLoginInfo: PushClientsManager.ClientLoginInfo) {}
    override fun unbind(chid: String, userId: String) {}

    override val host: String?
        get() = mHost

    override val isConnected: Boolean
        get() = socket?.isConnected ?: false

    override val isConnecting: Boolean
        get() = false // Simplified
}
