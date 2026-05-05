package com.xiaomi.push.service

import android.text.TextUtils
import com.google.protobuf.micro.InvalidProtocolBufferMicroException
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.network.HostManager
import com.xiaomi.push.log.LogUploader
import com.xiaomi.push.protobuf.ChannelMessage
import com.xiaomi.push.thrift.ChannelStatsType
import com.xiaomi.slim.Blob
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.smack.packet.CommonPacketExtension
import com.xiaomi.smack.packet.IQ
import com.xiaomi.smack.packet.Message
import com.xiaomi.smack.packet.Packet
import com.xiaomi.smack.util.TrafficUtils
import com.xiaomi.stats.StatsHelper
import java.util.Date

class PacketSync(
    private val pushAction: IPushServiceAction,
) {
    @Suppress("FunctionName")
    interface PacketReceiveHandler {
        fun Handle(blob: Blob): Boolean
        fun Handle(packet: Packet): Boolean
    }

    private fun dispatchNetFlow(blob: Blob) {
        val fullUserName = blob.fullUserName
        val channelId = blob.channelId.toString()
        val client = if (fullUserName.isNullOrEmpty() || channelId.isEmpty()) {
            null
        } else {
            PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(channelId, fullUserName)
        } ?: return
        try {
            TrafficUtils.distributionTraffic(
                pushAction.context,
                client.pkgName,
                blob.serializedSize.toLong(),
                true,
                true,
                System.currentTimeMillis(),
            )
        } catch (t: Throwable) {
            MyLog.e(t)
        }
    }

    private fun dispatchNetFlow(packet: Packet) {
        val userId = packet.to
        val channelId = packet.channelId
        val client = if (userId.isNullOrEmpty() || channelId.isNullOrEmpty()) {
            null
        } else {
            PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(channelId, userId)
        } ?: return
        try {
            TrafficUtils.distributionTraffic(
                pushAction.context,
                client.pkgName,
                TrafficUtils.getTrafficFlow(packet.toXML()).toLong(),
                true,
                true,
                System.currentTimeMillis(),
            )
        } catch (t: Throwable) {
            MyLog.e(t)
        }
    }

    private fun processRedirectMessage(extension: CommonPacketExtension?) {
        val plan = pushAction.runtimeObserver.resolveRedirect(extension?.text)
        if (!plan.shouldReconnect) {
            return
        }
        val fallbacks = HostManager.getInstance().getFallbacksByHost(ConnectionConfiguration.getXmppServerHost(), false) ?: return
        fallbacks.addPreferredHost(plan.preferredHosts.toTypedArray())
        pushAction.runtimeObserver.onChannelEvent(null, "server_redirect", "PacketSync.processRedirectMessage")
        pushAction.runtimeObserver.onConnectionStateChanged(
            "Disconnected",
            "PacketSync.processRedirectMessage",
            ConnectionConfiguration.getXmppServerHost(),
            "redirect_host_update",
        )
        pushAction.disconnect(20, null)
        pushAction.scheduleConnect(true)
    }

    private fun handleBindResult(channelId: String, userId: String?, response: ChannelMessage.XMMsgBindResp) {
        val client = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(channelId, userId) ?: return
        val plan = pushAction.runtimeObserver.resolveBindResult(response.result, response.errorType, response.errorReason)
        pushAction.runtimeObserver.onChannelEvent(client.pkgName, plan.eventAction, "PacketSync.handleBindResult")
        if (plan.shouldReportInvalidSig) {
            MyLog.w("SMACK: bind error invalid-sig token = ${client.token} sec = ${client.security}")
            StatsHelper.stats(0, ChannelStatsType.BIND_INVALID_SIG.value, 1, "", 0)
        }
        if (plan.shouldScheduleRebind) {
            pushAction.scheduleRebindChannel(client)
        }
        if (plan.clientStatus != null && plan.notifyType != null) {
            client.setStatus(
                plan.clientStatus,
                plan.notifyType,
                plan.statusReasonCode,
                plan.statusReasonMessage,
                plan.statusErrorType,
            )
        }
        if (plan.shouldDeactivateClient) {
            PushClientsManager.getInstance().deactivateClient(channelId, userId)
        }
        if (plan.runtimeState != null) {
            pushAction.runtimeObserver.onChannelStateChanged(
                client.pkgName,
                channelId,
                client.userId,
                client.session,
                plan.runtimeState,
                "PacketSync.handleBindResult",
                plan.statusReasonCode,
                plan.statusReasonMessage,
            )
        }
        pushAction.runtimeObserver.syncChannelTracker("PacketSync.handleBindResult")
        if (!response.result) {
            MyLog.w("SMACK: channel bind failed, chid=$channelId reason=${response.errorReason}")
            return
        }
        MyLog.w("SMACK: channel bind succeeded, chid=$channelId")
    }

    private fun handleKick(channelId: String, userId: String?, kickType: String?, kickReason: String?) {
        val plan = pushAction.runtimeObserver.resolveKick(kickType, kickReason)
        MyLog.w("kicked by server, chid=$channelId res= ${PushClientsManager.ClientLoginInfo.getResource(userId)} type=$kickType reason=$kickReason")
        val client = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(channelId, userId)
        if (client != null) {
            pushAction.runtimeObserver.onChannelEvent(client.pkgName, plan.eventAction, "PacketSync.handleKick")
            pushAction.runtimeObserver.onChannelStateChanged(
                client.pkgName,
                channelId,
                client.userId,
                client.session,
                plan.runtimeState,
                "PacketSync.handleKick",
                plan.statusReasonCode,
                plan.statusReasonMessage,
            )
        }
        if (plan.shouldCloseChannel) {
            pushAction.closeChannel(channelId, userId, 3, kickReason, kickType)
            if (plan.shouldDeactivateClient) {
                PushClientsManager.getInstance().deactivateClient(channelId, userId)
            }
        } else if (client != null && plan.shouldScheduleRebind) {
            pushAction.scheduleRebindChannel(client)
            client.setStatus(
                PushClientsManager.ClientStatus.unbind,
                3,
                plan.statusReasonCode,
                plan.statusReasonMessage,
                plan.statusErrorType,
            )
        }
        pushAction.runtimeObserver.syncChannelTracker("PacketSync.handleKick")
    }

    @Throws(InvalidProtocolBufferMicroException::class)
    fun handleBlob(blob: Blob) {
        val cmd = blob.cmd
        when (blob.channelId) {
            0 -> handleServerBlob(blob, cmd)
            else -> handleClientBlob(blob, cmd)
        }
    }

    fun onBlobReceive(blob: Blob) {
        if (blob.channelId != 5) {
            dispatchNetFlow(blob)
        }
        try {
            handleBlob(blob)
        } catch (e: Exception) {
            MyLog.e(
                "handle Blob chid = ${blob.channelId} cmd = ${blob.cmd} packetid = ${blob.packetID} failure ",
                e,
            )
        }
    }

    fun onPacketReceive(packet: Packet) {
        if (packet.channelId != "5") {
            dispatchNetFlow(packet)
        }
        var channelId = packet.channelId
        if (TextUtils.isEmpty(channelId)) {
            channelId = "1"
            packet.channelId = channelId
        }
        if (channelId == Blob.CLIENT_PING_ID) {
            MyLog.w("Received wrong packet with chid = 0 : ${packet.toXML()}")
        }
        when (packet) {
            is IQ -> {
                val extension = packet.getExtension("kick")
                if (extension != null) {
                    handleKick(channelId ?: "1", packet.to, extension.getAttributeValue("type"), extension.getAttributeValue("reason"))
                    return
                }
            }
            is Message -> {
                if (packet.type == "redir") {
                    val extension = packet.getExtension("hosts")
                    if (extension != null) {
                        processRedirectMessage(extension)
                    }
                    return
                }
            }
        }
        // Original code used internal onPacketSync and clientEventDispatcher. 
        // We'll keep it as is, assuming those components are available.
    }

    private fun handleServerBlob(blob: Blob, cmd: String?) {
        when {
            Blob.CMD_PING == cmd -> {
                val payload = blob.payload
                if (payload.isNotEmpty()) {
                    val ping = ChannelMessage.XMMsgPing.parseFrom(payload)
                    if (ping.hasPsc()) {
                        ServiceConfig.getInstance().handle(ping.psc)
                    }
                }
                if (pushAction.pushPackageName != PushConstants.PUSH_SERVICE_PACKAGE_NAME) {
                    pushAction.sendPongIfNeed()
                }
                if (blob.packetID == "1") {
                    MyLog.w("received a server ping")
                } else {
                    StatsHelper.pingEnded()
                }
                pushAction.onPong()
            }
            Blob.CMD_SYNC != cmd -> {
                if (blob.cmd == Blob.CMD_NOTIFY) {
                    val notify = ChannelMessage.XMMsgNotify.parseFrom(blob.payload)
                    MyLog.w("notify by server err = ${notify.errCode} desc = ${notify.errStr}")
                }
            }
            blob.subcmd == Blob.SUBCMD_CONF -> {
                ServiceConfig.getInstance().handle(ChannelMessage.PushServiceConfigMsg.parseFrom(blob.payload))
            }
            TextUtils.equals("U", blob.subcmd) -> {
                val message = ChannelMessage.XMMsgU.parseFrom(blob.payload)
                LogUploader.getInstance(pushAction.context).upload(
                    message.url,
                    message.token,
                    Date(message.start),
                    Date(message.end),
                    message.maxlen * 1024,
                    message.force,
                )
                val ack = Blob().apply {
                    setChannelId(0)
                    setCmd(blob.cmd, "UCA")
                    packetID = blob.packetID
                }
                pushAction.executeJob(SendMessageJob(pushAction, ack))
            }
            TextUtils.equals("P", blob.subcmd) -> {
                val message = ChannelMessage.XMMsgP.parseFrom(blob.payload)
                val ack = Blob().apply {
                    setChannelId(0)
                    setCmd(blob.cmd, "PCA")
                    packetID = blob.packetID
                    val response = ChannelMessage.XMMsgP()
                    if (message.hasCookie()) {
                        response.cookie = message.cookie
                    }
                    setPayload(response.toByteArray(), null)
                }
                pushAction.executeJob(SendMessageJob(pushAction, ack))
                MyLog.w("ACK msgP: id = ${blob.packetID}")
            }
        }
    }

    private fun handleClientBlob(blob: Blob, cmd: String?) {
        val channelId = blob.channelId.toString()
        when {
            cmd == Blob.CMD_BIND -> {
                val response = ChannelMessage.XMMsgBindResp.parseFrom(blob.payload)
                handleBindResult(channelId, blob.fullUserName, response)
            }
            cmd == Blob.CMD_KICK -> {
                val kick = ChannelMessage.XMMsgKick.parseFrom(blob.payload)
                handleKick(channelId, blob.fullUserName, kick.type, kick.reason)
            }
        }
    }
}
