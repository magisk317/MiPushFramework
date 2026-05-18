package com.xiaomi.push.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.mipush.sdk.Constants
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

class MIPushEventProcessor {
    fun processChannelOpenResult(
        pushAction: IPushServiceAction,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        success: Boolean,
        reasonCode: Int,
        reasonMessage: String?,
    ) {
        val account = pushAction.runtimeObserver.loadAccount(pushAction.context, "MIPushEventProcessor.processChannelOpenResult")
        if (success || account == null || reasonMessage != "token-expired") {
            return
        }
        pushAction.runtimeObserver.registerAccount(
            pushAction.context,
            account.packageName,
            account.appId,
            account.appToken,
            "MIPushEventProcessor.processChannelOpenResult",
        )
    }

    fun processNewPacket(
        pushAction: IPushServiceAction,
        blob: Blob,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
    ) {
        try {
            processMIPushMessage(pushAction, blob.getDecryptedPayload(clientLoginInfo.security), blob.serializedSize.toLong())
        } catch (e: IllegalArgumentException) {
            MyLog.e(e)
        }
    }

    fun processNewPacket(
        pushAction: IPushServiceAction,
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
                    RC4Cryption.generateKeyForRC4(clientLoginInfo.security, packet.packetID ?: ""),
                    extension.text,
                )
                processMIPushMessage(pushAction, decrypted, TrafficUtils.getTrafficFlow(packet.toXML()).toLong())
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
        fun postProcessMIPushMessage(pushAction: IPushServiceAction, targetPackage: String, payload: ByteArray, intent: Intent) {
            pushAction.runtimeObserver.postProcessMIPushMessage(targetPackage, payload, intent)
        }

        private fun processMIPushMessage(pushAction: IPushServiceAction, payload: ByteArray, trafficBytes: Long) {
            val container = buildContainer(payload)
            if (container == null) {
                pushAction.runtimeObserver.processMIPushMessage(payload, trafficBytes)
                return
            }
            pushAction.runtimeObserver.processMIPushMessage(payload, trafficBytes)
            maybeAckInboundSendMessage(pushAction, container)
        }

        private fun maybeAckInboundSendMessage(pushAction: IPushServiceAction, container: XmPushActionContainer) {
            if (container.action != ActionType.SendMessage || container.metaInfo == null) {
                return
            }
            if (!container.isEncryptAction || isHybridMessage(container)) {
                return
            }
            if (isTargetPackageMissing(pushAction.context, container)) {
                if (!container.packageName.isNullOrBlank() && !container.appid.isNullOrBlank()) {
                    MIPushAckDispatcher.sendAppNotInstallNotification(pushAction, container)
                }
                pushAction.runtimeObserver.onNotificationEvent(
                    container.packageName,
                    "app_absent_ack_instead_of_normal_ack",
                    "MIPushEventProcessor.maybeAckInboundSendMessage",
                )
                return
            }
            val metaInfo = container.metaInfo
            metaInfo.putToExtra(PushConstants.MESSAGE_RECEIVE_TIME, System.currentTimeMillis().toString())
            MIPushAckDispatcher.sendAckMessage(pushAction, container)
        }

        private fun isTargetPackageMissing(context: Context, container: XmPushActionContainer): Boolean {
            val packageName = MIPushNotificationHelper.getTargetPackage(container)
            if (packageName.isNullOrBlank()) return false
            if (packageName == context.packageName) return false
            if (packageName == PushConstants.PUSH_SERVICE_PACKAGE_NAME) return false
            return try {
                context.packageManager.getPackageInfo(packageName, 0)
                false
            } catch (_: PackageManager.NameNotFoundException) {
                true
            }
        }

        private fun isHybridMessage(container: XmPushActionContainer): Boolean {
            val action = container.metaInfo?.extra?.get(Constants.EXTRA_KEY_PUSH_SERVER_ACTION)
            return action == Constants.EXTRA_VALUE_HYBRID_MESSAGE ||
                action == Constants.EXTRA_VALUE_PLATFORM_MESSAGE
        }
    }
}
