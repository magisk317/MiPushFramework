package com.xiaomi.push.service

import io.github.magisk317.xposed.logging.MagiskOtel
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
        enqueue(pushAction, "send ack message for message.", reason = "service_ack") {
            sendServiceAck(pushAction, pushAction.context, container)
        }
    }

    @JvmStatic
    fun sendAppAbsentAck(pushAction: IPushServiceAction, container: XmPushActionContainer, targetPackage: String) {
        enqueue(pushAction, "send app absent ack message for message.", reason = "app_absent_ack") {
            sendServiceAppAbsentAck(pushAction, pushAction.context, container, targetPackage)
        }
    }

    @JvmStatic
    fun sendAppNotInstallNotification(pushAction: IPushServiceAction, container: XmPushActionContainer, targetPackage: String) {
        enqueue(pushAction, "send app absent message.", reason = "app_absent") {
            MIPushHelper.sendPacket(
                pushAction,
                pushAction.context,
                MIPushHelper.contructAppAbsentMessage(targetPackage, container.appid),
            )
        }
    }

    @JvmStatic
    @JvmOverloads
    fun sendClearPushMessageAck(
        pushAction: IPushServiceAction,
        container: XmPushActionContainer,
        notification: XmPushActionNotification,
        errorCode: Long = 0L,
        msgId: String? = null,
        resultCode: Int = 3,
        cancelType: Int = 0,
    ) {
        // Stock e1.b: the dispatcher always queues this ack after the clear resolve
        // attempt, including the no-match (errorCode 0, result_code 3) and the
        // no-matcher (errorCode -1, result_code -1, cancelType 0) paths.
        enqueue(pushAction, "send ack message for clear push message.", reason = "clear_push_ack") {
            sendServiceClearNotificationAck(
                pushAction,
                pushAction.context,
                container,
                notification,
                errorCode,
                msgId,
                resultCode,
                cancelType,
            )
        }
    }

    @JvmStatic
    fun sendErrorAck(
        pushAction: IPushServiceAction,
        container: XmPushActionContainer,
        error: String,
        reason: String,
    ) {
        enqueue(pushAction, "send wrong message ack for message.", reason = "error_ack") {
            sendServiceErrorAck(pushAction, pushAction.context, container, error, reason)
        }
    }

    /**
     * Stock 7.5.29 com.xiaomi.push.service.d.c: the setting_app_notification_permission_ack
     * replays the control's id/appId/packageName/target and full extra map, carries the
     * d.a/d.b wire errorCode and only sets reason when the stock reason is non-empty.
     */
    @JvmStatic
    fun sendSettingAppNotificationPermissionAck(
        pushAction: IPushServiceAction,
        container: XmPushActionContainer,
        notification: XmPushActionNotification,
        errorCode: Long,
        reason: String?,
    ) {
        enqueue(
            pushAction,
            "send ack message for setting app notification permission.",
            reason = "setting_app_notification_permission_ack",
        ) {
            val ackNotification = XmPushActionAckNotification().apply {
                type = NotificationType.SettingAppNotificationPermissionACK.value
                id = notification.id
                target = notification.target
                appId = notification.appId
                packageName = notification.packageName
                this.errorCode = errorCode
                if (!reason.isNullOrEmpty()) {
                    this.reason = reason
                }
                extra = notification.extra
            }
            MIPushHelper.sendPacket(
                pushAction,
                pushAction.context,
                MIPushHelper.constructResponseContainer(
                    container.packageName,
                    container.appid,
                    ackNotification,
                    ActionType.Notification,
                ),
            )
            pushAction.runtimeObserver.onNotificationEvent(
                notification.packageName,
                "service_setting_app_notification_permission_ack_sent",
                "MIPushAckDispatcher.sendSettingAppNotificationPermissionAck",
            )
        }
    }

    /**
     * Stock 7.5.29 m0.g:531-551: the __check_alive/__awake probe is answered with an
     * awake_system_app notification response (isRequest false) carrying app_running and,
     * when the target is not running, the echoed awaked flag (x0.f + x0.h plumbing).
     */
    @JvmStatic
    fun sendAwakeSystemAppResponse(
        pushAction: IPushServiceAction,
        container: XmPushActionContainer,
        targetPackage: String,
        appRunning: Boolean,
        awaked: Boolean,
    ) {
        enqueue(
            pushAction,
            "send awake system app response.",
            reason = "awake_system_app_response",
        ) {
            val response = XmPushActionNotification().apply {
                appId = container.appid
                packageName = targetPackage
                type = NotificationType.AwakeSystemApp.value
                id = container.metaInfo?.id
                extra = hashMapOf(
                    PushConstants.EXTRA_PARAM_APP_RUNNING to appRunning.toString(),
                ).apply {
                    if (!appRunning) {
                        put(PushConstants.EXTRA_PARAM_AWAKED, awaked.toString())
                    }
                }
            }
            MIPushHelper.sendPacket(
                pushAction,
                pushAction.context,
                MIPushHelper.constructResponseContainer(
                    container.packageName,
                    container.appid,
                    response,
                    ActionType.Notification,
                ),
            )
            pushAction.runtimeObserver.onNotificationEvent(
                container.packageName,
                "service_awake_system_app_response_sent",
                "MIPushAckDispatcher.sendAwakeSystemAppResponse",
            )
        }
    }

    @JvmStatic
    fun sendProfileIdMismatchAck(pushAction: IPushServiceAction, container: XmPushActionContainer) {
        enqueue(pushAction, "send ack message for checking profileId error ack message.", reason = "profile_id_mismatch") {
            val reason = "Profile ID is missing"
            val ackMessage = MIPushEventProcessor.constructAckMessage(pushAction.context, container)
            ackMessage.metaInfo?.apply {
                putToExtra(reason, "1")
                putToExtra(com.xiaomi.smack.packet.Message.MSG_TYPE_ERROR, "profileId_missing")
                putToExtra("reason", reason)
            }
            MIPushHelper.sendPacket(pushAction, pushAction.context, ackMessage)
            pushAction.runtimeObserver.onChannelEvent(
                container.packageName,
                "service_profile_id_error_ack_sent",
                "MIPushAckDispatcher.sendProfileIdMismatchAck",
            )
        }
    }

    @JvmStatic
    fun sendMIUINewAdsAckMessage(pushAction: IPushServiceAction, container: XmPushActionContainer) {
        enqueue(pushAction, "send ack message for unrecognized new miui message.", reason = "miui_new_ads") {
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
        enqueue(pushAction, "send ack message for obsleted message.", reason = "miui_old_ads") {
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

    private fun enqueue(
        pushAction: IPushServiceAction,
        description: String,
        reason: String,
        ackAction: AckAction,
    ) {
        pushAction.executeJob(
            object : XMPushServiceJob(TYPE_SEND_MSG) {
                override fun getDesc(): String = description

                override fun process() {
                    val startedAt = System.nanoTime()
                    try {
                        ackAction.process()
                        MagiskOtel.event(
                            name = "push.dispatch",
                            attributes = mapOf(
                                "result" to "ok",
                                "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                                "process" to "push",
                                "stage" to "ack_send",
                                "reason" to reason,
                            ),
                            statusOk = true,
                        )
                    } catch (e: Exception) {
                        MagiskOtel.event(
                            name = "push.dispatch",
                            attributes = mapOf(
                                "result" to "error",
                                "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                                "process" to "push",
                                "stage" to "ack_send",
                                "reason" to reason,
                                "error_class" to e.javaClass.simpleName,
                            ),
                            statusOk = false,
                        )
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
    @Suppress("LongParameterList")
    private fun sendServiceClearNotificationAck(
        pushAction: IPushServiceAction,
        context: Context,
        container: XmPushActionContainer,
        notification: XmPushActionNotification,
        errorCode: Long,
        msgId: String?,
        resultCode: Int,
        cancelType: Int,
    ) {
        // Stock e1.b.b(): the ack replays the control's extras and appends msgId (only
        // when non-empty), cancelType, hasPullDownCancel and resultCode
        // (TrackConstants.KEY_RESULT_CODE == "resultCode"). The service dispatcher never
        // reports a pull-down cancel here and always sends an empty reason.
        val ackExtras = HashMap(notification.extra ?: emptyMap())
        if (!msgId.isNullOrEmpty()) {
            ackExtras["msgId"] = msgId
        }
        ackExtras["cancelType"] = cancelType.toString()
        ackExtras["hasPullDownCancel"] = "0"
        ackExtras["resultCode"] = resultCode.toString()
        val ackNotification = XmPushActionAckNotification().apply {
            type = NotificationType.CancelPushMessageACK.value
            id = notification.id
            target = notification.target
            appId = notification.appId
            packageName = notification.packageName
            this.errorCode = errorCode
            reason = ""
            extra = ackExtras
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
