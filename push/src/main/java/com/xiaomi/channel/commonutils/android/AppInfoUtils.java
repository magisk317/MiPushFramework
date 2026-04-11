package com.xiaomi.channel.commonutils.android;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.text.TextUtils;
import android.util.Base64;
import com.google.protobuf.micro.CodedOutputStreamMicro;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.push.service.MIPushAccount;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/AppInfoUtils.class */
public class AppInfoUtils {
    private static final String ANDROID_PERMISSION_PREF = "android.permission.";
    public static final int PATTERN = 100000;
    public static final String SEPARATE_ITEM = "#";
    private static final String TAG = "AppInfoUtils.";

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/AppInfoUtils$AppNotificationOp.class */
    public enum AppNotificationOp {
        UNKNOWN(0),
        ALLOWED(1),
        NOT_ALLOWED(2);

        public static final int MASK = 3;
        private final int value;

        AppNotificationOp(int i) {
            this.value = i;
        }

        public static AppNotificationOp findByValue(int i) {
            switch (i) {
                case 0:
                    return UNKNOWN;
                case 1:
                    return ALLOWED;
                case 2:
                    return NOT_ALLOWED;
                default:
                    return null;
            }
        }

        public int getValue() {
            return this.value;
        }
    }

    private AppInfoUtils() {
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

    private static Object invokeCheckOpNoThrow(Object appOpsService, int op, int uid, String packageName) {
        if (appOpsService == null) {
            return null;
        }
        try {
            Method method = appOpsService.getClass().getMethod("checkOpNoThrow", Integer.TYPE, Integer.TYPE, String.class);
            method.setAccessible(true);
            return method.invoke(appOpsService, Integer.valueOf(op), Integer.valueOf(uid), packageName);
        } catch (Throwable th) {
            MyLog.w("checkOpNoThrow invoke error service=" + appOpsService.getClass().getName() + " " + th);
            return null;
        }
    }

    private static AppNotificationOp areNotificationsEnabled(Context context, ApplicationInfo applicationInfo) {
        Boolean boolValueOf;
        int i = Build.VERSION.SDK_INT;
        if (applicationInfo == null || i < 24) {
            return AppNotificationOp.UNKNOWN;
        }
        try {
            if (applicationInfo.packageName.equals(context.getPackageName())) {
                boolValueOf = Boolean.valueOf(((NotificationManager) context.getSystemService("notification")).areNotificationsEnabled());
            } else {
                Object objCallMethod = i >= 29 ? JavaCalls.callMethod(context.getSystemService("notification"), "getService", new Object[0]) : context.getSystemService(MIPushAccount.PREF_KEY_SECURITY);
                boolValueOf = null;
                if (objCallMethod != null) {
                    boolValueOf = (Boolean) JavaCalls.callMethodOrThrow(objCallMethod, "areNotificationsEnabledForPackage", applicationInfo.packageName, Integer.valueOf(applicationInfo.uid));
                }
            }
            if (boolValueOf != null) {
                return boolValueOf.booleanValue() ? AppNotificationOp.ALLOWED : AppNotificationOp.NOT_ALLOWED;
            }
        } catch (Exception e) {
            MyLog.w("are notifications enabled error " + e);
        }
        return AppNotificationOp.UNKNOWN;
    }

    public static boolean checkSelfPermission(Context context, String str) {
        return context.getPackageManager().checkPermission(str, context.getPackageName()) == 0;
    }

    public static String convertPermissionString(String[] strArr) {
        int i;
        boolean z;
        AppPermissionType[] appPermissionTypeArrValues = AppPermissionType.values();
        byte[] bArr = new byte[(int) Math.ceil(((double) appPermissionTypeArrValues.length) / 8.0d)];
        int i2 = -1;
        if (strArr == null) {
            MyLog.v("AppInfoUtils.: no permissions");
            return "";
        }
        int length = strArr.length;
        int i3 = 0;
        while (i3 < length) {
            String str = strArr[i3];
            int i4 = i2;
            if (!TextUtils.isEmpty(str)) {
                if (str.startsWith(ANDROID_PERMISSION_PREF)) {
                    int i5 = 0;
                    while (true) {
                        i = i2;
                        z = false;
                        if (i5 >= appPermissionTypeArrValues.length) {
                            break;
                        }
                        if (TextUtils.equals(ANDROID_PERMISSION_PREF + appPermissionTypeArrValues[i5].name(), str)) {
                            z = true;
                            i = i5;
                            break;
                        }
                        i5++;
                    }
                    i4 = i;
                    if (z) {
                        i4 = i;
                        if (i != -1) {
                            bArr[i / 8] = (byte) (bArr[i / 8] | (1 << (7 - (i % 8))));
                            i4 = i;
                        }
                    }
                } else {
                    i4 = i2;
                }
            }
            i3++;
            i2 = i4;
        }
        return new String(Base64.encode(bArr, 0));
    }

    public static Drawable getAppIconDrawable(Context context, String str) {
        ApplicationInfo applicationInfo = getApplicationInfo(context, str);
        Drawable drawableLoadLogo = null;
        Drawable drawable = null;
        if (applicationInfo != null) {
            try {
                Drawable drawableLoadIcon = applicationInfo.loadIcon(context.getPackageManager());
                drawableLoadLogo = drawableLoadIcon;
                if (drawableLoadIcon == null) {
                    drawableLoadLogo = drawableLoadIcon;
                    if (Build.VERSION.SDK_INT >= 9) {
                        drawable = drawableLoadIcon;
                        drawableLoadLogo = applicationInfo.loadLogo(context.getPackageManager());
                    }
                }
            } catch (Exception e) {
                MyLog.w("get app icon drawable failed, " + e);
                drawableLoadLogo = drawable;
            }
        }
        if (drawableLoadLogo == null) {
            drawableLoadLogo = new ColorDrawable(0);
        }
        return drawableLoadLogo;
    }

    public static int getAppIconId(Context context, String str) {
        ApplicationInfo applicationInfo = getApplicationInfo(context, str);
        int i = 0;
        if (applicationInfo != null) {
            int i2 = applicationInfo.icon;
            i = i2;
            if (i2 == 0) {
                i = i2;
                if (Build.VERSION.SDK_INT >= 9) {
                    i = applicationInfo.logo;
                }
            }
        }
        return i;
    }

    public static String getAppLabel(Context context, String str) {
        String string;
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(str, 0);
            string = str;
            if (packageInfo != null) {
                ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                string = str;
                if (applicationInfo != null) {
                    string = packageManager.getApplicationLabel(applicationInfo).toString();
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            MyLog.e(e);
            string = str;
        }
        return string;
    }

    public static AppNotificationOp getAppNotificationOp(Context context, String str, boolean z) {
        ApplicationInfo applicationInfo = null;
        AppNotificationOp appNotificationOpAreNotificationsEnabled = AppNotificationOp.UNKNOWN;
        if (context == null || TextUtils.isEmpty(str) || Build.VERSION.SDK_INT < 19) {
            return AppNotificationOp.UNKNOWN;
        }
        try {
            applicationInfo = str.equals(context.getPackageName()) ? context.getApplicationInfo() : context.getPackageManager().getApplicationInfo(str, 0);
            appNotificationOpAreNotificationsEnabled = areNotificationsEnabled(context, applicationInfo);
        } catch (Throwable th) {
            MyLog.w("get app op error " + th);
        }
        if (appNotificationOpAreNotificationsEnabled != AppNotificationOp.UNKNOWN) {
            return appNotificationOpAreNotificationsEnabled;
        }
        if (applicationInfo == null) {
            return AppNotificationOp.UNKNOWN;
        }
        Integer num = coerceInteger(JavaCalls.getStaticField((Class<? extends Object>) AppOpsManager.class, "OP_POST_NOTIFICATION"));
        if (num == null) {
            return AppNotificationOp.UNKNOWN;
        }
        Integer num2 = coerceInteger(
            invokeCheckOpNoThrow(
                context.getSystemService("appops"),
                num.intValue(),
                applicationInfo.uid,
                str
            )
        );
        Integer num3 = coerceInteger(JavaCalls.getStaticField((Class<? extends Object>) AppOpsManager.class, "MODE_ALLOWED"));
        Integer num4 = coerceInteger(JavaCalls.getStaticField((Class<? extends Object>) AppOpsManager.class, "MODE_IGNORED"));
        MyLog.i(String.format("get app mode %s|%s|%s", num2, num3, num4));
        Integer num5 = num3;
        if (num3 == null) {
            num5 = 0;
        }
        Integer num6 = num4;
        if (num4 == null) {
            num6 = 1;
        }
        if (num2 != null) {
            if (z) {
                return !num2.equals(num6) ? AppNotificationOp.ALLOWED : AppNotificationOp.NOT_ALLOWED;
            }
            return num2.equals(num5) ? AppNotificationOp.ALLOWED : AppNotificationOp.NOT_ALLOWED;
        }
        return AppNotificationOp.UNKNOWN;
    }

    public static String getAppPermissionBase64Str(Context context, String str) {
        try {
            return convertPermissionString(context.getPackageManager().getPackageInfo(str, CodedOutputStreamMicro.DEFAULT_BUFFER_SIZE).requestedPermissions);
        } catch (PackageManager.NameNotFoundException e) {
            MyLog.e(e.toString());
            return "";
        }
    }

    private static ApplicationInfo getApplicationInfo(Context context, String str) {
        ApplicationInfo applicationInfo;
        if (str.equals(context.getPackageName())) {
            applicationInfo = context.getApplicationInfo();
        } else {
            try {
                applicationInfo = context.getPackageManager().getApplicationInfo(str, 0);
            } catch (PackageManager.NameNotFoundException e) {
                MyLog.w("not found app info " + str);
                applicationInfo = null;
            }
        }
        return applicationInfo;
    }

    public static int getArchiveVersionCode(Context context, String str) {
        try {
            PackageInfo packageArchiveInfo = context.getPackageManager().getPackageArchiveInfo(str, 1);
            if (packageArchiveInfo == null) {
                return 0;
            }
            return (int) packageArchiveInfo.getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    public static String getForegroundApp(Context context) {
        return null;
    }

    public static String getProcessName(Context context) {
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses;
        if (context == null || (runningAppProcesses = ((ActivityManager) context.getSystemService("activity")).getRunningAppProcesses()) == null) {
            return null;
        }
        int iMyPid = Process.myPid();
        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : runningAppProcesses) {
            if (runningAppProcessInfo.pid == iMyPid) {
                return runningAppProcessInfo.processName;
            }
        }
        return null;
    }

    public static String getRunningAppPkgNames(Context context) {
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = ((ActivityManager) context.getSystemService("activity")).getRunningAppProcesses();
        ArrayList<String> arrayList = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        if (runningAppProcesses != null && runningAppProcesses.size() > 0) {
            Iterator<ActivityManager.RunningAppProcessInfo> it = runningAppProcesses.iterator();
            while (it.hasNext()) {
                String[] strArr = it.next().pkgList;
                for (int i = 0; strArr != null && i < strArr.length; i++) {
                    if (!arrayList.contains(strArr[i])) {
                        arrayList.add(strArr[i]);
                        if (arrayList.size() == 1) {
                            sb.append(((String) arrayList.get(0)).hashCode() % PATTERN);
                        } else {
                            sb.append("#");
                            sb.append(strArr[i].hashCode() % PATTERN);
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    public static Signature[] getSignature(Context context, String str) {
        try {
            PackageInfo packageArchiveInfo = context.getPackageManager().getPackageArchiveInfo(str, PackageManager.GET_SIGNING_CERTIFICATES);
            if (packageArchiveInfo == null || packageArchiveInfo.signingInfo == null) {
                return null;
            }
            return packageArchiveInfo.signingInfo.hasMultipleSigners()
                ? packageArchiveInfo.signingInfo.getApkContentsSigners()
                : packageArchiveInfo.signingInfo.getSigningCertificateHistory();
        } catch (Exception e) {
            return null;
        }
    }

    public static int getVersionCode(Context context, String str) {
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(str, 16384);
        } catch (Exception e) {
            MyLog.e(e);
            packageInfo = null;
        }
        if (packageInfo == null) {
            return 0;
        }
        return (int) packageInfo.getLongVersionCode();
    }

    public static String getVersionName(Context context, String str) {
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(str, 16384);
        } catch (Exception e) {
            MyLog.e(e);
            packageInfo = null;
        }
        return packageInfo != null ? packageInfo.versionName : "1.0";
    }

    public static boolean isAppMainProc(Context context) {
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = ((ActivityManager) context.getSystemService("activity")).getRunningAppProcesses();
        if (runningAppProcesses == null || runningAppProcesses.size() < 1) {
            return false;
        }
        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : runningAppProcesses) {
            if (runningAppProcessInfo.pid == Process.myPid() && runningAppProcessInfo.processName.equals(context.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAppRunning(Context context, String str) {
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = ((ActivityManager) context.getSystemService("activity")).getRunningAppProcesses();
        if (runningAppProcesses == null) {
            return false;
        }
        Iterator<ActivityManager.RunningAppProcessInfo> it = runningAppProcesses.iterator();
        while (it.hasNext()) {
            if (Arrays.asList(it.next().pkgList).contains(str)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isForeground(Context context) {
        return TextUtils.equals(context.getPackageName(), getForegroundApp(context));
    }

    public static boolean isPkgInstalled(Context context, String str) {
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(str, 0);
        } catch (PackageManager.NameNotFoundException e) {
            packageInfo = null;
        }
        return packageInfo != null;
    }
}
