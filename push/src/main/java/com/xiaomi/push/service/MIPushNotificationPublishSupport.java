package com.xiaomi.push.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;
import android.widget.RemoteViews;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobConstants;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.push.service.clientReport.PushClientReportManager;
import com.xiaomi.push.service.clientReport.ReportConstants;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import java.util.LinkedList;
import java.util.Map;

final class MIPushNotificationPublishSupport {
    private static final String NOTIFICATION_TIMEOUT = "timeout";

    private MIPushNotificationPublishSupport() {
    }

    static MIPushNotificationHelper.NotifyPushMessageInfo notifyPushMessage(Context context, XmPushActionContainer xmPushActionContainer, byte[] bArr, LinkedList<Pair<Integer, XmPushActionContainer>> linkedList) {
        Notification notification;
        MIPushNotificationHelper.NotifyPushMessageInfo notifyPushMessageInfo = new MIPushNotificationHelper.NotifyPushMessageInfo();
        if (isBlockedByAppNotificationOp(context, xmPushActionContainer)) {
            return notifyPushMessageInfo;
        }
        PushMetaInfo metaInfo = xmPushActionContainer.getMetaInfo();
        RemoteViews notificationForCustomLayout = MIPushNotificationViewSupport.getNotificationForCustomLayout(context, xmPushActionContainer);
        String targetPackage = MIPushNotificationHelper.getTargetPackage(xmPushActionContainer);
        final int iHashCode = ((targetPackage.hashCode() / 10) * 10) + (metaInfo != null ? metaInfo.getNotifyId() : 0);
        PendingIntent clickedPendingIntent = MIPushNotificationActionSupport.getClickedPendingIntent(context, xmPushActionContainer, metaInfo, bArr, iHashCode);
        if (clickedPendingIntent == null) {
            if (metaInfo != null) {
                PushClientReportManager.getInstance(context.getApplicationContext()).reportEvent4NeedDrop(xmPushActionContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainer), metaInfo.getId(), "11");
            }
            MyLog.w("The click PendingIntent is null. ");
            return notifyPushMessageInfo;
        }
        if (Build.VERSION.SDK_INT >= 11) {
            MIPushNotificationHelper.GetNotificationResult notificationForLargeIcons = MIPushNotificationViewSupport.getNotificationForLargeIcons(context, xmPushActionContainer, notificationForCustomLayout, clickedPendingIntent, iHashCode);
            notifyPushMessageInfo.traffic = notificationForLargeIcons.trafficSize;
            notifyPushMessageInfo.targetPkgName = targetPackage;
            notification = notificationForLargeIcons.notification;
        } else {
            notification = MIPushNotificationPlatformSupport.buildLegacyNotification(context, xmPushActionContainer, metaInfo, notificationForCustomLayout, clickedPendingIntent);
        }
        MIPushNotificationPlatformSupport.applyMiuiExtras(context, xmPushActionContainer, metaInfo, notification);
        NotificationManagerHelper notificationManagerHelperFrom = NotificationManagerHelper.from(context, targetPackage);
        notificationManagerHelperFrom.notify(iHashCode, notification);
        handlePostNotify(context, xmPushActionContainer, metaInfo, notification, targetPackage, iHashCode, notificationManagerHelperFrom, linkedList);
        return notifyPushMessageInfo;
    }

    private static boolean isBlockedByAppNotificationOp(Context context, XmPushActionContainer xmPushActionContainer) {
        String targetPackage = MIPushNotificationHelper.getTargetPackage(xmPushActionContainer);
        AppInfoUtils.AppNotificationOp appNotificationOp = AppInfoUtils.getAppNotificationOp(context, targetPackage, true);
        if (MIUIUtils.isXMSF(context) && appNotificationOp == AppInfoUtils.AppNotificationOp.NOT_ALLOWED) {
            PushMetaInfo metaInfo = xmPushActionContainer.getMetaInfo();
            if (metaInfo != null) {
                PushClientReportManager.getInstance(context.getApplicationContext()).reportEvent4NeedDrop(xmPushActionContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainer), metaInfo.getId(), "10:" + targetPackage);
            }
            MyLog.w("Do not notify because user block " + targetPackage + "‘s notification");
            return true;
        }
        return false;
    }

    private static void handlePostNotify(Context context, XmPushActionContainer xmPushActionContainer, PushMetaInfo pushMetaInfo, Notification notification, String str, int i, NotificationManagerHelper notificationManagerHelper, LinkedList<Pair<Integer, XmPushActionContainer>> linkedList) {
        if (MIUIUtils.isMIUI() && MIUIUtils.isXMSF(context)) {
            NotificationGroupHelper.getInstance().onNotificationNotify(context, i, notification);
            if (Build.VERSION.SDK_INT >= 26 && notification != null && notification.extras.getBoolean(MIPushTopNotificationSupport.LOCAL_FLAG, false)) {
                MIPushTopNotificationSupport.scheduleTopNotificationUpdate(context, str, i, pushMetaInfo.getId(), notification);
            }
        }
        if (MIPushNotificationHelper.isBusinessMessage(xmPushActionContainer)) {
            PushClientReportManager.getInstance(context.getApplicationContext()).reportEvent(xmPushActionContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainer), pushMetaInfo.getId(), ReportConstants.AWAKE_TYPE_TRY_SHOW, null);
        }
        if (MIPushNotificationHelper.isNormalNotificationMessage(xmPushActionContainer)) {
            PushClientReportManager.getInstance(context.getApplicationContext()).reportEvent(xmPushActionContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainer), pushMetaInfo.getId(), 1002, null);
        }
        scheduleTimeoutIfNeeded(context, pushMetaInfo, i, notificationManagerHelper);
        MIPushNotificationCacheSupport.cacheNotification(new Pair<>(Integer.valueOf(i), xmPushActionContainer), linkedList);
    }

    private static void scheduleTimeoutIfNeeded(Context context, PushMetaInfo pushMetaInfo, final int i, final NotificationManagerHelper notificationManagerHelper) {
        if (Build.VERSION.SDK_INT >= 26) {
            return;
        }
        String id = pushMetaInfo != null ? pushMetaInfo.getId() : null;
        int timeout = getTimeout(pushMetaInfo != null ? pushMetaInfo.getExtra() : null);
        if (timeout <= 0 || TextUtils.isEmpty(id)) {
            return;
        }
        final String str = ScheduledJobConstants.NOTIFICATION_TIMEOUT_JOB_ID + id;
        ScheduledJobManager scheduledJobManager = ScheduledJobManager.getInstance(context);
        scheduledJobManager.cancelJob(str);
        scheduledJobManager.addOneShootJob(new ScheduledJobManager.Job() { // from class: com.xiaomi.push.service.MIPushNotificationPublishSupport.1
            @Override
            public String getJobId() {
                return str;
            }

            @Override
            public void run() {
                notificationManagerHelper.cancel(i);
            }
        }, timeout);
    }

    private static int getTimeout(Map<String, String> map) {
        String str = map == null ? null : map.get(NOTIFICATION_TIMEOUT);
        int i = 0;
        if (!TextUtils.isEmpty(str)) {
            try {
                i = Integer.parseInt(str);
            } catch (Exception unused) {
                i = 0;
            }
        }
        return i;
    }
}
