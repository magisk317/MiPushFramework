package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.smack.XMPPException
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification

object MIPushAckDispatcher {
    @JvmStatic
    fun notifyRegisterError(context: Context, errorCode: Int, errorMessage: String) {
        val packageName = context.packageName // Usually the service package or target app
        val intent = Intent(PushConstants.MIPUSH_ACTION_ERROR).apply {
            setPackage(packageName)
            putExtra(PushConstants.MIPUSH_EXTRA_ERROR_CODE, errorCode)
            putExtra(PushConstants.MIPUSH_EXTRA_ERROR_MSG, errorMessage)
        }
        context.sendBroadcast(intent, MIPushHelper.getReceiverPermission(packageName))
    }

    private fun interface AckAction {
        @Throws(Exception::class)
        fun process()
    }

    @JvmStatic
    fun sendAckMessage(pushAction: IPushServiceAction, container: XmPushActionContainer) {
        enqueue(pushAction, "send ack message for message.") {
            sendServiceAck(pushAction, pushAction.context, container)
        }
    }

    @JvmStatic
    fun sendAppAbsentAck(pushAction: IPushServiceAction, container: XmPushActionContainer, targetPackage: String) {
        enqueue(pushAction, "send app absent ack message for message.") {
            sendServiceAppAbsentAck(pushAction, pushAction.context, container, targetPackage)
        }
    }

    @JvmStatic
    fun sendAppNotInstallNotification(pushAction: IPushServiceAction, container: XmPushActionContainer) {
        enqueue(pushAction, "send app absent message.") {
            MIPushHelper.sendPacket(
                pushAction,
                pushAction.context,
                MIPushHelper.contructAppAbsentMessage(container.packageName, container.appid),
            )
        }
    }

    @JvmStatic
    fun sendClearPushMessageAck(
        pushAction: IPushServiceAction,
        container: XmPushActionContainer,
        notification: XmPushActionNotification,
    ) {
        enqueue(pushAction, "send ack message for clear push message.") {
            sendServiceClearNotificationAck(pushAction, pushAction.context, container, notification)
        }
    }

    @JvmStatic
    fun sendErrorAck(
        pushAction: IPushServiceAction,
        container: XmPushActionContainer,
        error: String,
        reason: String,
    ) {
        enqueue(pushAction, "send wrong message ack for message.") {
            sendServiceErrorAck(pushAction, pushAction.context, container, error, reason)
        }
    }

    @JvmStatic
    fun sendMIUINewAdsAckMessage(pushAction: IPushServiceAction, container: XmPushActionContainer) {
        enqueue(pushAction, "send ack message for unrecognized new miui message.") {
            sendServiceAckWithMarker(
                pushAction,
                pushAction.context,
                container,
                "miui_message_unrecognized",
                "1",
                "MIPushAckDispatcher.sendMIUINewAdsAckMessage",
            )
        }
    }

    @JvmStatic
    fun sendMIUIOldAdsAckMessage(pushAction: IPushServiceAction, container: XmPushActionContainer) {
        enqueue(pushAction, "send ack message for obsleted message.") {
            sendServiceAckWithMarker(
                pushAction,
                pushAction.context,
                container,
                "message_obsleted",
                "1",
                "MIPushAckDispatcher.sendMIUIOldAdsAckMessage",
            )
        }
    }

    private fun enqueue(pushAction: IPushServiceAction, description: String, ackAction: AckAction) {
        pushAction.executeJob(
            object : XMPushServiceJob(TYPE_SEND_MSG) {
                override fun getDesc(): String = description

                override fun process() {
                    try {
                        ackAction.process()
                    } catch (e: Exception) {
                        MyLog.e(e)
                        if (e is XMPPException) {
                            pushAction.disconnect(10, e)
                        }
                    }
                }
            },
        )
    }

    @Throws(Exception::class)
    private fun sendServiceAck(pushAction: IPushServiceAction, context: Context, container: XmPushActionContainer) {
        val ackMessage = MIPushEventProcessor.constructAckMessage(context, container)
        MIPushHelper.sendPacket(pushAction, context, ackMessage)
        pushAction.runtimeObserver.onChannelEvent(container.packageName, "service_ack_sent", "MIPushAckDispatcher.sendServiceAck")
    }

    @Throws(Exception::class)
    private fun sendServiceAppAbsentAck(pushAction: IPushServiceAction, context: Context, container: XmPushActionContainer, targetPackage: String) {
        val ackMessage = MIPushEventProcessor.constructAckMessage(context, container)
        ackMessage.metaInfo?.putToExtra("absent_target_package", targetPackage)
        MIPushHelper.sendPacket(pushAction, context, ackMessage)
        pushAction.runtimeObserver.onChannelEvent(container.packageName, "service_app_absent_ack_sent", "MIPushAckDispatcher.sendServiceAppAbsentAck")
    }

    @Throws(Exception::class)
    private fun sendServiceErrorAck(
        pushAction: IPushServiceAction,
        context: Context,
        container: XmPushActionContainer,
        error: String,
        reason: String,
    ) {
        val ackMessage = MIPushEventProcessor.constructAckMessage(context, container)
        ackMessage.metaInfo?.apply {
            putToExtra(com.xiaomi.smack.packet.Message.MSG_TYPE_ERROR, error)
            putToExtra("reason", reason)
        }
        MIPushHelper.sendPacket(pushAction, context, ackMessage)
        pushAction.runtimeObserver.onChannelEvent(container.packageName, "service_error_ack_sent", "MIPushAckDispatcher.sendServiceErrorAck")
    }

    @Throws(Exception::class)
    private fun sendServiceAckWithMarker(
        pushAction: IPushServiceAction,
        context: Context,
        container: XmPushActionContainer,
        key: String,
        value: String,
        source: String,
    ) {
        val ackMessage = MIPushEventProcessor.constructAckMessage(context, container)
        ackMessage.metaInfo?.putToExtra(key, value)
        MIPushHelper.sendPacket(pushAction, context, ackMessage)
        pushAction.runtimeObserver.onChannelEvent(container.packageName, "service_ack_marker_sent", source)
    }

    @Throws(Exception::class)
    private fun sendServiceClearNotificationAck(
        pushAction: IPushServiceAction,
        context: Context,
        container: XmPushActionContainer,
        notification: XmPushActionNotification,
    ) {
        val ackNotification = XmPushActionAckNotification().apply {
            type = NotificationType.CancelPushMessageACK.value
            id = notification.id
            target = notification.target
            appId = notification.appId
            packageName = notification.packageName
            errorCode = 0L
            reason = "success clear push message."
        }
        MIPushHelper.sendPacket(
            pushAction,
            context,
            MIPushHelper.constructResponseContainer(container.packageName, container.appid, ackNotification, ActionType.Notification),
        )
        pushAction.runtimeObserver.onNotificationEvent(
            notification.packageName,
            "service_clear_notification_ack_sent",
            "MIPushAckDispatcher.sendServiceClearNotificationAck",
        )
    }
}
