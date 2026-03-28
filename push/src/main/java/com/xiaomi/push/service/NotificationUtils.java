package com.xiaomi.push.service;

import android.app.Notification;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/NotificationUtils.class */
public class NotificationUtils {
    private static final String FIELD_extraNotification = "extraNotification";
    private static final String METHOD_getTargetPkg = "getTargetPkg";
    private static final String METHOD_setTargetPkg = "setTargetPkg";
    private static final String SETTINGS_USER_AGGREGATE = "user_aggregate";
    private static final String SETTINGS_USER_FOLD = "user_fold";
    private static final int VALUE_DISABLE_DEFAULT = -2;
    private static final int VALUE_DISABLE_NOT_SUPPORT = -3;
    private static final int VALUE_DISABLE_USER = -1;
    private static final int VALUE_ENABLE_DEFAULT = 2;
    private static final int VALUE_ENABLE_USER = 1;
    private static final int VALUE_INVALID = 0;
    private static final String[] XIAOMI_BROWSER_PKG_ARRAY = {PushConstants.XIAOMI_GLOBALBROWSER_PACKAGE_NAME, PushConstants.XIAOMI_BROWSER_PACKAGE_NAME};
    private static String sBestBrowserPkg = null;

    static int getIdForSmallIconFromTargetPkg(Context context, String str) {
        return AppInfoUtils.getAppIconId(context, str);
    }

    public static String getTargetPackage(Notification notification) {
        String string = null;
        String str = null;
        try {
            if (Build.VERSION.SDK_INT >= 19) {
                string = null;
                if (notification.extras != null) {
                    string = notification.extras.getString(MIPushNotificationHelper.NOTIFICATION_EXTRA_TARGET_PACKAGE_STRING);
                }
            }
            String str2 = string;
            if (TextUtils.isEmpty(string)) {
                String str3 = string;
                Object field = JavaCalls.getField(notification, FIELD_extraNotification);
                str2 = string;
                if (field != null) {
                    str = string;
                    str2 = (String) JavaCalls.callMethod(field, METHOD_getTargetPkg, new Object[0]);
                }
            }
            str = str2;
        } catch (Exception e) {
        }
        return str;
    }

    public static int getUserAggregate(ContentResolver contentResolver) {
        if (Build.VERSION.SDK_INT < 17) {
            return 0;
        }
        try {
            return Settings.Global.getInt(contentResolver, SETTINGS_USER_AGGREGATE, 0);
        } catch (Exception e) {
            MyLog.w("get user aggregate failed, " + e);
            return 0;
        }
    }

    public static int getUserFold(ContentResolver contentResolver) {
        if (Build.VERSION.SDK_INT < 17) {
            return 0;
        }
        try {
            return Settings.Global.getInt(contentResolver, SETTINGS_USER_FOLD, 0);
        } catch (Exception e) {
            MyLog.w("get user fold failed, " + e);
            return 0;
        }
    }

    static boolean isNotificationFromXmsf(Context context, StatusBarNotification statusBarNotification) {
        boolean z = false;
        if (!MIUIUtils.isXMSF(context) || Build.VERSION.SDK_INT < 18 || statusBarNotification == null) {
            return false;
        }
        if (MIUIUtils.isXMSF(statusBarNotification.getPackageName()) || MIUIUtils.isXMSF(String.valueOf(JavaCalls.callMethod(statusBarNotification, "getOpPkg", new Object[0])))) {
            z = true;
        }
        return z;
    }

    public static boolean isUserAggregate(ContentResolver contentResolver) {
        int userAggregate = getUserAggregate(contentResolver);
        boolean z = true;
        if (userAggregate != 1) {
            z = userAggregate == 2;
        }
        return z;
    }

    public static boolean isUserFold(ContentResolver contentResolver) {
        int userFold = getUserFold(contentResolver);
        boolean z = true;
        if (userFold != 1) {
            z = userFold == 2;
        }
        return z;
    }

    static void setTargetPackage(Notification notification, String str) {
        try {
            if (Build.VERSION.SDK_INT >= 19 && notification.extras != null) {
                notification.extras.putString(MIPushNotificationHelper.NOTIFICATION_EXTRA_TARGET_PACKAGE_STRING, str);
            }
            Object field = JavaCalls.getField(notification, FIELD_extraNotification);
            if (field != null) {
                JavaCalls.callMethod(field, METHOD_setTargetPkg, str);
            }
        } catch (Exception e) {
        }
    }

    static void setXiaomiBrowserAsDefault(Context context, android.content.Intent intent) {
        String str = null;
        int i = -1;
        while (true) {
            String str2 = i < 0 ? sBestBrowserPkg : XIAOMI_BROWSER_PKG_ARRAY[i];
            if (!TextUtils.isEmpty(str2)) {
                intent.setPackage(str2);
                try {
                    if (context.getPackageManager().resolveActivity(intent, 65536) != null) {
                        str = str2;
                        break;
                    }
                } catch (Exception e) {
                    MyLog.w("not found xm browser:" + e);
                }
            }
            int i2 = i + 1;
            i = i2;
            if (i2 >= XIAOMI_BROWSER_PKG_ARRAY.length) {
                break;
            }
        }
        intent.setPackage(str);
        sBestBrowserPkg = str;
    }
}
