package com.xiaomi.push.service

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.CommonPacketExtension
import com.xiaomi.smack.packet.Message
import com.xiaomi.smack.packet.Packet
import com.xiaomi.smack.util.TrafficUtils
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionAckMessage
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TBase

class MIPushEventProcessor {
    fun processChannelOpenResult(
        context: Context,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        success: Boolean,
        reasonCode: Int,
        reasonMessage: String?,
    ) {
        val account = PushAccountRuntime.loadAccount(context, "MIPushEventProcessor.processChannelOpenResult")
        if (success || account == null || reasonMessage != "token-expired") {
            return
        }
        PushAccountRuntime.registerAccount(
            context,
            account.packageName,
            account.appId,
            account.appToken,
            "MIPushEventProcessor.processChannelOpenResult",
        )
    }

    fun processNewPacket(
        service: XMPushService,
        blob: Blob,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
    ) {
        try {
            processMIPushMessage(service, blob.getDecryptedPayload(clientLoginInfo.security), blob.serializedSize.toLong())
        } catch (e: IllegalArgumentException) {
            MyLog.e(e)
        }
    }

    fun processNewPacket(
        service: XMPushService,
        packet: Packet,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
    ) {
        if (packet !is Message) {
            MyLog.w("not a mipush message")
            return
        }
        val extension: CommonPacketExtension? = packet.getExtension("s")
        if (extension != null) {
            try {
                val decrypted = RC4Cryption.decrypt(
                    RC4Cryption.generateKeyForRC4(clientLoginInfo.security, packet.packetID),
                    extension.text,
                )
                processMIPushMessage(service, decrypted, TrafficUtils.getTrafficFlow(packet.toXML()).toLong())
            } catch (e: IllegalArgumentException) {
                MyLog.e(e)
            }
        }
    }

    companion object {
        @JvmStatic
        fun buildContainer(payload: ByteArray): XmPushActionContainer? {
            val container = XmPushActionContainer()
            return try {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload)
                container
            } catch (t: Throwable) {
                MyLog.e(t)
                null
            }
        }

        @JvmStatic
        fun buildIntent(payload: ByteArray, receivedAtMs: Long): Intent? {
            val container = buildContainer(payload) ?: return null
            return Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
                putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
                putExtra(PushConstants.MESSAGE_RECEIVE_TIME, receivedAtMs.toString())
                `package` = container.packageName
            }
        }

        @JvmStatic
        fun constructAckMessage(context: Context, container: XmPushActionContainer): XmPushActionContainer {
            val ackMessage = XmPushActionAckMessage().apply {
                setAppId(container.appid)
            }
            val metaInfo: PushMetaInfo? = container.metaInfo
            if (metaInfo != null) {
                ackMessage.setId(metaInfo.id)
                ackMessage.setMessageTs(metaInfo.messageTs)
                if (!TextUtils.isEmpty(metaInfo.topic)) {
                    ackMessage.setTopic(metaInfo.topic)
                }
            }
            ackMessage.setDeviceStatus(XmPushThriftSerializeUtils.getDeviceStatus(context, container))
            val ackContainer = MIPushHelper.generateRequestContainer(
                container.packageName,
                container.appid,
                ackMessage,
                ActionType.AckMessage,
            )
            val ackMetaInfo = container.metaInfo.deepCopy().apply {
                putToExtra(PushConstants.MESSAGE_ACK_TIME, System.currentTimeMillis().toString())
            }
            ackContainer.setMetaInfo(ackMetaInfo)
            return ackContainer
        }

        @JvmStatic
        @Throws(Throwable::class)
        fun postProcessMIPushMessage(service: XMPushService, targetPackage: String, payload: ByteArray, intent: Intent) {
            MIPushEventProcessorSupport.postProcessMIPushMessage(service, targetPackage, payload, intent)
        }

        private fun processMIPushMessage(service: XMPushService, payload: ByteArray, trafficBytes: Long) {
            MIPushEventProcessorSupport.processMIPushMessage(service, payload, trafficBytes)
        }
    }
}
