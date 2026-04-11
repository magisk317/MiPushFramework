package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.smack.XMPPException
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmsf.runtime.PushRuntime

object MIPushAckDispatcher {
    private fun interface AckAction {
        @Throws(Exception::class)
        fun process()
    }

    @JvmStatic
    fun sendAckMessage(pushService: XMPushService, container: XmPushActionContainer) {
        enqueue(pushService, "send ack message for message.") {
            sendServiceAck(pushService, container)
        }
    }

    @JvmStatic
    fun sendAppAbsentAck(pushService: XMPushService, container: XmPushActionContainer, targetPackage: String) {
        enqueue(pushService, "send app absent ack message for message.") {
            sendServiceAppAbsentAck(pushService, container, targetPackage)
        }
    }

    @JvmStatic
    fun sendAppNotInstallNotification(pushService: XMPushService, container: XmPushActionContainer) {
        enqueue(pushService, "send app absent message.") {
            MIPushHelper.sendPacket(
                pushService,
                MIPushHelper.contructAppAbsentMessage(container.packageName, container.appid),
            )
        }
    }

    @JvmStatic
    fun sendClearPushMessageAck(
        pushService: XMPushService,
        container: XmPushActionContainer,
        notification: XmPushActionNotification,
    ) {
        enqueue(pushService, "send ack message for clear push message.") {
            sendServiceClearNotificationAck(pushService, container, notification)
        }
    }

    @JvmStatic
    fun sendErrorAck(
        pushService: XMPushService,
        container: XmPushActionContainer,
        error: String,
        reason: String,
    ) {
        enqueue(pushService, "send wrong message ack for message.") {
            sendServiceErrorAck(pushService, container, error, reason)
        }
    }

    @JvmStatic
    fun sendMIUINewAdsAckMessage(pushService: XMPushService, container: XmPushActionContainer) {
        enqueue(pushService, "send ack message for unrecognized new miui message.") {
            sendServiceAckWithMarker(
                pushService,
                container,
                "miui_message_unrecognized",
                "1",
                "MIPushAckDispatcher.sendMIUINewAdsAckMessage",
            )
        }
    }

    @JvmStatic
    fun sendMIUIOldAdsAckMessage(pushService: XMPushService, container: XmPushActionContainer) {
        enqueue(pushService, "send ack message for obsleted message.") {
            sendServiceAckWithMarker(
                pushService,
                container,
                "message_obsleted",
                "1",
                "MIPushAckDispatcher.sendMIUIOldAdsAckMessage",
            )
        }
    }

    private fun enqueue(pushService: XMPushService, description: String, ackAction: AckAction) {
        pushService.executeJob(
            object : XMPushService.Job(XMPushServiceJob.TYPE_SEND_MSG) {
                override fun getDesc(): String = description

                override fun process() {
                    try {
                        ackAction.process()
                    } catch (e: Exception) {
                        MyLog.e(e)
                        if (e is XMPPException) {
                            pushService.disconnect(10, e)
                        }
                    }
                }
            },
        )
    }

    @Throws(Exception::class)
    private fun sendServiceAck(pushService: XMPushService, container: XmPushActionContainer) {
        val ackMessage = MIPushEventProcessor.constructAckMessage(pushService, container)
        MIPushHelper.sendPacket(pushService, ackMessage)
        PushRuntime.observeChannelEvent(container.packageName, "service_ack_sent", "MIPushAckDispatcher.sendServiceAck")
    }

    @Throws(Exception::class)
    private fun sendServiceAppAbsentAck(pushService: XMPushService, container: XmPushActionContainer, targetPackage: String) {
        val ackMessage = MIPushEventProcessor.constructAckMessage(pushService, container)
        ackMessage.metaInfo?.putToExtra("absent_target_package", targetPackage)
        MIPushHelper.sendPacket(pushService, ackMessage)
        PushRuntime.observeChannelEvent(container.packageName, "service_app_absent_ack_sent", "MIPushAckDispatcher.sendServiceAppAbsentAck")
    }

    @Throws(Exception::class)
    private fun sendServiceErrorAck(
        pushService: XMPushService,
        container: XmPushActionContainer,
        error: String,
        reason: String,
    ) {
        val ackMessage = MIPushEventProcessor.constructAckMessage(pushService, container)
        ackMessage.metaInfo?.apply {
            putToExtra(com.xiaomi.smack.packet.Message.MSG_TYPE_ERROR, error)
            putToExtra("reason", reason)
        }
        MIPushHelper.sendPacket(pushService, ackMessage)
        PushRuntime.observeChannelEvent(container.packageName, "service_error_ack_sent", "MIPushAckDispatcher.sendServiceErrorAck")
    }

    @Throws(Exception::class)
    private fun sendServiceAckWithMarker(
        pushService: XMPushService,
        container: XmPushActionContainer,
        key: String,
        value: String,
        source: String,
    ) {
        val ackMessage = MIPushEventProcessor.constructAckMessage(pushService, container)
        ackMessage.metaInfo?.putToExtra(key, value)
        MIPushHelper.sendPacket(pushService, ackMessage)
        PushRuntime.observeChannelEvent(container.packageName, "service_ack_marker_sent", source)
    }

    @Throws(Exception::class)
    private fun sendServiceClearNotificationAck(
        pushService: XMPushService,
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
            pushService,
            MIPushHelper.constructResponseContainer(container.packageName, container.appid, ackNotification, ActionType.Notification),
        )
        PushRuntime.observeNotificationEvent(
            notification.packageName,
            "service_clear_notification_ack_sent",
            "MIPushAckDispatcher.sendServiceClearNotificationAck",
        )
    }
}
