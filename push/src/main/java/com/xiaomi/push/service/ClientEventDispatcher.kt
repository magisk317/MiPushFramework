package com.xiaomi.push.service

import android.content.Context
import android.content.Intent
import android.os.Message
import android.os.RemoteException
import android.text.TextUtils
import com.magisk317.SdkNotificationCompat
import com.magisk317.push.hook.ExplicitHookBridge
import com.magisk317.push.pipeline.MiPushRuntimeBridge
import com.magisk317.XMPushUtils
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.CommonPacketExtension
import com.xiaomi.smack.packet.IQ
import com.xiaomi.smack.packet.Packet
import com.xiaomi.smack.packet.Presence
import com.xiaomi.smack.packet.Message as SmackMessage
import com.xiaomi.smack.util.TrafficUtils
import com.xiaomi.xmpush.thrift.ActionType

class ClientEventDispatcher {
    private val pushEventProcessor = MIPushEventProcessor()

    private fun getClientLoginInfo(blob: Blob): PushClientsManager.ClientLoginInfo? {
        val clients = PushClientsManager.getInstance()
            .getAllClientLoginInfoByChid(Integer.toString(blob.channelId))
        if (clients.isEmpty()) return null
        if (clients.size == 1) return clients.first()
        val fullUserName = blob.fullUserName
        for (item in clients) {
            if (TextUtils.equals(fullUserName, item.userId)) {
                return item
            }
        }
        return null
    }

    private fun getClientLoginInfo(packet: Packet): PushClientsManager.ClientLoginInfo? {
        val clients = PushClientsManager.getInstance().getAllClientLoginInfoByChid(packet.channelId)
        if (clients.isEmpty()) return null
        if (clients.size == 1) return clients.first()
        val from = packet.from
        val to = packet.to
        for (item in clients) {
            if (TextUtils.equals(from, item.userId) || TextUtils.equals(to, item.userId)) {
                return item
            }
        }
        return null
    }

    fun notifyChannelClosed(
        context: Context,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        reason: Int
    ) {
        if ("5".equals(clientLoginInfo.chid, ignoreCase = true)) return
        val intent = Intent().apply {
            action = "com.xiaomi.push.channel_closed"
            `package` = clientLoginInfo.pkgName
            putExtra(PushConstants.EXTRA_CHANNEL_ID, clientLoginInfo.chid)
            putExtra("ext_reason", reason)
            putExtra(PushConstants.EXTRA_USER_ID, clientLoginInfo.userId)
            putExtra(PushConstants.EXTRA_SESSION, clientLoginInfo.session)
        }
        if (clientLoginInfo.peer != null && "9" == clientLoginInfo.chid) {
            val msg = Message.obtain(null, 17, intent)
            try {
                clientLoginInfo.peer.send(msg)
                return
            } catch (_: RemoteException) {
                clientLoginInfo.peer = null
                MyLog.w("peer may died: " + clientLoginInfo.userId.substring(clientLoginInfo.userId.lastIndexOf('@')))
            }
        } else {
            sendBroadcast(context, intent, clientLoginInfo)
        }
    }

    fun notifyChannelOpenResult(
        context: Context,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        succeeded: Boolean,
        reason: Int,
        reasonMessage: String?
    ) {
        if ("5".equals(clientLoginInfo.chid, ignoreCase = true)) {
            pushEventProcessor.processChannelOpenResult(
                context,
                clientLoginInfo,
                succeeded,
                reason,
                reasonMessage
            )
            return
        }
        val intent = Intent().apply {
            action = "com.xiaomi.push.channel_opened"
            `package` = clientLoginInfo.pkgName
            putExtra("ext_succeeded", succeeded)
            if (!succeeded) putExtra("ext_reason", reason)
            if (!reasonMessage.isNullOrEmpty()) putExtra("ext_reason_msg", reasonMessage)
            putExtra("ext_chid", clientLoginInfo.chid)
            putExtra(PushConstants.EXTRA_USER_ID, clientLoginInfo.userId)
            putExtra(PushConstants.EXTRA_SESSION, clientLoginInfo.session)
        }
        sendBroadcast(context, intent, clientLoginInfo)
    }

    fun notifyKickedByServer(
        context: Context,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        kickType: String?,
        kickReason: String?
    ) {
        if ("5".equals(clientLoginInfo.chid, ignoreCase = true)) {
            MyLog.e("mipush kicked by server")
            return
        }
        val intent = Intent().apply {
            action = "com.xiaomi.push.kicked"
            `package` = clientLoginInfo.pkgName
            putExtra("ext_kick_type", kickType)
            putExtra("ext_kick_reason", kickReason)
            putExtra("ext_chid", clientLoginInfo.chid)
            putExtra(PushConstants.EXTRA_USER_ID, clientLoginInfo.userId)
            putExtra(PushConstants.EXTRA_SESSION, clientLoginInfo.session)
        }
        sendBroadcast(context, intent, clientLoginInfo)
    }

    fun notifyPacketArrival(pushService: XMPushService, chid: String, blob: Blob) {
        ExplicitHookBridge.notifyPacketArrival(pushService, chid, blob)
        val clientLoginInfo = getClientLoginInfo(blob)
        if (clientLoginInfo == null) {
            MyLog.e("error while notify channel closed! channel $chid not registered")
            return
        }
        if ("5".equals(chid, ignoreCase = true)) {
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
            if (SdkNotificationCompat.shouldUseModernHelper(payload)) {
                try {
                    SdkNotificationCompat.notifyWithModernHelper(pushService, payload)
                } catch (t: Throwable) {
                    MyLog.e(t)
                }
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
        if (clientLoginInfo.peer != null) {
            val msg = Message.obtain(null, 17, intent)
            try {
                clientLoginInfo.peer.send(msg)
                return
            } catch (_: RemoteException) {
                clientLoginInfo.peer = null
                MyLog.w("peer may died: " + clientLoginInfo.userId.substring(clientLoginInfo.userId.lastIndexOf('@')))
            }
        }
        if ("com.xiaomi.xmsf" != pkgName) {
            sendBroadcast(pushService, intent, clientLoginInfo)
        }
    }

    fun notifyPacketArrival(pushService: XMPushService, chid: String, packet: Packet) {
        ExplicitHookBridge.notifyPacketArrival(pushService, chid, packet)
        val clientLoginInfo = getClientLoginInfo(packet)
        if (clientLoginInfo == null) {
            MyLog.e("error while notify channel closed! channel $chid not registered")
            return
        }
        if ("5".equals(chid, ignoreCase = true)) {
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
            if (SdkNotificationCompat.shouldUseModernHelper(payload)) {
                try {
                    SdkNotificationCompat.notifyWithModernHelper(pushService, payload)
                } catch (t: Throwable) {
                    MyLog.e(t)
                }
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
        sendBroadcast(pushService, intent, clientLoginInfo)
    }

    fun notifyServiceStarted(context: Context) {
        val intent = Intent().apply {
            action = "com.xiaomi.push.service_started"
            if (MIUIUtils.isXMS()) {
                addFlags(0x1000000)
            }
        }
        context.sendBroadcast(intent)
    }

    companion object {
        @JvmStatic
        fun getReceiverPermission(clientLoginInfo: PushClientsManager.ClientLoginInfo): String {
            return if ("9" != clientLoginInfo.chid) {
                clientLoginInfo.pkgName + ".permission.MIPUSH_RECEIVE"
            } else {
                clientLoginInfo.pkgName + ".permission.MIMC_RECEIVE"
            }
        }

        private fun sendBroadcast(
            context: Context,
            intent: Intent,
            clientLoginInfo: PushClientsManager.ClientLoginInfo
        ) {
            if ("com.xiaomi.xmsf" == context.packageName) {
                context.sendBroadcast(intent)
            } else {
                context.sendBroadcast(intent, getReceiverPermission(clientLoginInfo))
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
}
