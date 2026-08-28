package com.xiaomi.mipush.sdk

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import android.text.TextUtils
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionAckMessage
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import io.github.magisk317.mipush.runtime.PushRuntime

/**
 * Synchronous stock-facing ACK operations kept outside the processor façade.
 * Construction only captures the application context; service clients are resolved per call.
 */
internal class PushMessageProcessorAckSupport(context: Context) {
    private val sAppContext: Context = context.applicationContext ?: context

    fun ackMessage(container: XmPushActionContainer) {
        val metaInfo = container.metaInfo ?: return
        val ackMessage = XmPushActionAckMessage().apply {
            appId = container.appid
            id = metaInfo.id
            messageTs = metaInfo.messageTs
            if (!TextUtils.isEmpty(metaInfo.topic)) {
                topic = metaInfo.topic
            }
            deviceStatus = XmPushThriftSerializeUtils.getDeviceStatus(sAppContext, container)
        }
        PushServiceClient.getInstance(sAppContext)
            .sendMessage(ackMessage, ActionType.AckMessage, false, container.metaInfo)
        PushRuntime.observeChannelEvent(
            container.packageName,
            "client_ack_sent",
            "PushMessageProcessor.ackMessage"
        )
    }

    fun ackMessage(sendMessage: XmPushActionSendMessage, container: XmPushActionContainer) {
        val metaInfo = container.metaInfo ?: return
        val ackMessage = XmPushActionAckMessage().apply {
            appId = sendMessage.appId
            id = sendMessage.id
            messageTs = sendMessage.message.createAt
            if (!TextUtils.isEmpty(sendMessage.topic)) {
                topic = sendMessage.topic
            }
            if (!TextUtils.isEmpty(sendMessage.aliasName)) {
                aliasName = sendMessage.aliasName
            }
            deviceStatus = XmPushThriftSerializeUtils.getDeviceStatus(sAppContext, container)
        }
        PushServiceClient.getInstance(sAppContext).sendMessage(ackMessage, ActionType.AckMessage, metaInfo)
        PushRuntime.observeChannelEvent(
            container.packageName,
            "client_ack_sent",
            "PushMessageProcessor.ackMessageSendMessage"
        )
    }

    fun reportDecryptFail(container: XmPushActionContainer) {
        MyLog.w("receive a message but decrypt failed. report now.")
        val notification = XmPushActionNotification(container.metaInfo.id, false).apply {
            type = NotificationType.DecryptMessageFail.value
            appId = container.appid
            packageName = container.packageName
            extra = HashMap<String, String>().apply {
                put("regid", MiPushClient.getRegId(sAppContext))
            }
        }
        PushServiceClient.getInstance(sAppContext).sendMessage(notification, ActionType.Notification, false, null)
    }

    fun sendAckNotification(notification: XmPushActionNotification) {
        val ackNotification = XmPushActionAckNotification().apply {
            type = NotificationType.CancelPushMessageACK.value
            id = notification.id
            target = notification.target
            appId = notification.appId
            packageName = notification.packageName
            errorCode = 0L
            reason = "success clear push message."
        }
        PushServiceClient.getInstance(sAppContext).sendMessage(
            ackNotification,
            ActionType.Notification,
            false,
            true,
            null,
            false,
            sAppContext.packageName,
            AppInfoHolder.getInstance(sAppContext).appID,
            false
        )
        PushRuntime.observeNotificationEvent(
            notification.packageName,
            "clear_notification_ack_sent",
            "PushMessageProcessor.sendAckNotification"
        )
    }
}
