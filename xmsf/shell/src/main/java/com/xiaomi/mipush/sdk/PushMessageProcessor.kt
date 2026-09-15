package com.xiaomi.mipush.sdk

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.SharedPrefsCompat
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.mipush.sdk.PushMessageHandler.PushMessageInterface
import com.xiaomi.push.clientreport.PerfMessageHelper
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.clientReport.PushClientReportHelper
import com.xiaomi.push.service.clientReport.PushClientReportManager
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMessage
import com.xiaomi.xmpush.thrift.RegistrationReason
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmpush.thrift.XmPushActionSubscriptionResult
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionUnSubscriptionResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import java.util.LinkedList
import java.util.Queue
import java.util.TimeZone
import org.apache.thrift.TBase
import org.apache.thrift.TException
import io.github.magisk317.xposed.logging.MagiskOtel

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/mipush/sdk/d0.java
 */
class PushMessageProcessor private constructor(context: Context) {
    private var sAppContext: Context = context.applicationContext ?: context
    private val ackSupport = PushMessageProcessorAckSupport(sAppContext)
    private val processEventEmitter: PushMessageProcessorEventEmitter =
        { name, result, stage, reason, statusOk, extra ->
            emitProcessEvent(name, result, stage, reason, statusOk, extra)
        }
    private val actionResultSupport = PushMessageProcessorActionResultSupport(sAppContext, processEventEmitter)
    private val notificationActionSupport =
        PushMessageProcessorNotificationActionSupport(sAppContext, ackSupport, processEventEmitter)

    private fun ackMessage(container: XmPushActionContainer) = ackSupport.ackMessage(container)

    private fun ackMessage(sendMessage: XmPushActionSendMessage, container: XmPushActionContainer) =
        ackSupport.ackMessage(sendMessage, container)

    private fun isHybridMsg(container: XmPushActionContainer): Boolean =
        PushMessageProcessorMessageClassification.isHybridMessage(container.metaInfo?.extra)

    private fun emitProcessEvent(
        name: String,
        result: String,
        stage: String,
        reason: String,
        statusOk: Boolean = true,
        extra: Map<String, String> = emptyMap(),
    ) {
        val attrs = linkedMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "app",
            "stage" to stage,
            "reason" to reason,
        )
        attrs.putAll(extra)
        MagiskOtel.event(name = name, attributes = attrs, statusOk = statusOk)
    }

    private fun processMessage(
        container: XmPushActionContainer,
        fromNotification: Boolean,
        payload: ByteArray,
        messageId: String?,
        eventMessageType: Int
    ): PushMessageInterface? {
        try {
            val body = PushContainerHelper.getResponseMessageBodyFromContainer(sAppContext, container)
            if (body == null) {
                MyLog.e("receiving an un-recognized message. " + container.action)
                PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                    sAppContext.packageName,
                    PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                    messageId ?: "",
                    ReportConstants.ERROR_UN_RECOGNIZED_MSG
                )
                emitProcessEvent(
                    name = "push.event",
                    result = "error",
                    stage = "message_processor",
                    reason = "unrecognized",
                    statusOk = false,
                    extra = mapOf(
                        "msg_type" to (container.action?.name ?: "unknown"),
                        "target_package" to (container.packageName ?: sAppContext.packageName),
                    ),
                )
                return null
            }
            val action = container.action
            MyLog.w("processing a message, action=$action")
            return when (action) {
                ActionType.SendMessage -> processSendMessage(
                    container,
                    body as XmPushActionSendMessage,
                    fromNotification,
                    payload,
                    messageId,
                    eventMessageType
                )

                ActionType.Registration -> processRegistrationResult(body as XmPushActionRegistrationResult, messageId, eventMessageType)
                ActionType.UnRegistration -> {
                    val unreg = body as XmPushActionUnRegistrationResult
                    if (unreg.errorCode == 0L) {
                        AppInfoHolder.getInstance(sAppContext).clear()
                        MiPushClient.clearExtras(sAppContext)
                        emitProcessEvent(
                            name = "push.register",
                            result = "ok",
                            stage = "unregistration_result",
                            reason = "success",
                            extra = mapOf("target_package" to sAppContext.packageName),
                        )
                    } else {
                        emitProcessEvent(
                            name = "push.register",
                            result = "error",
                            stage = "unregistration_result",
                            reason = "error_code",
                            statusOk = false,
                            extra = mapOf(
                                "target_package" to sAppContext.packageName,
                                "error_code" to unreg.errorCode.toString(),
                            ),
                        )
                    }
                    PushMessageHandler.removeAllPushCallbackClass()
                    null
                }

                ActionType.Subscription -> processSubscriptionResult(body as XmPushActionSubscriptionResult)
                ActionType.UnSubscription -> processUnSubscriptionResult(body as XmPushActionUnSubscriptionResult)
                ActionType.Command -> processCommandResult(body as XmPushActionCommandResult, payload)
                ActionType.Notification -> processNotification(container, body, payload)
                else -> null
            }
        } catch (e: DecryptException) {
            MyLog.e(e)
            reportDecryptFail(container)
            PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                sAppContext.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.ERROR_DECRYPT_MSG_FAILED
            )
            emitProcessEvent(
                name = "push.event",
                result = "error",
                stage = "message_processor",
                reason = "decrypt_failed",
                statusOk = false,
                extra = mapOf(
                    "msg_type" to (container.action?.name ?: "unknown"),
                    "target_package" to (container.packageName ?: sAppContext.packageName),
                ),
            )
            return null
        } catch (e: TException) {
            MyLog.e(e)
            MyLog.e("receive a message which action string is not valid. is the reg expired?")
            PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                sAppContext.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.ERROR_DESERIALIZE_MSG_T_EXCEPTION
            )
            emitProcessEvent(
                name = "push.event",
                result = "error",
                stage = "message_processor",
                reason = "deserialize_failed",
                statusOk = false,
                extra = mapOf(
                    "msg_type" to (container.action?.name ?: "unknown"),
                    "target_package" to (container.packageName ?: sAppContext.packageName),
                ),
            )
            return null
        }
    }

    private fun processSendMessage(
        container: XmPushActionContainer,
        sendMessage: XmPushActionSendMessage,
        fromNotification: Boolean,
        payload: ByteArray,
        messageId: String?,
        eventMessageType: Int
    ): PushMessageInterface? {
        if (!container.isEncryptAction) {
            MyLog.e("receiving an un-encrypt message(SendMessage).")
            return null
        }
        if (AppInfoHolder.getInstance(sAppContext).isPaused && !fromNotification) {
            MyLog.w("receive a message in pause state. drop it")
            PushClientReportManager.getInstance(sAppContext).reportEvent4NeedDrop(
                sAppContext.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.DROP_PAUSE_STATE
            )
            return null
        }
        val message = sendMessage.message
        if (message == null) {
            MyLog.e("receive an empty message without push content, drop it")
            PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                sAppContext.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.ERROR_EMPTY_MSG_WITHOUT_CONTENT
            )
            return null
        }
        if (fromNotification) {
            if (MIPushNotificationHelper.isBusinessMessage(container)) {
                MiPushClient.reportIgnoreRegMessageClicked(
                    sAppContext,
                    message.id,
                    container.metaInfo,
                    container.packageName,
                    message.appId
                )
            } else {
                MiPushClient.reportMessageClicked(sAppContext, message.id, container.metaInfo, message.appId)
            }
        }
        if (!fromNotification) {
            if (!TextUtils.isEmpty(sendMessage.aliasName) && MiPushClient.aliasSetTime(sAppContext, sendMessage.aliasName) < 0) {
                MiPushClient.addAlias(sAppContext, sendMessage.aliasName)
            } else if (!TextUtils.isEmpty(sendMessage.topic) && MiPushClient.topicSubscribedTime(sAppContext, sendMessage.topic) < 0) {
                MiPushClient.addTopic(sAppContext, sendMessage.topic)
            }
        }

        var id = container.metaInfo?.extra?.get(PushConstants.EXTRA_JOB_KEY)
        val jobKey = id
        if (TextUtils.isEmpty(id)) {
            id = message.id
        }
        val miPushMessage = if (fromNotification || !isDuplicateMessage(sAppContext, id)) {
            val generatedMessage = PushMessageHelper.generateMessage(sendMessage, container.metaInfo, fromNotification)
            if (generatedMessage.passThrough == 0 &&
                !fromNotification &&
                MIPushNotificationHelper.isNotifyForeground(generatedMessage.extra)
            ) {
                MIPushNotificationHelper.notifyPushMessage(sAppContext, container, payload)
                return null
            }
            MyLog.w("receive a message, msgid=" + message.id + ", jobkey=" + id)
            if (fromNotification &&
                generatedMessage.extra != null &&
                generatedMessage.extra!!.containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT)
            ) {
                processClickedNotifyEffect(container, message, generatedMessage, messageId, jobKey, eventMessageType)
                return null
            }
            generatedMessage
        } else {
            MyLog.w("drop a duplicate message, key=$id")
            PushClientReportManager.getInstance(sAppContext).reportEvent4DUPMD(
                sAppContext.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                "2:$id"
            )
            null
        }
        if (container.metaInfo == null && !fromNotification) {
            ackMessage(sendMessage, container)
        }
        return miPushMessage
    }

    private fun processClickedNotifyEffect(
        container: XmPushActionContainer,
        message: PushMessage,
        miPushMessage: MiPushMessage,
        messageId: String?,
        jobKey: String?,
        eventMessageType: Int
    ) {
        val extra = miPushMessage.extra ?: return
        val notifyEffect = extra[PushConstants.EXTRA_PARAM_NOTIFY_EFFECT]
        if (MIPushNotificationHelper.isBusinessMessage(container)) {
            val notificationIntent = getNotificationMessageIntent(sAppContext, container.packageName, extra) ?: return
            notificationIntent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, eventMessageType)
            notificationIntent.putExtra("messageId", messageId)
            notificationIntent.putExtra(PushConstants.EXTRA_JOB_KEY, jobKey)
            val payload = message.payload
            if (!TextUtils.isEmpty(payload)) {
                notificationIntent.putExtra("payload", payload)
            }
            sAppContext.startActivity(notificationIntent)
            PushClientReportManager.getInstance(sAppContext).reportEvent(
                sAppContext.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.AWAKE_TYPE_PROCESS_AFTER_CLICK,
                notifyEffect
            )
            return
        }
        val notificationIntent = getNotificationMessageIntent(sAppContext, sAppContext.packageName, extra) ?: return
        if (notifyEffect != PushConstants.NOTIFICATION_CLICK_WEB_PAGE) {
            notificationIntent.putExtra(PushMessageHelper.KEY_MESSAGE, miPushMessage)
            notificationIntent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, eventMessageType)
            notificationIntent.putExtra("messageId", messageId)
            notificationIntent.putExtra(PushConstants.EXTRA_JOB_KEY, jobKey)
        }
        sAppContext.startActivity(notificationIntent)
        MyLog.w("start activity succ")
        PushClientReportManager.getInstance(sAppContext).reportEvent(
            sAppContext.packageName,
            PushClientReportHelper.getInterfaceIdByType(eventMessageType),
            messageId ?: "",
            ReportConstants.NOTIFICATION_TYPE_PROCESS_AFTER_CLICK,
            notifyEffect
        )
        if (notifyEffect == PushConstants.NOTIFICATION_CLICK_WEB_PAGE) {
            PushClientReportManager.getInstance(sAppContext).reportEvent4NeedDrop(
                sAppContext.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.DROP_OPEN_WEB
            )
        }
    }

    private fun processRegistrationResult(
        result: XmPushActionRegistrationResult,
        messageId: String?,
        eventMessageType: Int
    ): PushMessageInterface? = actionResultSupport.processRegistrationResult(result, messageId, eventMessageType)

    private fun processSubscriptionResult(result: XmPushActionSubscriptionResult): PushMessageInterface =
        actionResultSupport.processSubscriptionResult(result)

    private fun processUnSubscriptionResult(result: XmPushActionUnSubscriptionResult): PushMessageInterface =
        actionResultSupport.processUnSubscriptionResult(result)

    private fun processCommandResult(result: XmPushActionCommandResult, payload: ByteArray): PushMessageInterface? =
        actionResultSupport.processCommandResult(result, payload)

    private fun processNotification(
        container: XmPushActionContainer,
        body: TBase<*, *>,
        payload: ByteArray
    ): PushMessageInterface? {
        PerfMessageHelper.collectPerfData(sAppContext.packageName, sAppContext, body, ActionType.Notification, payload.size)
        if (body is XmPushActionAckNotification) {
            emitProcessEvent(
                name = "push.event",
                result = "ok",
                stage = "notification_process",
                reason = "ack",
                extra = mapOf(
                    "target_package" to (container.packageName ?: sAppContext.packageName),
                    "msg_type" to "ack_notification",
                ),
            )
            return processAckNotification(body)
        }
        if (body !is XmPushActionNotification) {
            emitProcessEvent(
                name = "push.event",
                result = "skip",
                stage = "notification_process",
                reason = "unsupported_body",
                extra = mapOf(
                    "target_package" to (container.packageName ?: sAppContext.packageName),
                ),
            )
            return null
        }
        emitProcessEvent(
            name = "push.event",
            result = "ok",
            stage = "notification_process",
            reason = "notification",
            extra = mapOf(
                "target_package" to (container.packageName ?: sAppContext.packageName),
                "msg_type" to "notification",
                "payload_size" to payload.size.toString(),
            ),
        )
        return processNotificationMessage(container, body)
    }

    private fun processAckNotification(notification: XmPushActionAckNotification): PushMessageInterface? =
        notificationActionSupport.processAckNotification(notification)

    private fun processEnableDisableAck(
        notification: XmPushActionAckNotification,
        id: String?,
        retryType: RetryType,
        enable: Boolean
    ) {
        notificationActionSupport.processEnableDisableAck(notification, id, retryType, enable)
    }

    private fun processNotificationMessage(
        container: XmPushActionContainer,
        notification: XmPushActionNotification
    ): PushMessageInterface? = notificationActionSupport.processNotificationMessage(container, notification)

    private fun processMessage(container: XmPushActionContainer, payload: ByteArray): PushMessageInterface? {
        try {
            val body = PushContainerHelper.getResponseMessageBodyFromContainer(sAppContext, container)
            if (body == null) {
                MyLog.e("message arrived: receiving an un-recognized message. " + container.action)
                return null
            }
            val action = container.action
            MyLog.w("message arrived: processing an arrived message, action=$action")
            if (action == ActionType.SendMessage) {
                if (!container.isEncryptAction) {
                    MyLog.e("message arrived: receiving an un-encrypt message(SendMessage).")
                } else {
                    val sendMessage = body as XmPushActionSendMessage
                    val message = sendMessage.message
                    if (message != null) {
                        val jobKey = container.metaInfo?.extra?.get(PushConstants.EXTRA_JOB_KEY)
                        val miPushMessage = PushMessageHelper.generateMessage(sendMessage, container.metaInfo, false)
                        miPushMessage.setArrivedMessage(true)
                        MyLog.w("message arrived: receive a message, msgid=" + message.id + ", jobkey=" + jobKey)
                        return miPushMessage
                    } else {
                        MyLog.e("message arrived: receive an empty message without push content, drop it")
                    }
                }
                return null
            }
            return null
        } catch (e: DecryptException) {
            MyLog.e(e)
            MyLog.e("message arrived: receive a message but decrypt failed. report when click.")
            return null
        } catch (e: TException) {
            MyLog.e(e)
            MyLog.e("message arrived: receive a message which action string is not valid. is the reg expired?")
            return null
        }
    }

    private fun processSendTokenAckNotification(notification: XmPushActionAckNotification) {
        notificationActionSupport.processSendTokenAckNotification(notification)
    }

    private fun processSingleTokenACK(id: String?, errorCode: Long, assemblePush: AssemblePush) {
        notificationActionSupport.processSingleTokenACK(id, errorCode, assemblePush)
    }

    private fun processStatDataACK(notification: XmPushActionAckNotification) {
        notificationActionSupport.processStatDataACK(notification)
    }

    private fun reportDecryptFail(container: XmPushActionContainer) = ackSupport.reportDecryptFail(container)

    private fun sendAckNotification(notification: XmPushActionNotification) =
        ackSupport.sendAckNotification(notification)

    private fun tryToReinitialize() {
        val sharedPreferences = sAppContext.getSharedPreferences("mipush_extra", 0)
        val currentTimeMillis = System.currentTimeMillis()
        if (kotlin.math.abs(currentTimeMillis - sharedPreferences.getLong(Constants.SP_KEY_LAST_REINITIALIZE, 0L)) > 1800000) {
            MiPushClient.reInitialize(sAppContext, RegistrationReason.PackageUnregistered)
            sharedPreferences.edit().putLong(Constants.SP_KEY_LAST_REINITIALIZE, currentTimeMillis).apply()
        }
    }

    fun getTimeForTimeZone(timeZone: TimeZone, targetTimeZone: TimeZone, list: List<String>): List<String> =
        PushMessageProcessorTimeZoneConverter.convert(timeZone, targetTimeZone, list)

    fun processIntent(intent: Intent): PushMessageInterface? {
        val action = intent.action
        MyLog.w("receive an intent from server, action=$action")
        val receiveTime = intent.getStringExtra(PushConstants.MESSAGE_RECEIVE_TIME)
            ?: System.currentTimeMillis().toString()
        val messageId = intent.getStringExtra("messageId")
        val eventMessageType = intent.getIntExtra(ReportConstants.EVENT_MESSAGE_TYPE, -1)
        if (PushConstants.MIPUSH_ACTION_NEW_MESSAGE != action) {
            if (PushConstants.MIPUSH_ACTION_ERROR == action) {
                val commandMessage = MiPushCommandMessage()
                val container = XmPushActionContainer()
                try {
                    val payload = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
                    if (payload != null) {
                        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload)
                    }
                } catch (_: TException) {
                }
                commandMessage.command = container.action.toString()
                commandMessage.resultCode = intent.getIntExtra(PushConstants.MIPUSH_EXTRA_ERROR_CODE, 0).toLong()
                commandMessage.reason = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_ERROR_MSG)
                MyLog.e(
                    "receive a error message. code = " +
                        intent.getIntExtra(PushConstants.MIPUSH_EXTRA_ERROR_CODE, 0) +
                        ", msg= " +
                        intent.getStringExtra(PushConstants.MIPUSH_EXTRA_ERROR_MSG)
                )
                return commandMessage
            }
            if (PushConstants.MIPUSH_ACTION_MESSAGE_ARRIVED != action) {
                return null
            }
            val payload = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
            if (payload == null) {
                MyLog.e("message arrived: receiving an empty message, drop")
                return null
            }
            val container = XmPushActionContainer()
            try {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload)
                val appInfoHolder = AppInfoHolder.getInstance(sAppContext)
                if (MIPushNotificationHelper.isBusinessMessage(container)) {
                    MyLog.e("message arrived: receive ignore reg message, ignore!")
                } else if (!appInfoHolder.appRegistered()) {
                    MyLog.e("message arrived: receive message without registration. need unregister or re-register!")
                } else {
                    if (!appInfoHolder.appRegistered() || !appInfoHolder.invalidated()) {
                        return processMessage(container, payload)
                    }
                    MyLog.e("message arrived: app info is invalidated")
                }
                return null
            } catch (e: Exception) {
                MyLog.e("fail to deal with arrived message. $e")
                return null
            }
        }

        val payload = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
        val fromNotification = intent.getBooleanExtra(MIPushNotificationHelper.FROM_NOTIFICATION, false)
        if (payload == null) {
            MyLog.e("receiving an empty message, drop")
            PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                sAppContext.packageName,
                intent,
                ReportConstants.ERROR_DROP_EMPTY_MESSAGE
            )
            return null
        }
        val container = XmPushActionContainer()
        try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload)
            val appInfoHolder = AppInfoHolder.getInstance(sAppContext)
            val metaInfo = container.metaInfo
            if (container.action == ActionType.SendMessage && metaInfo != null) {
                try {
                    if (!appInfoHolder.isPaused && !fromNotification) {
                        metaInfo.putToExtra(PushConstants.MESSAGE_RECEIVE_TIME, receiveTime)
                        metaInfo.putToExtra(PushConstants.MESSAGE_ACK_TIME, System.currentTimeMillis().toString())
                        if (isHybridMsg(container)) {
                            MyLog.i("this is a mina's message, ack later")
                            metaInfo.putToExtra(Constants.EXTRA_KEY_HYBRID_MESSAGE_TS, metaInfo.messageTs.toString())
                            metaInfo.putToExtra(
                                Constants.EXTRA_KEY_HYBRID_DEVICE_STATUS,
                                XmPushThriftSerializeUtils.getDeviceStatus(sAppContext, container).toInt().toString()
                            )
                        } else {
                            ackMessage(container)
                        }
                    }
                } catch (e: Exception) {
                    PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                        sAppContext.packageName,
                        intent,
                        ReportConstants.ERROR_PROCESS_RECEIVE_MESSAGE_EXCEPTION
                    )
                    MyLog.e(e)
                    return null
                }
            }
            if (container.action == ActionType.SendMessage && !container.isEncryptAction) {
                if (MIPushNotificationHelper.isBusinessMessage(container)) {
                    MyLog.w(
                        String.format(
                            "drop an un-encrypted wake-up messages. %1\$s, %2\$s",
                            container.packageName,
                            metaInfo?.id ?: ""
                        )
                    )
                    PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                        sAppContext.packageName,
                        intent,
                        String.format("13: %1\$s", container.packageName)
                    )
                    return null
                }
                MyLog.w(
                    String.format(
                        "drop an un-encrypted messages. %1\$s, %2\$s",
                        container.packageName,
                        metaInfo?.id ?: ""
                    )
                )
                PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                    sAppContext.packageName,
                    intent,
                    String.format("14: %1\$s", container.packageName)
                )
                return null
            }
            if (container.action == ActionType.SendMessage &&
                container.isEncryptAction &&
                MIPushNotificationHelper.isBusinessMessage(container) &&
                (!fromNotification || metaInfo == null || metaInfo.extra == null || !metaInfo.extra.containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT))
            ) {
                MyLog.w(
                    String.format(
                        "drop a wake-up messages which not has 'notify_effect' attr. %1\$s, %2\$s",
                        container.packageName,
                        metaInfo?.id ?: ""
                    )
                )
                PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                    sAppContext.packageName,
                    intent,
                    String.format("25: %1\$s", container.packageName)
                )
                return null
            }
            try {
                if (!appInfoHolder.appRegistered() && container.action != ActionType.Registration) {
                    if (MIPushNotificationHelper.isBusinessMessage(container)) {
                        return processMessage(container, fromNotification, payload, messageId, eventMessageType)
                    }
                    MyLog.e("receive message without registration. need re-register!")
                    PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                        sAppContext.packageName,
                        intent,
                        ReportConstants.ERROR_UN_REGISTER_NOT_AWAKE_MSG
                    )
                    tryToReinitialize()
                    return null
                }
                if (!appInfoHolder.appRegistered() || !appInfoHolder.invalidated()) {
                    return processMessage(container, fromNotification, payload, messageId, eventMessageType)
                }
                if (container.action != ActionType.UnRegistration) {
                    MiPushClient.unregisterPush(sAppContext)
                    return null
                }
                appInfoHolder.clear()
                MiPushClient.clearExtras(sAppContext)
                PushMessageHandler.removeAllPushCallbackClass()
                return null
            } catch (e: Exception) {
                PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                    sAppContext.packageName,
                    intent,
                    ReportConstants.ERROR_PROCESS_RECEIVE_MESSAGE_EXCEPTION
                )
                MyLog.e(e)
                return null
            }
        } catch (e: TException) {
            PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                sAppContext.packageName,
                intent,
                ReportConstants.ERROR_PROCESS_RECEIVE_MESSAGE_T_EXCEPTION
            )
            MyLog.e(e)
            return null
        } catch (e: Exception) {
            PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                sAppContext.packageName,
                intent,
                ReportConstants.ERROR_PROCESS_RECEIVE_MESSAGE_T_EXCEPTION
            )
            MyLog.e(e)
            return null
        }
    }

    companion object {
        private const val MAX_MSG_CACHE_COUNT = 25
        private const val PREF_KEY_CACHED_MSGIDS = "pref_msg_ids"

        private var mCachedMsgIds: Queue<String?>? = null
        private var sInstance: PushMessageProcessor? = null
        private val lock = Any()

        @JvmStatic
        fun getInstance(context: Context): PushMessageProcessor {
            return sInstance ?: synchronized(lock) {
                sInstance ?: PushMessageProcessor(context).also { sInstance = it }
            }
        }

        @JvmStatic
        fun getNotificationMessageIntent(context: Context, packageName: String, map: Map<String, String>?): Intent? =
            PushMessageProcessorNotificationIntentFactory.getNotificationMessageIntent(context, packageName, map)


        private fun isDuplicateMessage(context: Context, messageId: String?): Boolean = synchronized(lock) {
            AppInfoHolder.getInstance(context)
            val sharedPreferences = AppInfoHolder.getSharedPreferences(context)
            if (mCachedMsgIds == null) {
                val msgIds = sharedPreferences.getString(PREF_KEY_CACHED_MSGIDS, "")!!.split(",")
                mCachedMsgIds = LinkedList()
                for (cachedId in msgIds) {
                    mCachedMsgIds!!.add(cachedId)
                }
            }
            if (mCachedMsgIds!!.contains(messageId)) {
                return@synchronized true
            }
            mCachedMsgIds!!.add(messageId)
            if (mCachedMsgIds!!.size > MAX_MSG_CACHE_COUNT) {
                mCachedMsgIds!!.poll()
            }
            val joined = XMStringUtils.join(mCachedMsgIds, ",")
            val editor = sharedPreferences.edit()
            editor.putString(PREF_KEY_CACHED_MSGIDS, joined)
            SharedPrefsCompat.apply(editor)
            false
        }

        @JvmStatic
        fun removeCachedDupKey(context: Context, messageId: String?) {
            synchronized(lock) {
                mCachedMsgIds?.remove(messageId) ?: return
                AppInfoHolder.getInstance(context)
                val sharedPreferences: SharedPreferences = AppInfoHolder.getSharedPreferences(context)
                val joined = XMStringUtils.join(mCachedMsgIds, ",")
                val editor = sharedPreferences.edit()
                editor.putString(PREF_KEY_CACHED_MSGIDS, joined)
                SharedPrefsCompat.apply(editor)
            }
        }
    }
}
