package com.xiaomi.push.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import com.xiaomi.channel.commonutils.android.DeviceInfo;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.xmpush.thrift.ConfigKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

final class NotificationManagerPlatformSupport {
    private static final String XMSF_FAKE_CONDITION_PROVIDER_PATH = "xmsf_fake_condition_provider_path";
    private static Context appContext;
    private static boolean supportFwk;
    private static Object nms;

    private NotificationManagerPlatformSupport() {
    }

    static void init(Context context) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
            NotificationManager nm = getNm();
            Boolean bool = (Boolean) JavaCalls.callMethod(nm, "isSystemConditionProviderEnabled", XMSF_FAKE_CONDITION_PROVIDER_PATH);
            NotificationManagerHelper.outLog("fwk is support.init:" + bool);
            boolean booleanValue = bool != null ? bool.booleanValue() : false;
            supportFwk = booleanValue;
            if (booleanValue) {
                nms = JavaCalls.callMethod(nm, "getService", new Object[0]);
            }
        }
    }

    static boolean isRomSupportNotificationBelongToApp(Context context) {
        init(context);
        return isSupportFwk();
    }

    static boolean isSupportFwk() {
        return MIUIUtils.isMIUI() && OnlineConfig.getInstance(appContext).getBooleanValue(ConfigKey.NotificationBelongToAppSwitch.getValue(), true) && supportFwk;
    }

    static NotificationManager getNm() {
        return (NotificationManager) appContext.getSystemService("notification");
    }

    static int getPkgUid(String str) {
        if (Build.VERSION.SDK_INT < 24) {
            return -1;
        }
        try {
            return appContext.getPackageManager().getPackageUid(str, 0);
        } catch (Exception e) {
            return -1;
        }
    }

    static List<?> getListFromParceledListSlice(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            Object invoke = obj.getClass().getMethod("getList", new Class[0]).invoke(obj, new Object[0]);
            return invoke instanceof List ? (List<?>) invoke : null;
        } catch (Exception e) {
            return null;
        }
    }

    static Object newParceledListSlice(List<?> list) throws Exception {
        return Class.forName("android.content.pm.ParceledListSlice").getConstructor(List.class).newInstance(list);
    }

    static void cancel(String str, int i) throws Exception {
        JavaCalls.callMethodOrThrow(nms, "cancelNotificationWithTag", str, null, Integer.valueOf(i), Integer.valueOf(DeviceInfo.getSpaceId()));
    }

    static void createNotificationChannel(String str, NotificationChannel notificationChannel) throws Exception {
        int pkgUid = getPkgUid(str);
        if (pkgUid != -1) {
            JavaCalls.callMethodOrThrow(nms, "createNotificationChannelsForPackage", str, Integer.valueOf(pkgUid), newParceledListSlice(Arrays.asList(notificationChannel)));
        }
    }

    static void createNotificationChannelGroup(String str, NotificationChannelGroup notificationChannelGroup) throws Exception {
        int pkgUid = getPkgUid(str);
        if (pkgUid != -1) {
            JavaCalls.callMethodOrThrow(nms, "updateNotificationChannelGroupForPackage", str, Integer.valueOf(pkgUid), notificationChannelGroup);
        }
    }

    static List<StatusBarNotification> getActiveNotifications(String str) throws Exception {
        int spaceId = DeviceInfo.getSpaceId();
        if (spaceId == -1) {
            return null;
        }
        List<?> listFromParceledListSlice = getListFromParceledListSlice(JavaCalls.callMethod(nms, "getAppActiveNotifications", str, Integer.valueOf(spaceId)));
        if (listFromParceledListSlice == null) {
            return null;
        }
        List<StatusBarNotification> result = new ArrayList<>(listFromParceledListSlice.size());
        for (Object item : listFromParceledListSlice) {
            result.add((StatusBarNotification) item);
        }
        return result;
    }

    static NotificationChannelGroup getNotificationChannelGroup(String str, String str2) throws Exception {
        int pkgUid = getPkgUid(str2);
        if (pkgUid == -1) {
            return null;
        }
        return (NotificationChannelGroup) JavaCalls.callMethod(nms, "getNotificationChannelGroupForPackage", str, str2, Integer.valueOf(pkgUid));
    }

    static List<NotificationChannel> getNotificationChannels(String str) throws Exception {
        int pkgUid = getPkgUid(str);
        if (pkgUid == -1) {
            return null;
        }
        List<?> listFromParceledListSlice = getListFromParceledListSlice(JavaCalls.callMethod(nms, "getNotificationChannelsForPackage", str, Integer.valueOf(pkgUid), Boolean.FALSE));
        if (listFromParceledListSlice == null) {
            return null;
        }
        List<NotificationChannel> result = new ArrayList<>(listFromParceledListSlice.size());
        for (Object item : listFromParceledListSlice) {
            result.add((NotificationChannel) item);
        }
        return result;
    }

    static List<StatusBarNotification> filterLocalActiveNotifications(String str, StatusBarNotification[] statusBarNotificationArr) {
        boolean isMIUI = MIUIUtils.isMIUI();
        List<StatusBarNotification> arrayList = new ArrayList<>();
        if (statusBarNotificationArr == null || statusBarNotificationArr.length <= 0) {
            return arrayList;
        }
        for (StatusBarNotification statusBarNotification : statusBarNotificationArr) {
            if (!isMIUI || str.equals(NotificationUtils.getTargetPackage(statusBarNotification.getNotification()))) {
                arrayList.add(statusBarNotification);
            }
        }
        return arrayList;
    }

    static List<NotificationChannel> filterMipushChannels(String str, String str2, List<NotificationChannel> list) {
        if (!MIUIUtils.isMIUI() || list == null) {
            return list;
        }
        List<NotificationChannel> arrayList = new ArrayList<>();
        String format = String.format(str2, str, "");
        Iterator<NotificationChannel> it = list.iterator();
        while (it.hasNext()) {
            NotificationChannel next = it.next();
            if (next.getId().startsWith(format)) {
                arrayList.add(next);
            }
        }
        return arrayList;
    }

    static void notify(String str, int i, Notification notification) {
        NotificationManager nm = getNm();
        if (Build.VERSION.SDK_INT >= 19) {
            notification.extras.putString("xmsf_target_package", str);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            nm.notifyAsPackage(str, null, i, notification);
        } else {
            nm.notify(i, notification);
        }
    }
}
