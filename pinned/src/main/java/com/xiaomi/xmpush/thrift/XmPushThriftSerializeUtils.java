package com.xiaomi.xmpush.thrift;

import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import java.lang.reflect.Method;
import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.XmPushTBinaryProtocol;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushThriftSerializeUtils.class */
public class XmPushThriftSerializeUtils {
    public static final int MASK_CHARGING = 4;
    public static final int MASK_GEO_PASS = 1;
    public static final int MASK_GEO_RECEIVE = 4;
    public static final int MASK_GEO_SHOW = 2;
    public static final int MASK_SCREEN_LOCKED = 8;
    public static final int MASK_TYPE_SHIELD = 16;
    private static final int NOTIFICATION_STATUS_UNKNOWN = 0;
    private static final int NOTIFICATION_STATUS_ALLOWED = 1;
    private static final int NOTIFICATION_STATUS_BLOCKED = 2;
    private static final String TAG = "XmPushThriftSerializeUtils";

    public static void convertByteArrayToThriftObject(TBase<?, ?> t, byte[] bArr) throws TException {
        if (bArr == null) {
            throw new TException("the message byte is empty.");
        }
        new TDeserializer(new XmPushTBinaryProtocol.Factory(true, true, bArr.length)).deserialize(t, bArr);
    }

    public static byte[] convertThriftObjectToBytes(TBase<?, ?> t) {
        if (t == null) {
            return null;
        }
        try {
            return new TSerializer(new TBinaryProtocol.Factory()).serialize(t);
        } catch (TException e) {
            Log.e(TAG, "convertThriftObjectToBytes catch TException.", e);
            return null;
        }
    }

    public static short getDeviceStatus(Context context, XmPushActionContainer xmPushActionContainer) {
        int deviceStatus = getNotificationStatus(context, xmPushActionContainer == null ? null : xmPushActionContainer.packageName);
        if (isCharging(context)) {
            deviceStatus += MASK_CHARGING;
        }
        if (isScreenLocked(context)) {
            deviceStatus += MASK_SCREEN_LOCKED;
        }
        return (short) deviceStatus;
    }

    public static short getGeoMsgStatus(boolean z, boolean z2, boolean z3) {
        int i = 0;
        int i2 = z ? 4 : 0;
        if (z2) {
            i = 2;
        }
        return (short) (0 + i2 + i + (z3 ? 1 : 0));
    }

    private static Integer coerceInteger(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        return null;
    }

    private static ApplicationInfo getApplicationInfo(Context context, String packageName) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            return packageName.equals(context.getPackageName())
                ? context.getApplicationInfo()
                : context.getPackageManager().getApplicationInfo(packageName, 0);
        } catch (Throwable th) {
            return null;
        }
    }

    private static int getNotificationStatus(Context context, String packageName) {
        if (context == null || TextUtils.isEmpty(packageName) || Build.VERSION.SDK_INT < 19) {
            return NOTIFICATION_STATUS_UNKNOWN;
        }
        ApplicationInfo applicationInfo = getApplicationInfo(context, packageName);
        if (applicationInfo == null) {
            return NOTIFICATION_STATUS_UNKNOWN;
        }
        if (packageName.equals(context.getPackageName()) && Build.VERSION.SDK_INT >= 24) {
            try {
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager != null) {
                    return notificationManager.areNotificationsEnabled() ? NOTIFICATION_STATUS_ALLOWED : NOTIFICATION_STATUS_BLOCKED;
                }
            } catch (Throwable th) {
                Log.w(TAG, "Failed to query notifications for current package", th);
            }
        }
        Integer mode = queryNotificationMode(context, applicationInfo.uid, packageName);
        if (mode == null) {
            return NOTIFICATION_STATUS_UNKNOWN;
        }
        Integer allowedMode = readStaticInt(AppOpsManager.class, "MODE_ALLOWED", Integer.valueOf(0));
        return mode.equals(allowedMode) ? NOTIFICATION_STATUS_ALLOWED : NOTIFICATION_STATUS_BLOCKED;
    }

    private static boolean isCharging(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Intent batteryChanged = context.registerReceiver(null, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
            if (batteryChanged == null) {
                return false;
            }
            int status = batteryChanged.getIntExtra("status", -1);
            return status == 2 || status == 5;
        } catch (Throwable th) {
            return false;
        }
    }

    private static boolean isScreenLocked(Context context) {
        if (context == null) {
            return false;
        }
        try {
            KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            return keyguardManager != null && keyguardManager.isKeyguardLocked();
        } catch (Throwable th) {
            Log.w(TAG, "Failed to query keyguard state", th);
            return false;
        }
    }

    private static Integer queryNotificationMode(Context context, int uid, String packageName) {
        Object appOps = context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return null;
        }
        Integer opPostNotification = readStaticInt(AppOpsManager.class, "OP_POST_NOTIFICATION", null);
        if (opPostNotification == null) {
            return null;
        }
        try {
            Method method = appOps.getClass().getMethod("checkOpNoThrow", Integer.TYPE, Integer.TYPE, String.class);
            method.setAccessible(true);
            return coerceInteger(method.invoke(appOps, opPostNotification, Integer.valueOf(uid), packageName));
        } catch (Throwable th) {
            Log.w(TAG, "Failed to query app ops notification mode", th);
            return null;
        }
    }

    private static Integer readStaticInt(Class<?> type, String fieldName, Integer fallback) {
        try {
            Object value = type.getField(fieldName).get(null);
            Integer coerced = coerceInteger(value);
            return coerced != null ? coerced : fallback;
        } catch (Throwable th) {
            return fallback;
        }
    }
}
