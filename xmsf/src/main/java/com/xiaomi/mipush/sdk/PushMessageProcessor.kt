package com.xiaomi.mipush.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.net.Uri
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.SharedPrefsCompat
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.mipush.sdk.PushMessageHandler.PushMessageInterface
import com.xiaomi.mipush.sdk.stat.PushStatClientManager
import com.xiaomi.mipush.sdk.stat.upload.UploadDataHelper
import com.xiaomi.push.clientreport.PerfMessageHelper
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.push.service.OnlineConfigHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.awake.AwakeUploadHelper
import com.xiaomi.push.service.clientReport.PushClientReportHelper
import com.xiaomi.push.service.clientReport.PushClientReportManager
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.push.service.xmpush.Command
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.ConfigKey
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.PushMessage
import com.xiaomi.xmpush.thrift.RegistrationReason
import com.xiaomi.xmpush.thrift.XmPushActionAckMessage
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionCustomConfig
import com.xiaomi.xmpush.thrift.XmPushActionNormalConfig
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmpush.thrift.XmPushActionSubscriptionResult
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionUnSubscriptionResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import io.github.magisk317.mipush.runtime.PushRuntime
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.LinkedList
import java.util.Queue
import java.util.Locale
import java.util.TimeZone
import android.content.pm.PackageManager
import org.apache.thrift.TBase
import org.apache.thrift.TException

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/mipush/sdk/d0.java
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/PushMessageProcessor.java
 */
class PushMessageProcessor private constructor(context: Context) {
    private var sAppContext: Context = context.applicationContext ?: context

    private fun ackMessage(container: XmPushActionContainer) {
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

    private fun ackMessage(sendMessage: XmPushActionSendMessage, container: XmPushActionContainer) {
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

    private fun isHybridMsg(container: XmPushActionContainer): Boolean {
        val extra = container.metaInfo?.extra ?: return false
        val action = extra[Constants.EXTRA_KEY_PUSH_SERVER_ACTION]
        return TextUtils.equals(action, Constants.EXTRA_VALUE_HYBRID_MESSAGE) ||
            TextUtils.equals(action, Constants.EXTRA_VALUE_PLATFORM_MESSAGE)
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
                    if ((body as XmPushActionUnRegistrationResult).errorCode == 0L) {
                        AppInfoHolder.getInstance(sAppContext).clear()
                        MiPushClient.clearExtras(sAppContext)
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
            val notificationIntent = getNotificationMessageIntent(sAppContext, container.packageName, extra)!!
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
    ): PushMessageInterface? {
        val appInfoHolder = AppInfoHolder.getInstance(sAppContext)
        val requestId = appInfoHolder.appRegRequestId
        MyLog.w(
            "registration result received errorCode=${result.errorCode} reason=${result.reason} " +
                "requestIdPresent=${!TextUtils.isEmpty(requestId)} resultIdMatch=${TextUtils.equals(requestId, result.id)} " +
                "resultAppIdPresent=${!TextUtils.isEmpty(result.appId)} resultAppIdMatch=${TextUtils.equals(appInfoHolder.appID, result.appId)} " +
                "regIdPresent=${!TextUtils.isEmpty(result.regId)} regSecretPresent=${!TextUtils.isEmpty(result.regSecret)} " +
                "regionPresent=${!TextUtils.isEmpty(result.region)} messageIdPresent=${!TextUtils.isEmpty(messageId)} " +
                appInfoHolder.registrationStateSummary(result.appId, appInfoHolder.appToken)
        )
        if (TextUtils.isEmpty(requestId) || !TextUtils.equals(requestId, result.id)) {
            MyLog.w("bad Registration result: " + appInfoHolder.registrationStateSummary(result.appId, appInfoHolder.appToken))
            PushClientReportManager.getInstance(sAppContext).reportEvent4ERROR(
                sAppContext.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.ERROR_BAD_REGISTRATION_RESULT
            )
            return null
        }
        appInfoHolder.appRegRequestId = null
        if (result.errorCode == 0L) {
            appInfoHolder.putRegIDAndSecret(result.regId, result.regSecret, result.region)
            MyLog.w("registration result stored " + appInfoHolder.registrationStateSummary(result.appId, appInfoHolder.appToken))
            PushClientReportManager.getInstance(sAppContext).reportEvent(
                sAppContext.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.REGISTER_TYPE_APP_SUCCESS,
                ReportConstants.REGISTER_SUCCESS
            )
        } else {
            MyLog.w(
                "registration result failed errorCode=${result.errorCode} reason=${result.reason} " +
                    appInfoHolder.registrationStateSummary(result.appId, appInfoHolder.appToken)
            )
            PushClientReportManager.getInstance(sAppContext).reportEvent(
                sAppContext.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.REGISTER_TYPE_APP_SUCCESS,
                ReportConstants.REGISTER_FAIL
            )
        }
        val args = if (!TextUtils.isEmpty(result.regId)) arrayListOf(result.regId) else null
        val commandMessage = PushMessageHelper.generateCommandMessage(
            Command.COMMAND_REGISTER.value,
            args,
            result.errorCode,
            result.reason,
            null
        )
        PushServiceClient.getInstance(sAppContext).processPendRequest()
        return commandMessage
    }

    private fun processSubscriptionResult(result: XmPushActionSubscriptionResult): PushMessageInterface {
        if (result.errorCode == 0L) {
            MiPushClient.addTopic(sAppContext, result.topic)
        }
        val args = if (!TextUtils.isEmpty(result.topic)) arrayListOf(result.topic) else null
        MyLog.persist("resp-cmd:" + Command.COMMAND_SUBSCRIBE_TOPIC + ", " + result.id)
        return PushMessageHelper.generateCommandMessage(
            Command.COMMAND_SUBSCRIBE_TOPIC.value,
            args,
            result.errorCode,
            result.reason,
            result.category
        )
    }

    private fun processUnSubscriptionResult(result: XmPushActionUnSubscriptionResult): PushMessageInterface {
        if (result.errorCode == 0L) {
            MiPushClient.removeTopic(sAppContext, result.topic)
        }
        val args = if (!TextUtils.isEmpty(result.topic)) arrayListOf(result.topic) else null
        MyLog.persist("resp-cmd:" + Command.COMMAND_UNSUBSCRIBE_TOPIC + ", " + result.id)
        return PushMessageHelper.generateCommandMessage(
            Command.COMMAND_UNSUBSCRIBE_TOPIC.value,
            args,
            result.errorCode,
            result.reason,
            result.category
        )
    }

    private fun processCommandResult(result: XmPushActionCommandResult, payload: ByteArray): PushMessageInterface? {
        PerfMessageHelper.collectPerfData(sAppContext.packageName, sAppContext, result, ActionType.Command, payload.size)
        val cmdName = result.cmdName
        var cmdArgs = result.cmdArgs
        if (result.errorCode == 0L) {
            if (TextUtils.equals(cmdName, Command.COMMAND_SET_ACCEPT_TIME.value) && cmdArgs != null && cmdArgs.size > 1) {
                MiPushClient.addAcceptTime(sAppContext, cmdArgs[0], cmdArgs[1])
                AppInfoHolder.getInstance(sAppContext).setPaused("00:00" == cmdArgs[0] && "00:00" == cmdArgs[1])
                cmdArgs = getTimeForTimeZone(TimeZone.getTimeZone("GMT+08"), TimeZone.getDefault(), cmdArgs)
            } else if (TextUtils.equals(cmdName, Command.COMMAND_SET_ALIAS.value) && !cmdArgs.isNullOrEmpty()) {
                MiPushClient.addAlias(sAppContext, cmdArgs[0])
            } else if (TextUtils.equals(cmdName, Command.COMMAND_UNSET_ALIAS.value) && !cmdArgs.isNullOrEmpty()) {
                MiPushClient.removeAlias(sAppContext, cmdArgs[0])
            } else if (TextUtils.equals(cmdName, Command.COMMAND_SET_ACCOUNT.value) && !cmdArgs.isNullOrEmpty()) {
                MiPushClient.addAccount(sAppContext, cmdArgs[0])
            } else if (TextUtils.equals(cmdName, Command.COMMAND_UNSET_ACCOUNT.value) && !cmdArgs.isNullOrEmpty()) {
                MiPushClient.removeAccount(sAppContext, cmdArgs[0])
            } else if (TextUtils.equals(cmdName, Command.COMMAND_CHK_VDEVID.value)) {
                if (cmdArgs.isNullOrEmpty()) {
                    return null
                }
                DeviceInfo.updateVirtDevId(sAppContext, cmdArgs[0])
                return null
            }
        }
        MyLog.persist("resp-cmd:$cmdName, " + result.id)
        return PushMessageHelper.generateCommandMessage(
            cmdName,
            cmdArgs,
            result.errorCode,
            result.reason,
            result.category
        )
    }

    private fun processNotification(
        container: XmPushActionContainer,
        body: TBase<*, *>,
        payload: ByteArray
    ): PushMessageInterface? {
        PerfMessageHelper.collectPerfData(sAppContext.packageName, sAppContext, body, ActionType.Notification, payload.size)
        if (body is XmPushActionAckNotification) {
            return processAckNotification(body)
        }
        if (body !is XmPushActionNotification) {
            return null
        }
        return processNotificationMessage(container, body)
    }

    private fun processAckNotification(notification: XmPushActionAckNotification): PushMessageInterface? {
        val id = notification.id
        MyLog.persist("resp-type:" + notification.type + ", code:" + notification.errorCode + ", " + id)
        if (NotificationType.DisablePushMessage.value.equals(notification.type, ignoreCase = true)) {
            processEnableDisableAck(notification, id, RetryType.DISABLE_PUSH, enable = false)
            return null
        }
        if (NotificationType.EnablePushMessage.value.equals(notification.type, ignoreCase = true)) {
            processEnableDisableAck(notification, id, RetryType.ENABLE_PUSH, enable = true)
            return null
        }
        if (NotificationType.ThirdPartyRegUpdate.value.equals(notification.type, ignoreCase = true)) {
            processSendTokenAckNotification(notification)
            return null
        }
        if (NotificationType.UploadTinyData.value.equals(notification.type, ignoreCase = true)) {
            processStatDataACK(notification)
        }
        return null
    }

    private fun processEnableDisableAck(
        notification: XmPushActionAckNotification,
        id: String?,
        retryType: RetryType,
        enable: Boolean
    ) {
        if (notification.errorCode == 0L) {
            synchronized(OperatePushHelper::class.java) {
                val helper = OperatePushHelper.getInstance(sAppContext)
                if (helper.isMessageOperating(id)) {
                    helper.removeOperateMessage(id)
                    if (OperatePushHelper.SYNCING == helper.getSyncStatus(retryType)) {
                        helper.putSyncStatus(retryType, OperatePushHelper.SYNCED)
                        if (!enable) {
                            MiPushClient.clearNotification(sAppContext)
                            MiPushClient.clearLocalNotificationType(sAppContext)
                            PushMessageHandler.removeAllPushCallbackClass()
                            PushServiceClient.getInstance(sAppContext).closePush()
                        }
                    }
                }
            }
            return
        }
        if (OperatePushHelper.SYNCING != OperatePushHelper.getInstance(sAppContext).getSyncStatus(retryType)) {
            OperatePushHelper.getInstance(sAppContext).removeOperateMessage(id)
            return
        }
        synchronized(OperatePushHelper::class.java) {
            val helper = OperatePushHelper.getInstance(sAppContext)
            if (helper.isMessageOperating(id)) {
                if (helper.getRetryCount(id) < 10) {
                    helper.increaseRetryCount(id)
                    PushServiceClient.getInstance(sAppContext).sendPushEnableDisableMessage(!enable, id)
                } else {
                    helper.removeOperateMessage(id)
                }
            }
        }
    }

    private fun processNotificationMessage(
        container: XmPushActionContainer,
        notification: XmPushActionNotification
    ): PushMessageInterface? {
        if ("registration id expired".equals(notification.type, ignoreCase = true)) {
            val allAlias = MiPushClient.getAllAlias(sAppContext)
            val allTopic = MiPushClient.getAllTopic(sAppContext)
            val allUserAccount = MiPushClient.getAllUserAccount(sAppContext)
            val acceptTime = MiPushClient.getAcceptTime(sAppContext)
            MyLog.persist("resp-type:" + notification.type + ", " + notification.id)
            MiPushClient.reInitialize(sAppContext, RegistrationReason.RegIdExpired)
            for (alias in allAlias) {
                MiPushClient.removeAlias(sAppContext, alias)
                MiPushClient.setAlias(sAppContext, alias, null)
            }
            for (topic in allTopic) {
                MiPushClient.removeTopic(sAppContext, topic)
                MiPushClient.subscribe(sAppContext, topic, null)
            }
            for (account in allUserAccount) {
                MiPushClient.removeAccount(sAppContext, account)
                MiPushClient.setUserAccount(sAppContext, account, null)
            }
            val acceptTimeParts = acceptTime.split(",")
            if (acceptTimeParts.size != 2) {
                return null
            }
            MiPushClient.removeAcceptTime(sAppContext)
            MiPushClient.addAcceptTime(sAppContext, acceptTimeParts[0], acceptTimeParts[1])
            return null
        }
        if (NotificationType.ClientInfoUpdateOk.value.equals(notification.type, ignoreCase = true)) {
            val extra = notification.extra
            if (extra == null || !extra.containsKey(Constants.EXTRA_KEY_APP_VERSION)) {
                return null
            }
            AppInfoHolder.getInstance(sAppContext).updateVersionName(extra[Constants.EXTRA_KEY_APP_VERSION])
            return null
        }
        if (NotificationType.AwakeApp.value.equals(notification.type, ignoreCase = true)) {
            val extra = notification.extra
            if (!container.isEncryptAction || extra == null || !extra.containsKey(AwakeUploadHelper.KEY_AWAKE_INFO)) {
                return null
            }
            AwakeHelper.doAwAppLogic(
                sAppContext,
                AppInfoHolder.getInstance(sAppContext).appID,
                OnlineConfig.getInstance(sAppContext).getIntValue(ConfigKey.AwakeInfoUploadWaySwitch.value, 0),
                extra[AwakeUploadHelper.KEY_AWAKE_INFO]
            )
            return null
        }
        if (NotificationType.NormalClientConfigUpdate.value.equals(notification.type, ignoreCase = true)) {
            val normalConfig = XmPushActionNormalConfig()
            try {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(normalConfig, notification.getBinaryExtra())
                OnlineConfigHelper.updateNormalConfigs(OnlineConfig.getInstance(sAppContext), normalConfig)
                return null
            } catch (e: TException) {
                return null
            }
        }
        if (NotificationType.CustomClientConfigUpdate.value.equals(notification.type, ignoreCase = true)) {
            val customConfig = XmPushActionCustomConfig()
            try {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(customConfig, notification.getBinaryExtra())
                OnlineConfigHelper.updateCustomConfigs(OnlineConfig.getInstance(sAppContext), customConfig)
                return null
            } catch (e: TException) {
                return null
            }
        }
        if (NotificationType.SyncInfoResult.value.equals(notification.type, ignoreCase = true)) {
            SyncInfoHelper.saveInfo(sAppContext, notification)
            return null
        }
        if (NotificationType.ForceSync.value.equals(notification.type, ignoreCase = true)) {
            MyLog.w("receive force sync notification")
            SyncInfoHelper.doSyncInfoAsync(sAppContext, false)
            return null
        }
        if (!NotificationType.CancelPushMessage.value.equals(notification.type)) {
            if (NotificationType.HybridRegisterResult.value.equals(notification.type)) {
                try {
                    val result = XmPushActionRegistrationResult()
                    XmPushThriftSerializeUtils.convertByteArrayToThriftObject(result, notification.getBinaryExtra())
                    MiPushClient4Hybrid.onReceiveRegisterResult(sAppContext, result)
                    return null
                } catch (e: TException) {
                    MyLog.e(e)
                    return null
                }
            }
            if (!NotificationType.HybridUnregisterResult.value.equals(notification.type)) {
                NotificationType.PushLogUpload.value.equals(notification.type)
                return null
            }
            try {
                val result = XmPushActionUnRegistrationResult()
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(result, notification.getBinaryExtra())
                MiPushClient4Hybrid.onReceiveUnregisterResult(sAppContext, result)
                return null
            } catch (e: TException) {
                MyLog.e(e)
                return null
            }
        }
        MyLog.persist("resp-type:" + notification.type + ", " + notification.id)
        val extra = notification.extra
        if (extra != null) {
            var notifyId = -2
            if (extra.containsKey(PushConstants.PUSH_NOTIFY_ID)) {
                val value = extra[PushConstants.PUSH_NOTIFY_ID]
                notifyId = if (TextUtils.isEmpty(value)) {
                    -2
                } else {
                    try {
                        value!!.toInt()
                    } catch (e: NumberFormatException) {
                        e.printStackTrace()
                        -2
                    }
                }
            }
            if (notifyId >= -1) {
                MiPushClient.clearNotification(sAppContext, notifyId)
            } else {
                MiPushClient.clearNotification(
                    sAppContext,
                    if (extra.containsKey(PushConstants.PUSH_TITLE)) extra[PushConstants.PUSH_TITLE] else "",
                    if (extra.containsKey(PushConstants.PUSH_DESCRIPTION)) extra[PushConstants.PUSH_DESCRIPTION] else ""
                )
            }
            return null
        }
        sendAckNotification(notification)
        return null
    }

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
        MyLog.v("ASSEMBLE_PUSH : $notification")
        val id = notification.id
        val extra = notification.extra
        if (extra != null) {
            val regInfo = extra[Constants.ASSEMBLE_PUSH_REG_INFO]
            if (TextUtils.isEmpty(regInfo)) {
                return
            }
            if (regInfo!!.contains("brand:" + PhoneBrand.FCM.name)) {
                MyLog.w("ASSEMBLE_PUSH : receive fcm token sync ack")
                AssemblePushHelper.saveAssemblePushTokenAfterAck(sAppContext, AssemblePush.ASSEMBLE_PUSH_FCM, regInfo)
                processSingleTokenACK(id, notification.errorCode, AssemblePush.ASSEMBLE_PUSH_FCM)
                return
            }
            if (regInfo.contains("brand:" + PhoneBrand.HUAWEI.name)) {
                MyLog.w("ASSEMBLE_PUSH : receive hw token sync ack")
                AssemblePushHelper.saveAssemblePushTokenAfterAck(sAppContext, AssemblePush.ASSEMBLE_PUSH_HUAWEI, regInfo)
                processSingleTokenACK(id, notification.errorCode, AssemblePush.ASSEMBLE_PUSH_HUAWEI)
                return
            }
            if (regInfo.contains("brand:" + PhoneBrand.OPPO.name)) {
                MyLog.w("ASSEMBLE_PUSH : receive COS token sync ack")
                AssemblePushHelper.saveAssemblePushTokenAfterAck(sAppContext, AssemblePush.ASSEMBLE_PUSH_COS, regInfo)
                processSingleTokenACK(id, notification.errorCode, AssemblePush.ASSEMBLE_PUSH_COS)
                return
            }
            if (regInfo.contains("brand:" + PhoneBrand.VIVO.name)) {
                MyLog.w("ASSEMBLE_PUSH : receive FTOS token sync ack")
                AssemblePushHelper.saveAssemblePushTokenAfterAck(sAppContext, AssemblePush.ASSEMBLE_PUSH_FTOS, regInfo)
                processSingleTokenACK(id, notification.errorCode, AssemblePush.ASSEMBLE_PUSH_FTOS)
            }
        }
    }

    private fun processSingleTokenACK(id: String?, errorCode: Long, assemblePush: AssemblePush) {
        val retryType = AssemblePushInfoHelper.getRetryType(assemblePush) ?: return
        if (errorCode == 0L) {
            synchronized(OperatePushHelper::class.java) {
                val helper = OperatePushHelper.getInstance(sAppContext)
                if (helper.isMessageOperating(id)) {
                    helper.removeOperateMessage(id)
                    if (OperatePushHelper.SYNCING == helper.getSyncStatus(retryType)) {
                        helper.putSyncStatus(retryType, OperatePushHelper.SYNCED)
                    }
                }
            }
            return
        }
        if (OperatePushHelper.SYNCING != OperatePushHelper.getInstance(sAppContext).getSyncStatus(retryType)) {
            OperatePushHelper.getInstance(sAppContext).removeOperateMessage(id)
            return
        }
        synchronized(OperatePushHelper::class.java) {
            val helper = OperatePushHelper.getInstance(sAppContext)
            if (helper.isMessageOperating(id)) {
                if (helper.getRetryCount(id) < 10) {
                    helper.increaseRetryCount(id)
                    PushServiceClient.getInstance(sAppContext).sendAssemblePushTokenCommon(id, retryType, assemblePush)
                } else {
                    helper.removeOperateMessage(id)
                }
            }
        }
    }

    private fun processStatDataACK(notification: XmPushActionAckNotification) {
        val id = notification.id
        MyLog.i("receive ack $id")
        val extra = notification.extra
        if (extra != null) {
            val realSource = extra[UploadDataHelper.REAL_SOURCE]
            if (TextUtils.isEmpty(realSource)) {
                return
            }
            MyLog.i("receive ack : messageId = $id  realSource = $realSource")
            PushStatClientManager.getInstance(sAppContext).onResult(id ?: "", realSource ?: "", notification.errorCode == 0L)
        }
    }

    private fun reportDecryptFail(container: XmPushActionContainer) {
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

    private fun sendAckNotification(notification: XmPushActionNotification) {
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

    private fun tryToReinitialize() {
        val sharedPreferences = sAppContext.getSharedPreferences("mipush_extra", 0)
        val currentTimeMillis = System.currentTimeMillis()
        if (kotlin.math.abs(currentTimeMillis - sharedPreferences.getLong(Constants.SP_KEY_LAST_REINITIALIZE, 0L)) > 1800000) {
            MiPushClient.reInitialize(sAppContext, RegistrationReason.PackageUnregistered)
            sharedPreferences.edit().putLong(Constants.SP_KEY_LAST_REINITIALIZE, currentTimeMillis).apply()
        }
    }

    fun getTimeForTimeZone(timeZone: TimeZone, targetTimeZone: TimeZone, list: List<String>): List<String> {
        if (timeZone == targetTimeZone) {
            return list
        }
        val rawOffset = ((timeZone.rawOffset - targetTimeZone.rawOffset) / 1000) / 60
        val startHour = list[0].split(":")[0].toLong()
        val start = ((((startHour * 60) + list[0].split(":")[1].toLong()) - rawOffset) + 1440) % 1440
        val end = ((((list[1].split(":")[0].toLong() * 60) + list[1].split(":")[1].toLong()) - rawOffset) + 1440) % 1440
        return arrayListOf(
            String.format(Locale.US, "%1$02d:%2$02d", start / 60, start % 60),
            String.format(Locale.US, "%1$02d:%2$02d", end / 60, end % 60)
        )
    }

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
                } catch (e: TException) {
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
            if (sInstance == null) {
                sInstance = PushMessageProcessor(context)
            }
            return sInstance!!
        }

        @JvmStatic
        fun getNotificationMessageIntent(context: Context, packageName: String, map: Map<String, String>?): Intent? {
            if (map == null || !map.containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT)) {
                return null
            }
            val notifyEffect = map[PushConstants.EXTRA_PARAM_NOTIFY_EFFECT]
            var intentFlags = -1
            val flags = map["intent_flag"]
            if (!TextUtils.isEmpty(flags)) {
                try {
                    intentFlags = flags!!.toInt()
                } catch (e: NumberFormatException) {
                    MyLog.e("Cause by intent_flag:" + e.message)
                }
            }
            var intent: Intent? = null
            if (PushConstants.NOTIFICATION_CLICK_DEFAULT == notifyEffect) {
                try {
                    intent = context.packageManager.getLaunchIntentForPackage(packageName)
                } catch (e: Exception) {
                    MyLog.e("Cause:" + e.message)
                }
            } else if (PushConstants.NOTIFICATION_CLICK_INTENT == notifyEffect) {
                if (map.containsKey("intent_uri")) {
                    val intentUri = map["intent_uri"]
                    if (intentUri != null) {
                        try {
                            intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
                            intent.setPackage(packageName)
                        } catch (e: URISyntaxException) {
                            MyLog.e("Cause:" + e.message)
                        }
                    }
                } else if (map.containsKey("class_name")) {
                    intent = Intent().apply {
                        component = ComponentName(packageName, map["class_name"]!!)
                    }
                }
            } else if (PushConstants.NOTIFICATION_CLICK_WEB_PAGE == notifyEffect) {
                val webUri = map["web_uri"]
                if (webUri != null) {
                    var url = webUri.trim()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "http://$url"
                    }
                    try {
                        val protocol = URL(url).protocol
                        if ("http" == protocol || "https" == protocol) {
                            intent = Intent("android.intent.action.VIEW").apply {
                                data = Uri.parse(url)
                            }
                        }
                    } catch (e: MalformedURLException) {
                        MyLog.e("Cause:" + e.message)
                    }
                }
            }
            if (intent == null) {
                return null
            }
            if (intentFlags >= 0) {
                intent.flags = intentFlags
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                val resolveActivity: ResolveInfo? = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                if (resolveActivity != null) {
                    return intent
                }
                MyLog.w("not resolve activity:$intent")
            } catch (e: Exception) {
                MyLog.e("Cause:" + e.message)
            }
            return null
        }

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
                mCachedMsgIds!!.remove(messageId)
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
