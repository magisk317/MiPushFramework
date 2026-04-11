package com.xiaomi.push.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.text.TextUtils
import android.util.Pair
import android.widget.RemoteViews
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobConstants
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.push.service.clientReport.PushClientReportManager
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import java.util.LinkedList

/**
 * Legacy MIUI notification publish chain retained for rollback/reference only.
 *
 * Display notifications no longer flow through this implementation by default; the active entry
 * now routes to [MyMIPushNotificationHelper] from
 * [MIPushNotificationHelper.notifyPushMessage].
 */
object MIPushNotificationPublishSupport {
    private const val NOTIFICATION_TIMEOUT = "timeout"

    @JvmStatic
    fun notifyPushMessage(
        context: Context,
        container: XmPushActionContainer,
        payload: ByteArray,
        cachedNotifications: LinkedList<Pair<Int, XmPushActionContainer>>,
    ): MIPushNotificationHelper.NotifyPushMessageInfo {
        val notifyInfo = MIPushNotificationHelper.NotifyPushMessageInfo()
        if (isBlockedByAppNotificationOp(context, container)) {
            return notifyInfo
        }
        val metaInfo = container.metaInfo
        val customLayout = MIPushNotificationViewSupport.getNotificationForCustomLayout(context, container)
        val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
        val notificationId = (targetPackage.hashCode() / 10) * 10 + (metaInfo?.notifyId ?: 0)
        val clickedPendingIntent = MIPushNotificationActionSupport.getClickedPendingIntent(
            context,
            container,
            metaInfo,
            payload,
            notificationId,
        )
        if (clickedPendingIntent == null) {
            metaInfo?.let {
                PushClientReportManager.getInstance(context.applicationContext)
                    .reportEvent4NeedDrop(
                        container.packageName,
                        MIPushNotificationHelper.getInterfaceId(container),
                        it.id,
                        "11",
                    )
            }
            MyLog.w("The click PendingIntent is null. ")
            return notifyInfo
        }

        val notification: Notification
        if (Build.VERSION.SDK_INT >= 11) {
            val result = MIPushNotificationViewSupport.getNotificationForLargeIcons(
                context,
                container,
                customLayout,
                clickedPendingIntent,
                notificationId,
            )
            notifyInfo.traffic = result.trafficSize
            notifyInfo.targetPkgName = targetPackage
            notification = result.notification ?: return notifyInfo
        } else {
            notification = MIPushNotificationPlatformSupport.buildLegacyNotification(
                context,
                container,
                metaInfo,
                customLayout,
                clickedPendingIntent,
            )
        }

        MIPushNotificationPlatformSupport.applyMiuiExtras(context, container, metaInfo, notification)
        val notificationManager = NotificationManagerHelper.from(context, targetPackage)
        notificationManager.notify(notificationId, notification)
        handlePostNotify(
            context,
            container,
            metaInfo,
            notification,
            targetPackage,
            notificationId,
            notificationManager,
            cachedNotifications,
        )
        return notifyInfo
    }

    private fun isBlockedByAppNotificationOp(context: Context, container: XmPushActionContainer): Boolean {
        val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
        val appNotificationOp = AppInfoUtils.getAppNotificationOp(context, targetPackage, true)
        if (MIUIUtils.isXMSF(context) && appNotificationOp == AppInfoUtils.AppNotificationOp.NOT_ALLOWED) {
            container.metaInfo?.let {
                PushClientReportManager.getInstance(context.applicationContext)
                    .reportEvent4NeedDrop(
                        container.packageName,
                        MIPushNotificationHelper.getInterfaceId(container),
                        it.id,
                        "10:$targetPackage",
                    )
            }
            MyLog.w("Do not notify because user block ${targetPackage}‘s notification")
            return true
        }
        return false
    }

    private fun handlePostNotify(
        context: Context,
        container: XmPushActionContainer,
        pushMetaInfo: PushMetaInfo,
        notification: Notification,
        packageName: String,
        notificationId: Int,
        notificationManager: NotificationManagerHelper,
        cachedNotifications: LinkedList<Pair<Int, XmPushActionContainer>>,
    ) {
        if (MIUIUtils.isMIUI() && MIUIUtils.isXMSF(context)) {
            NotificationGroupHelper.getInstance().onNotificationNotify(context, notificationId, notification)
            if (Build.VERSION.SDK_INT >= 26 && notification.extras.getBoolean(MIPushTopNotificationSupport.LOCAL_FLAG, false)) {
                MIPushTopNotificationSupport.scheduleTopNotificationUpdate(
                    context,
                    packageName,
                    notificationId,
                    pushMetaInfo.id,
                    notification,
                )
            }
        }
        if (MIPushNotificationHelper.isBusinessMessage(container)) {
            PushClientReportManager.getInstance(context.applicationContext)
                .reportEvent(
                    container.packageName,
                    MIPushNotificationHelper.getInterfaceId(container),
                    pushMetaInfo.id,
                    ReportConstants.AWAKE_TYPE_TRY_SHOW,
                    null,
                )
        }
        if (MIPushNotificationHelper.isNormalNotificationMessage(container)) {
            PushClientReportManager.getInstance(context.applicationContext)
                .reportEvent(
                    container.packageName,
                    MIPushNotificationHelper.getInterfaceId(container),
                    pushMetaInfo.id,
                    1002,
                    null,
                )
        }
        scheduleTimeoutIfNeeded(context, pushMetaInfo, notificationId, notificationManager)
        MIPushNotificationCacheSupport.cacheNotification(Pair(notificationId, container), cachedNotifications)
    }

    private fun scheduleTimeoutIfNeeded(
        context: Context,
        pushMetaInfo: PushMetaInfo,
        notificationId: Int,
        notificationManager: NotificationManagerHelper,
    ) {
        if (Build.VERSION.SDK_INT >= 26) {
            return
        }
        val messageId = pushMetaInfo.id
        val timeout = getTimeout(pushMetaInfo.extra)
        if (timeout <= 0 || TextUtils.isEmpty(messageId)) {
            return
        }
        val jobId = ScheduledJobConstants.NOTIFICATION_TIMEOUT_JOB_ID + messageId
        val scheduledJobManager = ScheduledJobManager.getInstance(context)
        scheduledJobManager.cancelJob(jobId)
        scheduledJobManager.addOneShootJob(
            object : ScheduledJobManager.Job() {
                override fun getJobId(): String = jobId

                override fun run() {
                    notificationManager.cancel(notificationId)
                }
            },
            timeout,
        )
    }

    private fun getTimeout(extra: Map<String, String>?): Int {
        val timeout = extra?.get(NOTIFICATION_TIMEOUT)
        return if (TextUtils.isEmpty(timeout)) {
            0
        } else {
            timeout?.toIntOrNull() ?: 0
        }
    }
}
