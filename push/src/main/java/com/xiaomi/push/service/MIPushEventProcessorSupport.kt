package com.xiaomi.push.service

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.clientreport.PerfMessageHelper
import com.xiaomi.push.service.clientReport.PushClientReportManager
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.smack.util.TrafficUtils
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import org.apache.thrift.TBase
import org.apache.thrift.TException

internal object MIPushEventProcessorSupport {
    @JvmStatic
    @Throws(Throwable::class)
    fun postProcessMIPushMessage(service: XMPushService, targetPackage: String, payload: ByteArray, intent: Intent) {
        val container = MIPushEventProcessor.buildContainer(payload) ?: return
        val metaInfo = container.metaInfo
        if (payload.isNotEmpty()) {
            PerfMessageHelper.collectPerfData(container.packageName, service.applicationContext, null, container.action, payload.size)
        }
        if (MIPushMessageRoutingSupport.isMIUIOldAdsSDKMessage(container) &&
            MIPushMessageRoutingSupport.isMIUIPushSupported(service, targetPackage)
        ) {
            if (MIPushNotificationHelper.isNPBMessage(container)) {
                PushClientReportManager.getInstance(service.applicationContext)
                    .reportEvent4NeedDrop(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, "5")
            }
            MIPushAckDispatcher.sendMIUIOldAdsAckMessage(service, container)
            return
        }
        if (MIPushMessageRoutingSupport.isMIUIPushMessage(container) &&
            !MIPushMessageRoutingSupport.isMIUIPushSupported(service, targetPackage) &&
            !MIPushMessageRoutingSupport.predefinedNotification(container)
        ) {
            if (MIPushNotificationHelper.isNPBMessage(container)) {
                PushClientReportManager.getInstance(service.applicationContext)
                    .reportEvent4NeedDrop(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, "6")
            }
            MIPushAckDispatcher.sendMIUINewAdsAckMessage(service, container)
            return
        }
        if ((!MIPushNotificationHelper.isBusinessMessage(container) || !AppInfoUtils.isPkgInstalled(service, container.packageName)) &&
            !MIPushMessageRoutingSupport.isIntentAvailable(service, intent)
        ) {
            if (!AppInfoUtils.isPkgInstalled(service, container.packageName)) {
                if (MIPushNotificationHelper.isNPBMessage(container)) {
                    PushClientReportManager.getInstance(service.applicationContext)
                        .reportEvent4ERROR(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, "2")
                }
                MIPushAckDispatcher.sendAppNotInstallNotification(service, container)
                return
            }
            MyLog.w("receive a mipush message, we can see the app, but we can't see the receiver.")
            if (MIPushNotificationHelper.isNPBMessage(container)) {
                PushClientReportManager.getInstance(service.applicationContext)
                    .reportEvent4ERROR(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, "3")
            }
            return
        }
        if (ActionType.Registration == container.action) {
            val packageName = container.packageName
            service.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, 0)
                .edit()
                .putString(packageName, container.appid)
                .commit()
            MIPushAppInfo.getInstance(service).removeDisablePushPkg(packageName)
            MIPushAppInfo.getInstance(service).removeDisablePushPkgCache(packageName)
            PushClientReportManager.getInstance(service.applicationContext)
                .reportEvent(packageName, ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID, metaInfo.id, ReportConstants.REGISTER_TYPE_RECEIVE, null)
            if (!TextUtils.isEmpty(metaInfo.id)) {
                intent.putExtra("messageId", metaInfo.id)
                intent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, ReportConstants.REGISTER_TYPE)
            }
        }
        if (MIPushNotificationHelper.isNormalNotificationMessage(container)) {
            PushClientReportManager.getInstance(service.applicationContext)
                .reportEvent(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, 1001, System.currentTimeMillis(), null)
            if (!TextUtils.isEmpty(metaInfo.id)) {
                intent.putExtra("messageId", metaInfo.id)
                intent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, 1000)
            }
        }
        if (MIPushNotificationHelper.isPassThoughMessage(container)) {
            PushClientReportManager.getInstance(service.applicationContext)
                .reportEvent(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, 2001, System.currentTimeMillis(), null)
            if (!TextUtils.isEmpty(metaInfo.id)) {
                intent.putExtra("messageId", metaInfo.id)
                intent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, 2000)
            }
        }
        if (MIPushNotificationHelper.isBusinessMessage(container)) {
            PushClientReportManager.getInstance(service.applicationContext)
                .reportEvent(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, 3001, System.currentTimeMillis(), null)
            if (!TextUtils.isEmpty(metaInfo.id)) {
                intent.putExtra("messageId", metaInfo.id)
                intent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, ReportConstants.AWAKE_TYPE)
            }
        }
        if (metaInfo != null &&
            !TextUtils.isEmpty(metaInfo.title) &&
            !TextUtils.isEmpty(metaInfo.description) &&
            metaInfo.passThrough != 1 &&
            (MIPushNotificationHelper.isNotifyForeground(metaInfo.extra) || !MIPushNotificationHelper.isApplicationForeground(service, container.packageName))
        ) {
            val duplicateKey = metaInfo.extra?.get(PushConstants.EXTRA_JOB_KEY).takeUnless { it.isNullOrEmpty() } ?: metaInfo.id
            val duplicated = MiPushMessageDuplicate.isDuplicateMessage(service, container.packageName, duplicateKey)
            if (duplicated) {
                PushClientReportManager.getInstance(service.applicationContext)
                    .reportEvent4DUPMD(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, "1:$duplicateKey")
                MyLog.w("drop a duplicate message, key=$duplicateKey")
            } else {
                val notifyInfo = MIPushNotificationHelper.notifyPushMessage(service, container, payload)
                if (notifyInfo.traffic > 0 && !TextUtils.isEmpty(notifyInfo.targetPkgName)) {
                    TrafficUtils.distributionTraffic(
                        service,
                        notifyInfo.targetPkgName,
                        notifyInfo.traffic,
                        true,
                        false,
                        System.currentTimeMillis(),
                    )
                }
                if (!MIPushNotificationHelper.isBusinessMessage(container) && AppInfoUtils.isAppRunning(service.applicationContext, targetPackage)) {
                    val arrivedIntent = Intent(PushConstants.MIPUSH_ACTION_MESSAGE_ARRIVED).apply {
                        putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
                        `package` = container.packageName
                    }
                    try {
                        val receivers: List<ResolveInfo>? = service.packageManager.queryBroadcastReceivers(arrivedIntent, 0)
                        if (!receivers.isNullOrEmpty()) {
                            MyLog.w("broadcast message arrived.")
                            service.sendBroadcast(arrivedIntent, MIPushHelper.getReceiverPermission(container.packageName))
                        }
                    } catch (_: Exception) {
                        PushClientReportManager.getInstance(service.applicationContext)
                            .reportEvent4ERROR(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, "1")
                    }
                }
            }
            MIPushAckDispatcher.sendAckMessage(service, container)
            if (container.action == ActionType.UnRegistration && PushConstants.PUSH_SERVICE_PACKAGE_NAME != service.packageName) {
                service.stopSelf()
            }
            return
        }

        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME.contains(container.packageName) &&
            container.isEncryptAction &&
            metaInfo != null &&
            metaInfo.extra != null &&
            metaInfo.extra.containsKey("ab")
        ) {
            MIPushAckDispatcher.sendAckMessage(service, container)
            MyLog.v("receive abtest message. ack it.${metaInfo.id}")
            if (container.action == ActionType.UnRegistration && PushConstants.PUSH_SERVICE_PACKAGE_NAME != service.packageName) {
                service.stopSelf()
            }
            return
        }

        if (MIPushMessageRoutingSupport.shouldSendBroadcast(service, targetPackage, container, metaInfo)) {
            var shouldBroadcast = true
            if (ActionType.Notification == container.action) {
                var notificationBody: TBase<*, *>? = null
                var parsed = false
                try {
                    notificationBody = UnEncryptedPushContainerHelper.getResponseMessageBodyFromContainer(service, container)
                    if (notificationBody == null) {
                        MyLog.e("receiving an un-recognized notification message. ${container.action}")
                    } else {
                        parsed = true
                    }
                } catch (e: TException) {
                    MyLog.e("receive a message which action string is not valid. $e")
                }
                if (parsed && notificationBody is XmPushActionNotification) {
                    val notification = notificationBody
                    if (NotificationType.CancelPushMessage.value == notification.type && notification.extra != null) {
                        var notificationId = -2
                        val rawNotifyId = notification.extra[PushConstants.PUSH_NOTIFY_ID]
                        if (!TextUtils.isEmpty(rawNotifyId)) {
                            try {
                                notificationId = rawNotifyId?.toInt() ?: -2
                            } catch (e: NumberFormatException) {
                                MyLog.w("parse notifyId from STRING to INT failed: $e")
                            }
                        }
                        if (notificationId >= -1) {
                            MyLog.w("try to retract a message by notifyId=$notificationId")
                            MIPushNotificationHelper.clearNotification(service, container.packageName, notificationId)
                        } else {
                            val title = notification.extra[PushConstants.PUSH_TITLE]
                            val description = notification.extra[PushConstants.PUSH_DESCRIPTION]
                            MyLog.w("try to retract a message by title&description.")
                            MIPushNotificationHelper.clearNotification(service, container.packageName, title, description)
                        }
                        shouldBroadcast = false
                        MIPushAckDispatcher.sendClearPushMessageAck(service, container, notification)
                    }
                }
            }
            if (shouldBroadcast) {
                if (metaInfo != null && !TextUtils.isEmpty(metaInfo.id)) {
                    when {
                        MIPushNotificationHelper.isPassThoughMessage(container) -> {
                            PushClientReportManager.getInstance(service.applicationContext)
                                .reportEvent(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, ReportConstants.THROUGH_TYPE_SEND_RECEIVE_BROADCAST, null)
                        }
                        MIPushNotificationHelper.isBusinessMessage(container) -> {
                            PushClientReportManager.getInstance(service.applicationContext)
                                .reportEvent4NeedDrop(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, "7")
                        }
                        MIPushNotificationHelper.isNormalNotificationMessage(container) -> {
                            PushClientReportManager.getInstance(service.applicationContext)
                                .reportEvent4NeedDrop(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, "8")
                        }
                        MIPushNotificationHelper.isRegisterMessage(container) -> {
                            PushClientReportManager.getInstance(service.applicationContext)
                                .reportEvent(container.packageName, ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID, metaInfo.id, ReportConstants.REGISTER_TYPE_SEND_BROADCAST, null)
                        }
                    }
                }
                MyLog.w("broadcast passthrough message.")
                service.sendBroadcast(intent, MIPushHelper.getReceiverPermission(container.packageName))
            }
        } else {
            PushClientReportManager.getInstance(service.applicationContext)
                .reportEvent4NeedDrop(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, "9")
        }

        if (container.action == ActionType.UnRegistration && PushConstants.PUSH_SERVICE_PACKAGE_NAME != service.packageName) {
            service.stopSelf()
        }
    }

    @JvmStatic
    fun processMIPushMessage(service: XMPushService, payload: ByteArray, trafficBytes: Long) {
        val container = MIPushEventProcessor.buildContainer(payload) ?: return
        if (TextUtils.isEmpty(container.packageName)) {
            MyLog.w("receive a mipush message without package name")
            return
        }
        val now = System.currentTimeMillis()
        val intent = MIPushEventProcessor.buildIntent(payload, now) ?: return
        val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
        try {
            TrafficUtils.distributionTraffic(service, targetPackage, trafficBytes, true, true, System.currentTimeMillis())
        } catch (t: Throwable) {
            MyLog.e(t)
        }
        val metaInfo = container.metaInfo
        if (metaInfo != null && metaInfo.id != null) {
            MyLog.persist("receive a message. appid=${container.appid}, msgid= ${metaInfo.id}, action=${container.action}")
        }
        metaInfo?.putToExtra(PushConstants.MESSAGE_RECEIVE_TIME, now.toString())

        if (container.action == ActionType.SendMessage &&
            MIPushAppInfo.getInstance(service).isUnRegistered(container.packageName) &&
            !MIPushNotificationHelper.isBusinessMessage(container)
        ) {
            val messageId = metaInfo?.id ?: ""
            if (metaInfo != null && MIPushNotificationHelper.isNPBMessage(container)) {
                PushClientReportManager.getInstance(service.applicationContext)
                    .reportEvent4NeedDrop(container.packageName, MIPushNotificationHelper.getInterfaceId(container), messageId, "1")
            }
            MyLog.w("Drop a message for unregistered, msgid=$messageId")
            MIPushAckDispatcher.sendAppAbsentAck(service, container, container.packageName)
            return
        }

        if (container.action == ActionType.SendMessage &&
            MIPushAppInfo.getInstance(service).isPushDisabled4User(container.packageName) &&
            !MIPushNotificationHelper.isBusinessMessage(container)
        ) {
            val messageId = metaInfo?.id ?: ""
            if (metaInfo != null && MIPushNotificationHelper.isNPBMessage(container)) {
                PushClientReportManager.getInstance(service.applicationContext)
                    .reportEvent4NeedDrop(container.packageName, MIPushNotificationHelper.getInterfaceId(container), messageId, "2")
            }
            MyLog.w("Drop a message for push closed, msgid=$messageId")
            MIPushAckDispatcher.sendAppAbsentAck(service, container, container.packageName)
            return
        }

        if (container.action == ActionType.SendMessage &&
            service.packageName != PushConstants.PUSH_SERVICE_PACKAGE_NAME &&
            service.packageName != container.packageName
        ) {
            MyLog.w("Receive a message with wrong package name, expect ${service.packageName}, received ${container.packageName}")
            MIPushAckDispatcher.sendErrorAck(
                service,
                container,
                "unmatched_package",
                "package should be ${service.packageName}, but got ${container.packageName}",
            )
            if (metaInfo != null && MIPushNotificationHelper.isNPBMessage(container)) {
                PushClientReportManager.getInstance(service.applicationContext)
                    .reportEvent4NeedDrop(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, "3")
            }
            return
        }

        if (metaInfo?.extra?.containsKey("hide") == true && "true".equals(metaInfo.extra["hide"], ignoreCase = true)) {
            MIPushAckDispatcher.sendAckMessage(service, container)
            return
        }

        if (metaInfo?.extra?.containsKey(PushConstants.EXTRA_PARAM_MIID) == true) {
            val requiredMiid = metaInfo.extra[PushConstants.EXTRA_PARAM_MIID]
            val currentMiid = SystemUtils.getMIID(service.applicationContext)
            if (TextUtils.isEmpty(currentMiid) || !TextUtils.equals(requiredMiid, currentMiid)) {
                if (MIPushNotificationHelper.isNPBMessage(container)) {
                    PushClientReportManager.getInstance(service.applicationContext)
                        .reportEvent4NeedDrop(container.packageName, MIPushNotificationHelper.getInterfaceId(container), metaInfo.id, "4")
                }
                MyLog.w("$requiredMiid should be login, but got $currentMiid")
                MIPushAckDispatcher.sendErrorAck(
                    service,
                    container,
                    "miid already logout or anther already login",
                    "$requiredMiid should be login, but got $currentMiid",
                )
                return
            }
        }

        try {
            postProcessMIPushMessage(service, targetPackage, payload, intent)
        } catch (t: Throwable) {
            MyLog.e(t)
        }
    }
}
