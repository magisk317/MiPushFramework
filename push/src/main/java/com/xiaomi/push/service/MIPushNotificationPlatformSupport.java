package com.xiaomi.push.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.widget.RemoteViews;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.push.service.clientReport.PushClientReportManager;
import com.xiaomi.push.service.clientReport.ReportConstants;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

final class MIPushNotificationPlatformSupport {
    private static final String EXTRA_MESSAGE_ID = "message_id";
    private static final String EXTRA_SHOW_AT_TAIL = "miui.showAtTail";
    private static final String EXTRA_SCORE_INFO = "score_info";

    private MIPushNotificationPlatformSupport() {
    }

    @SuppressWarnings("deprecation")
    static Notification buildLegacyNotification(Context context, XmPushActionContainer xmPushActionContainer, PushMetaInfo pushMetaInfo, RemoteViews remoteViews, PendingIntent pendingIntent) {
        Notification notification = new Notification(MIPushNotificationViewSupport.getIdForSmallIcon(context, MIPushNotificationHelper.getTargetPackage(xmPushActionContainer)), null, System.currentTimeMillis());
        String[] strArrDetermineTitleAndDespByDIP = MIPushNotificationViewSupport.determineTitleAndDespByDIP(context, pushMetaInfo);
        try {
            notification.getClass().getMethod("setLatestEventInfo", Context.class, CharSequence.class, CharSequence.class, PendingIntent.class).invoke(notification, context, strArrDetermineTitleAndDespByDIP[0], strArrDetermineTitleAndDespByDIP[1], pendingIntent);
        } catch (InvocationTargetException e) {
            reportLegacyBuildError(context, xmPushActionContainer, pushMetaInfo, "7");
            MyLog.e("meet invocation target error. " + e);
        } catch (IllegalAccessException e2) {
            reportLegacyBuildError(context, xmPushActionContainer, pushMetaInfo, "5");
            MyLog.e("meet illegal access error. " + e2);
        } catch (IllegalArgumentException e3) {
            reportLegacyBuildError(context, xmPushActionContainer, pushMetaInfo, "6");
            MyLog.e("meet illegal argument error. " + e3);
        } catch (NoSuchMethodException e4) {
            reportLegacyBuildError(context, xmPushActionContainer, pushMetaInfo, "4");
            MyLog.e("meet no such method error. " + e4);
        }
        Map<String, String> extra = pushMetaInfo.getExtra();
        if (extra != null && extra.containsKey(MIPushNotificationHelper.NOTIFICATION_TICKER)) {
            notification.tickerText = extra.get(MIPushNotificationHelper.NOTIFICATION_TICKER);
        }
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - MIPushNotificationHelper.lastNotify > MIPushNotificationHelper.NOTIFY_INTERVAL) {
            MIPushNotificationHelper.lastNotify = currentTimeMillis;
            int i = pushMetaInfo.notifyType;
            String targetPackage = MIPushNotificationHelper.getTargetPackage(xmPushActionContainer);
            if (MIPushNotificationHelper.hasLocalNotifyType(context, targetPackage)) {
                i = MIPushNotificationHelper.getLocalNotifyType(context, targetPackage);
            }
            notification.defaults = i;
            if (extra != null && (i & 1) != 0) {
                String str = extra.get(MIPushNotificationHelper.NOTIFICATION_SOUND_URI);
                if (!TextUtils.isEmpty(str) && str.startsWith(MIPushNotificationHelper.ANDROID_RESOURCE + targetPackage)) {
                    notification.defaults = i ^ 1;
                    notification.sound = android.net.Uri.parse(str);
                }
            }
        }
        notification.flags |= 16;
        if (remoteViews != null) {
            notification.contentView = remoteViews;
        }
        return notification;
    }

    static void applyMiuiExtras(Context context, XmPushActionContainer xmPushActionContainer, PushMetaInfo pushMetaInfo, Notification notification) {
        if (MIUIUtils.isMIUI() && Build.VERSION.SDK_INT >= 19) {
            if (!TextUtils.isEmpty(pushMetaInfo.getId())) {
                notification.extras.putString(EXTRA_MESSAGE_ID, pushMetaInfo.getId());
            }
            String str = pushMetaInfo.getInternal() == null ? null : pushMetaInfo.getInternal().get(EXTRA_SCORE_INFO);
            if (!TextUtils.isEmpty(str)) {
                notification.extras.putString(EXTRA_SCORE_INFO, str);
            }
            int i = -1;
            if (MIPushNotificationHelper.isNormalNotificationMessage(xmPushActionContainer)) {
                i = 1000;
            } else if (MIPushNotificationHelper.isBusinessMessage(xmPushActionContainer)) {
                i = 3000;
            }
            notification.extras.putString(ReportConstants.EVENT_MESSAGE_TYPE, String.valueOf(i));
            notification.extras.putString(MIPushNotificationHelper.NOTIFICATION_EXTRA_TARGET_PACKAGE_STRING, MIPushNotificationHelper.getTargetPackage(xmPushActionContainer));
        }
        String str2 = pushMetaInfo.getExtra() == null ? null : pushMetaInfo.getExtra().get("message_count");
        if (MIUIUtils.isMIUI() && str2 != null) {
            try {
                setMessageCount(notification, Integer.parseInt(str2));
            } catch (NumberFormatException e) {
                reportLegacyBuildError(context, xmPushActionContainer, pushMetaInfo, "8");
                MyLog.e("fail to set message count. " + e);
            }
        }
        String str3 = pushMetaInfo.getExtra() == null ? null : pushMetaInfo.getExtra().get(EXTRA_SHOW_AT_TAIL);
        if (Build.VERSION.SDK_INT >= 19 && MIUIUtils.isMIUI()) {
            notification.extras.putBoolean(EXTRA_SHOW_AT_TAIL, "true".equals(str3));
        }
        if (!MIUIUtils.isXMS() && MIUIUtils.isXMSF(context)) {
            setTargetPackage(notification, MIPushNotificationHelper.getTargetPackage(xmPushActionContainer));
        }
    }

    private static void reportLegacyBuildError(Context context, XmPushActionContainer xmPushActionContainer, PushMetaInfo pushMetaInfo, String str) {
        if (pushMetaInfo != null) {
            PushClientReportManager.getInstance(context.getApplicationContext()).reportEvent4ERROR(xmPushActionContainer.getPackageName(), MIPushNotificationHelper.getInterfaceId(xmPushActionContainer), pushMetaInfo.getId(), str);
        }
    }

    private static void setMessageCount(Notification notification, int i) {
        Object field = JavaCalls.getField(notification, "extraNotification");
        if (field != null) {
            JavaCalls.callMethod(field, "setMessageCount", Integer.valueOf(i));
        }
    }

    private static Notification setTargetPackage(Notification notification, String str) {
        try {
            Field declaredField = Notification.class.getDeclaredField("extraNotification");
            declaredField.setAccessible(true);
            Object obj = declaredField.get(notification);
            Method declaredMethod = obj.getClass().getDeclaredMethod("setTargetPkg", CharSequence.class);
            declaredMethod.setAccessible(true);
            declaredMethod.invoke(obj, str);
        } catch (Exception e) {
            MyLog.e(e);
        }
        return notification;
    }
}
