package com.xiaomi.mipush.sdk

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.mipush.sdk.PushMessageHandler.PushMessageInterface
import com.xiaomi.mipush.sdk.stat.PushStatClientManager
import com.xiaomi.mipush.sdk.stat.upload.UploadDataHelper
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.push.service.OnlineConfigHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.awake.AwakeUploadHelper
import com.xiaomi.xmpush.thrift.ConfigKey
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.RegistrationReason
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionCustomConfig
import com.xiaomi.xmpush.thrift.XmPushActionNormalConfig
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TException

/** Context-bound synchronous handlers for notification and notification-ACK actions. */
internal class PushMessageProcessorNotificationActionSupport(
    context: Context,
    private val ackSupport: PushMessageProcessorAckSupport,
    @Suppress("UNUSED_PARAMETER") private val eventEmitter: PushMessageProcessorEventEmitter,
) {
    private val sAppContext: Context = context.applicationContext ?: context

    fun processAckNotification(notification: XmPushActionAckNotification): PushMessageInterface? {
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

    fun processEnableDisableAck(
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

    fun processNotificationMessage(
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
            } catch (_: TException) {
                return null
            }
        }
        if (NotificationType.CustomClientConfigUpdate.value.equals(notification.type, ignoreCase = true)) {
            val customConfig = XmPushActionCustomConfig()
            try {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(customConfig, notification.getBinaryExtra())
                OnlineConfigHelper.updateCustomConfigs(OnlineConfig.getInstance(sAppContext), customConfig)
                return null
            } catch (_: TException) {
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
        ackSupport.sendAckNotification(notification)
        return null
    }

    fun processSendTokenAckNotification(notification: XmPushActionAckNotification) {
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

    fun processSingleTokenACK(id: String?, errorCode: Long, assemblePush: AssemblePush) {
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

    fun processStatDataACK(notification: XmPushActionAckNotification) {
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
}
