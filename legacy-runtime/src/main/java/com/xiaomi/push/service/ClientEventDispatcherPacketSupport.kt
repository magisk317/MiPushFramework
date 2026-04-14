package com.xiaomi.push.service

import android.content.Intent
import android.os.Message
import android.os.RemoteException
import android.text.TextUtils
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.CommonPacketExtension
import com.xiaomi.smack.packet.IQ
import com.xiaomi.smack.packet.Packet
import com.xiaomi.smack.packet.Presence
import com.xiaomi.smack.packet.Message as SmackMessage
import com.xiaomi.smack.util.TrafficUtils

internal object ClientEventDispatcherPacketSupport {
    fun getClientLoginInfo(blob: Blob): PushClientsManager.ClientLoginInfo? {
        val clients = PushClientsManager.getInstance()
            .getAllClientLoginInfoByChid(Integer.toString(blob.channelId))
        if (clients.isEmpty()) return null
        if (clients.count() == 1) return clients.first()
        val fullUserName = blob.fullUserName
        for (item in clients) {
            if (TextUtils.equals(fullUserName, item.userId)) {
                return item
            }
        }
        return null
    }

    fun getClientLoginInfo(packet: Packet): PushClientsManager.ClientLoginInfo? {
        val clients = PushClientsManager.getInstance().getAllClientLoginInfoByChid(packet.channelId)
        if (clients.isEmpty()) return null
        if (clients.count() == 1) return clients.first()
        val from = packet.from
        val to = packet.to
        for (item in clients) {
            if (TextUtils.equals(from, item.userId) || TextUtils.equals(to, item.userId)) {
                return item
            }
        }
        return null
    }

    fun notifyPacketArrival(pushAction: IPushServiceAction, chid: String, blob: Blob, pushEventProcessor: MIPushEventProcessor) {
        pushAction.postOnCreate() // Replacing XMPushServiceLifecycleBridge.ensureCreated(pushService)
        pushAction.runtimeObserver.notifyPacketArrival(chid, blob)
        val clientLoginInfo = getClientLoginInfo(blob)
        if (clientLoginInfo == null) {
            MyLog.e("error while notify channel closed! channel $chid not registered")
            return
        }
        if ("5".equals(chid, ignoreCase = true)) {
            runBlobMipushPath(pushAction, blob, clientLoginInfo, pushEventProcessor)
            return
        }

        val pkgName = clientLoginInfo.pkgName
        val intent = Intent().apply {
            action = "com.xiaomi.push.new_msg"
            `package` = pkgName
            putExtra("ext_chid", chid)
            putExtra("ext_raw_packet", blob.getDecryptedPayload(clientLoginInfo.security))
            putExtra(PushConstants.EXTRA_SESSION, clientLoginInfo.session)
            putExtra(PushConstants.EXTRA_SECURITY, clientLoginInfo.security)
        }
        val peer = clientLoginInfo.peer
        if (peer != null) {
            val msg = Message.obtain(null, 17, intent)
            try {
                peer.send(msg)
                return
            } catch (_: RemoteException) {
                clientLoginInfo.peer = null
                val userId = clientLoginInfo.userId
                MyLog.w("peer may died: " + userId.substring(userId.lastIndexOf('@')))
            }
        }
        if ("com.xiaomi.xmsf" != pkgName) {
            ClientEventDispatcherChannelSupport.sendBroadcast(pushAction.context, intent, clientLoginInfo)
        }
    }

    fun notifyPacketArrival(pushAction: IPushServiceAction, chid: String, packet: Packet, pushEventProcessor: MIPushEventProcessor) {
        pushAction.postOnCreate() // Replacing XMPushServiceLifecycleBridge.ensureCreated(pushService)
        pushAction.runtimeObserver.notifyPacketArrival(chid, packet)
        val clientLoginInfo = getClientLoginInfo(packet)
        if (clientLoginInfo == null) {
            MyLog.e("error while notify channel closed! channel $chid not registered")
            return
        }
        if ("5".equals(chid, ignoreCase = true)) {
            runPacketMipushPath(pushAction, packet, clientLoginInfo, pushEventProcessor)
            return
        }

        val pkgName = clientLoginInfo.pkgName
        val action = when (packet) {
            is SmackMessage -> "com.xiaomi.push.new_msg"
            is IQ -> "com.xiaomi.push.new_iq"
            is Presence -> "com.xiaomi.push.new_pres"
            else -> {
                MyLog.e("unknown packet type, drop it")
                return
            }
        }
        val intent = Intent().apply {
            this.action = action
            `package` = pkgName
            putExtra("ext_chid", chid)
            putExtra("ext_packet", packet.toBundle())
            putExtra(PushConstants.EXTRA_SESSION, clientLoginInfo.session)
            putExtra(PushConstants.EXTRA_SECURITY, clientLoginInfo.security)
        }
        ClientEventDispatcherChannelSupport.sendBroadcast(pushAction.context, intent, clientLoginInfo)
    }

    private fun runBlobMipushPath(
        pushAction: IPushServiceAction,
        blob: Blob,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        pushEventProcessor: MIPushEventProcessor
    ) {
        var payload: ByteArray? = null
        try {
            payload = blob.getDecryptedPayload(clientLoginInfo.security)
            pushAction.runtimeObserver.onPayloadReceived(
                pushAction.context,
                payload,
                blob.serializedSize.toLong(),
                "ClientEventDispatcher.notifyPacketArrival(blob)"
            )
        } catch (t: Throwable) {
            MyLog.e(t)
        }
        try {
            pushEventProcessor.processNewPacket(pushAction, blob, clientLoginInfo)
        } catch (t: Throwable) {
            if (shouldFallbackWithMyHelper(pushAction, t, payload)) {
                MyLog.w("fallback to delegative notification handler: ${t.javaClass.name}, ${t.message}")
                try {
                    payload?.let { pushAction.notificationHandler?.handleNotification(pushAction.context.packageName, it) }
                } catch (fallbackError: Throwable) {
                    MyLog.e(fallbackError)
                }
            } else {
                throw t
            }
        }
    }

    private fun runPacketMipushPath(
        pushAction: IPushServiceAction,
        packet: Packet,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        pushEventProcessor: MIPushEventProcessor
    ) {
        var payload: ByteArray? = null
        try {
            if (packet is SmackMessage) {
                val extension: CommonPacketExtension? = packet.getExtension("s")
                if (extension != null) {
                    payload = RC4Cryption.decrypt(
                        RC4Cryption.generateKeyForRC4(clientLoginInfo.security, packet.packetID),
                        extension.text
                    )
                    pushAction.runtimeObserver.onPayloadReceived(
                        pushAction.context,
                        payload,
                        TrafficUtils.getTrafficFlow(packet.toXML()).toLong(),
                        "ClientEventDispatcher.notifyPacketArrival(packet)"
                    )
                }
            }
        } catch (t: Throwable) {
            MyLog.e(t)
        }
        try {
            pushEventProcessor.processNewPacket(pushAction, packet, clientLoginInfo)
        } catch (t: Throwable) {
            if (shouldFallbackWithMyHelper(pushAction, t, payload)) {
                MyLog.w("fallback to delegative notification handler: ${t.javaClass.name}, ${t.message}")
                try {
                    payload?.let { pushAction.notificationHandler?.handleNotification(pushAction.context.packageName, it) }
                } catch (fallbackError: Throwable) {
                    MyLog.e(fallbackError)
                }
            } else {
                throw t
            }
        }
    }


    private fun shouldFallbackWithMyHelper(pushAction: IPushServiceAction, t: Throwable?, payload: ByteArray?): Boolean {
        if (payload == null || payload.isEmpty() || t == null || !isNotificationPayload(pushAction, payload)) {
            return false
        }
        var current: Throwable? = t
        while (current != null) {
            val message = current.message
            if (message != null && (message.contains("FLAG_IMMUTABLE") || message.contains("FLAG_MUTABLE"))) {
                return true
            }
            for (element in current.stackTrace) {
                val cls = element.className
                if (cls.contains("MIPushNotificationHelper")) {
                    return true
                }
            }
            current = current.cause
        }
        return false
    }

    private fun isNotificationPayload(pushAction: IPushServiceAction, payload: ByteArray): Boolean {
        return pushAction.runtimeObserver.packToContainer(payload) != null
    }
}
