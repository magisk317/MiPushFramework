package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.slim.Blob
import com.xiaomi.smack.Connection
import com.xiaomi.smack.SmackConfiguration
import com.xiaomi.smack.XMPPException
import com.xiaomi.smack.filter.PacketFilter
import com.xiaomi.smack.packet.Packet
import com.xiaomi.xmsf.runtime.PushRuntime

class XMPushServiceConnectionDelegate(
    private val service: XMPushService,
) {
    fun connect() {
        val currentConnection = service.currentConnection
        val plan = PushServiceConnectionRuntime.planConnect(
            currentConnection?.isConnecting == true,
            currentConnection?.isConnected == true,
        )
        PushRuntime.observeChannelEvent(null, plan.eventAction, "XMPushServiceConnectionDelegate.connect")
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
        service.connectionConfiguration.connectionPoint = Network.getActiveConnPoint(service)
        connectBySlim()
        if (service.currentConnection == null) {
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
        val plan = PushServiceConnectionRuntime.planCheckAlive(service.isConnected, Network.hasNetwork(service))
        PushRuntime.observeChannelEvent(null, plan.eventAction, "XMPushServiceConnectionDelegate.checkAlive")
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
            object : XMPushService.Job(XMPushServiceJob.TYPE_CONNECTING_TIMEOUT) {
                override fun getDesc(): String = "disconnect because of connecting timeout"

                override fun process() {
                    if (service.isConnecting) {
                        service.disconnect(18, null)
                    }
                }
            },
            15000L,
        )
    }

    fun sendPongIfNeed() {
        if (System.currentTimeMillis() - service.lastAliveAt >= SmackConfiguration.getCheckAliveInterval() &&
            Network.isConnected(service)
        ) {
            checkAlive(true)
        }
    }

    private fun connectBySlim() {
        try {
            service.slimConnection.addPacketListener(
                service.servicePacketListener,
                PacketFilter { true },
            )
            service.slimConnection.connect()
            service.currentConnection = service.slimConnection
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
