package com.xiaomi.push.service

import android.content.Intent
import android.os.Message
import android.os.RemoteException
import android.text.TextUtils
import com.magisk317.SdkNotificationCompat
import com.magisk317.XMPushUtils
import com.magisk317.push.hook.HookTraceCompat
import com.magisk317.push.pipeline.MiPushRuntimeBridge
import com.magisk317.service.XMPushServiceLifecycleBridge
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.CommonPacketExtension
import com.xiaomi.smack.packet.IQ
import com.xiaomi.smack.packet.Packet
import com.xiaomi.smack.packet.Presence
import com.xiaomi.smack.packet.Message as SmackMessage
import com.xiaomi.smack.util.TrafficUtils
import com.xiaomi.xmpush.thrift.ActionType

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

    fun notifyPacketArrival(pushService: XMPushService, chid: String, blob: Blob, pushEventProcessor: MIPushEventProcessor) {
        XMPushServiceLifecycleBridge.ensureCreated(pushService)
        HookTraceCompat.notifyPacketArrival(chid, blob)
        val clientLoginInfo = getClientLoginInfo(blob)
        if (clientLoginInfo == null) {
            MyLog.e("error while notify channel closed! channel $chid not registered")
            return
        }
        if ("5".equals(chid, ignoreCase = true)) {
            val payload = runBlobMipushPath(pushService, blob, clientLoginInfo, pushEventProcessor)
            maybeFallbackWithModernHelper(pushService, payload)
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
            ClientEventDispatcherChannelSupport.sendBroadcast(pushService, intent, clientLoginInfo)
        }
    }

    fun notifyPacketArrival(pushService: XMPushService, chid: String, packet: Packet, pushEventProcessor: MIPushEventProcessor) {
        XMPushServiceLifecycleBridge.ensureCreated(pushService)
        HookTraceCompat.notifyPacketArrival(chid, packet)
        val clientLoginInfo = getClientLoginInfo(packet)
        if (clientLoginInfo == null) {
            MyLog.e("error while notify channel closed! channel $chid not registered")
            return
        }
        if ("5".equals(chid, ignoreCase = true)) {
            val payload = runPacketMipushPath(pushService, packet, clientLoginInfo, pushEventProcessor)
            maybeFallbackWithModernHelper(pushService, payload)
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
        ClientEventDispatcherChannelSupport.sendBroadcast(pushService, intent, clientLoginInfo)
    }

    private fun runBlobMipushPath(
        pushService: XMPushService,
        blob: Blob,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        pushEventProcessor: MIPushEventProcessor
    ): ByteArray? {
        var payload: ByteArray? = null
        try {
            payload = blob.getDecryptedPayload(clientLoginInfo.security)
            MiPushRuntimeBridge.onPayloadFromServer(
                pushService,
                payload,
                blob.serializedSize.toLong(),
                "ClientEventDispatcher.notifyPacketArrival(blob)"
            )
        } catch (t: Throwable) {
            MyLog.e(t)
        }
        try {
            pushEventProcessor.processNewPacket(pushService, blob, clientLoginInfo)
        } catch (t: Throwable) {
            if (shouldFallbackWithMyHelper(t, payload)) {
                MyLog.w("fallback to MyMIPushNotificationHelper: ${t.javaClass.name}, ${t.message}")
                try {
                    payload?.let { MyMIPushNotificationHelper.notifyPushMessage(pushService, it) }
                } catch (fallbackError: Throwable) {
                    MyLog.e(fallbackError)
                }
            } else {
                throw t
            }
        }
        return payload
    }

    private fun runPacketMipushPath(
        pushService: XMPushService,
        packet: Packet,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        pushEventProcessor: MIPushEventProcessor
    ): ByteArray? {
        var payload: ByteArray? = null
        try {
            if (packet is SmackMessage) {
                val extension: CommonPacketExtension? = packet.getExtension("s")
                if (extension != null) {
                    payload = RC4Cryption.decrypt(
                        RC4Cryption.generateKeyForRC4(clientLoginInfo.security, packet.packetID),
                        extension.text
                    )
                    MiPushRuntimeBridge.onPayloadFromServer(
                        pushService,
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
            pushEventProcessor.processNewPacket(pushService, packet, clientLoginInfo)
        } catch (t: Throwable) {
            if (shouldFallbackWithMyHelper(t, payload)) {
                MyLog.w("fallback to MyMIPushNotificationHelper: ${t.javaClass.name}, ${t.message}")
                try {
                    payload?.let { MyMIPushNotificationHelper.notifyPushMessage(pushService, it) }
                } catch (fallbackError: Throwable) {
                    MyLog.e(fallbackError)
                }
            } else {
                throw t
            }
        }
        return payload
    }

    private fun maybeFallbackWithModernHelper(pushService: XMPushService, payload: ByteArray?) {
        if (SdkNotificationCompat.shouldUseModernHelper(payload)) {
            try {
                SdkNotificationCompat.notifyWithModernHelper(pushService, payload)
            } catch (t: Throwable) {
                MyLog.e(t)
            }
        }
    }

    private fun shouldFallbackWithMyHelper(t: Throwable?, payload: ByteArray?): Boolean {
        if (payload == null || payload.isEmpty() || t == null || !isNotificationPayload(payload)) {
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

    private fun isNotificationPayload(payload: ByteArray): Boolean {
        val container = XMPushUtils.packToContainer(payload) ?: return false
        return container.action == ActionType.SendMessage || container.action == ActionType.Notification
    }
}
