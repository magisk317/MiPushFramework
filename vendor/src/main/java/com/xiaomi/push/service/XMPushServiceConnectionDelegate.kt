package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.slim.Blob
import com.xiaomi.smack.Connection
import com.xiaomi.smack.SmackConfiguration
import com.xiaomi.smack.XMPPException
import com.xiaomi.smack.filter.PacketFilter
import com.xiaomi.smack.packet.Packet

class XMPushServiceConnectionDelegate(
    private val service: XMPushServiceCore,
) {
    fun connect() {
        val currentConnection = service.currentConnection
        MyLog.w(
            "connect() currentConnection=" +
                if (currentConnection == null) {
                    "null"
                } else {
                    "connectionInstanceId=${currentConnection.connectionInstanceId} " +
                        "instance=${currentConnection.hashCode()} connected=${currentConnection.isConnected} " +
                        "connecting=${currentConnection.isConnecting} host=${currentConnection.host} " +
                        "network=${Network.getActiveNetworkSnapshot(service)}"
                }
        )
        val plan = service.runtimeObserver.resolveConnectionAttemptPlan(
            isConnected = currentConnection?.isConnected == true,
            isConnecting = currentConnection?.isConnecting == true,
        )
        ReconnectDebugLog.w(
            "connection_attempt action=${plan.action} event=${plan.eventAction} " +
                "network=${com.xiaomi.channel.commonutils.network.Network.hasNetwork(service)} " +
                "activeClients=${PushClientsManager.getInstance().getActiveClientCount()} " +
                "isConnected=${currentConnection?.isConnected == true} " +
                "isConnecting=${currentConnection?.isConnecting == true}"
        )
        service.runtimeObserver.onChannelEvent(null, plan.eventAction, "XMPushServiceConnectionDelegate.connect")
        when (plan.action) {
            PushConnectionAttemptAction.SkipConnecting -> {
                MyLog.e("try to connect while connecting.")
                return
            }

            PushConnectionAttemptAction.SkipConnected -> {
                MyLog.e("try to connect while is connected.")
                return
            }

            else -> Unit
        }
        service.connectionConfiguration.setConnectionPoint(Network.getActiveConnPoint(service))
        connectBySlim()
        if (service.currentConnection == null) {
            ReconnectDebugLog.e("connect_attempt_ended_without_connection")
            PushClientsManager.getInstance().notifyConnectionFailed(service)
            service.broadcastNetworkAvailable(false)
        }
    }

    fun broadcastNetworkAvailable(available: Boolean) {
        try {
            if (com.xiaomi.channel.commonutils.android.SystemUtils.isBootCompleted()) {
                if (available) {
                    service.sendBroadcast(android.content.Intent("miui.intent.action.NETWORK_CONNECTED"))
                    service.networkListenersSnapshot.forEach { it.onNetwrokAvaible() }
                } else {
                    service.sendBroadcast(android.content.Intent("miui.intent.action.NETWORK_BLOCKED"))
                }
            }
        } catch (e: Exception) {
            MyLog.e(e)
        }
    }

    fun checkAlive(fromServerPing: Boolean) {
        service.lastAliveAt = System.currentTimeMillis()
        val plan = service.runtimeObserver.resolveCheckAlivePlan(service.isConnected, Network.hasNetwork(service))
        service.runtimeObserver.onChannelEvent(null, plan.eventAction, "XMPushServiceConnectionDelegate.checkAlive")
        when (plan.action) {
            PushCheckAliveAction.ScheduleConnect -> service.scheduleConnect(true)
            PushCheckAliveAction.Ping -> service.executeJobNow(PingJob(service, fromServerPing))
            else -> {
                service.executeJobNow(DisconnectJob(service, 17, null))
                service.scheduleConnect(true)
            }
        }
    }

    fun disconnect(reason: Int, error: Exception?) {
        val currentConnection = service.currentConnection
        MyLog.w("disconnect ${service.hashCode()}, ${currentConnection?.hashCode()}")
        if (currentConnection != null) {
            currentConnection.disconnect(reason, error)
            service.clearCurrentConnection()
        }
        service.removeJobs(7)
        service.removeJobs(4)
        PushClientsManager.getInstance().resetAllClients(service, reason)
    }

    fun closeChannel(chid: String, userId: String, reason: Int, reasonMessage: String, errorType: String) {
        PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(chid, userId)?.let { client ->
            service.executeJob(UnbindJob(service, client, reason, errorType, reasonMessage))
        }
        PushClientsManager.getInstance().deactivateClient(chid, userId)
    }

    fun scheduleRebindChannel(clientLoginInfo: PushClientsManager.ClientLoginInfo?) {
        if (clientLoginInfo == null) {
            return
        }
        val nextRetryInterval = clientLoginInfo.getNextRetryInterval()
        MyLog.w("schedule rebind job in ${nextRetryInterval / 1000}")
        service.executeJobDelayed(BindJob(service, clientLoginInfo), nextRetryInterval)
    }

    @Throws(XMPPException::class)
    fun batchSendPacket(blobs: Array<Blob>) {
        requireConnection().batchSend(blobs)
    }

    @Throws(XMPPException::class)
    fun batchSendPacket(packets: Array<Packet>) {
        requireConnection().batchSendPacket(packets)
    }

    @Throws(XMPPException::class)
    fun sendPacket(blob: Blob) {
        requireConnection().send(blob)
    }

    @Throws(XMPPException::class)
    fun sendPacket(packet: Packet) {
        requireConnection().sendPacket(packet)
    }

    fun setConnectingTimeout() {
        service.executeJobDelayed(
            object : XMPushServiceCore.Job(XMPushServiceJob.TYPE_CONNECTING_TIMEOUT) {
                override fun getDesc(): String = "disconnect because of connecting timeout"

                override fun process() {
                    if (service.isConnecting) {
                        ReconnectDebugLog.w(
                            "connecting_timeout fired connected=${service.isConnected} " +
                                "connecting=${service.isConnecting} " +
                                "host=${service.currentConnection?.host}"
                        )
                        service.disconnect(18, null)
                    }
                }
            },
            15000L,
        )
    }

    fun sendPongIfNeed() {
        if (System.currentTimeMillis() - service.lastAliveAt >= SmackConfiguration.keepAliveInterval &&
            Network.isConnected(service)
        ) {
            checkAlive(true)
        }
    }

    private fun connectBySlim() {
        try {
            // MiPush SDK 3.7.9 and stock XMSF 7.4.67-C create one service-owned SlimConnection and
            // reuse it across reconnects. Recreating it here discarded stock's short-connection
            // count and cached failure history before either could influence host selection.
            val slimConnection = service.slimConnection
            MyLog.w(
                "connectBySlim connectionInstanceId=${slimConnection.connectionInstanceId} " +
                    "socketGeneration=${slimConnection.socketGeneration} instance=${slimConnection.hashCode()} " +
                    "current=${service.currentConnection?.hashCode()} " +
                    "network=${Network.getActiveNetworkSnapshot(service)}",
            )
            slimConnection.addPacketListener(
                service.servicePacketListener,
                PacketFilter { true },
            )
            slimConnection.connect()
            service.currentConnection = slimConnection
            // TCP/reader 初始化完成后，仍可能等待服务端 challenge；避免握手卡死时永久阻塞重连。
            service.setConnectingTimeout()
            MyLog.w(
                "connectBySlim connected connectionInstanceId=${slimConnection.connectionInstanceId} " +
                    "socketGeneration=${slimConnection.socketGeneration} " +
                    "current=${service.currentConnection?.hashCode()} host=${service.currentConnection?.host} " +
                    "network=${Network.getActiveNetworkSnapshot(service)}",
            )
        } catch (e: XMPPException) {
            MyLog.e("fail to create Slim connection", e)
            service.slimConnection.disconnect(3, e)
        }
    }

    @Throws(XMPPException::class)
    private fun requireConnection(): Connection {
        return service.currentConnection ?: throw XMPPException("try send msg while connection is null.")
    }
}
