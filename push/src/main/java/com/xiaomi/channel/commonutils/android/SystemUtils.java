package com.xiaomi.channel.commonutils.android;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.slim.Blob;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/SystemUtils.class */
public class SystemUtils {
    private static String cachedMOSVersion = null;
    private static Context sContext;

    private static String getCOSVersion() {
        String str = SystemProperties.get("ro.build.version.opporom", "");
        if (!TextUtils.isEmpty(str) && !str.startsWith("ColorOS_")) {
            cachedMOSVersion = "ColorOS_" + str;
        }
        return cachedMOSVersion;
    }

    public static Context getContext() {
        return sContext;
    }

    private static String getEMUIVersion() {
        String str = SystemProperties.get("ro.build.version.emui", "");
        cachedMOSVersion = str;
        return str;
    }

    private static String getFOSVersion() {
        String str = SystemProperties.get("ro.vivo.os.version", "");
        if (!TextUtils.isEmpty(str) && !str.startsWith("FuntouchOS_")) {
            cachedMOSVersion = "FuntouchOS_" + str;
        }
        return cachedMOSVersion;
    }

    public static String getMIID(Context context) {
        if (MIUIUtils.isNotMIUI()) {
            return "";
        }
        String str = (String) JavaCalls.callStaticMethod("com.xiaomi.xmsf.helper.MIIDAccountHelper", "getMIID", context);
        return TextUtils.isEmpty(str) ? Blob.CLIENT_PING_ID : str;
    }

    public static int getMIUIType() {
        try {
            Class<?> clsLoadClass = loadClass(null, "miui.os.Build");
            if (clsLoadClass.getField("IS_STABLE_VERSION").getBoolean(null)) {
                return 3;
            }
            return clsLoadClass.getField("IS_DEVELOPMENT_VERSION").getBoolean(null) ? 2 : 1;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String getManufacturerOSVersion() {
        synchronized (SystemUtils.class) {
            try {
                String str = cachedMOSVersion;
                if (str != null) {
                    return str;
                }
                String str2 = Build.VERSION.INCREMENTAL;
                String eMUIVersion = str2;
                if (getMIUIType() <= 0) {
                    eMUIVersion = getEMUIVersion();
                    if (TextUtils.isEmpty(eMUIVersion)) {
                        eMUIVersion = getCOSVersion();
                        if (TextUtils.isEmpty(eMUIVersion)) {
                            eMUIVersion = getFOSVersion();
                            if (TextUtils.isEmpty(eMUIVersion)) {
                                eMUIVersion = String.valueOf(SystemProperties.get("ro.product.brand", "Android") + "_" + str2);
                            }
                        }
                    }
                }
                cachedMOSVersion = eMUIVersion;
                return eMUIVersion;
            } finally {
            }
        }
    }

    public static void initialize(Context context) {
        sContext = context.getApplicationContext();
    }

    public static boolean isBootCompleted() {
        return TextUtils.equals((String) JavaCalls.callStaticMethod("android.os.SystemProperties", "get", "sys.boot_completed"), "1");
    }

    public static boolean isDebuggable(Context context) {
        boolean z = false;
        try {
            if ((context.getApplicationInfo().flags & 2) != 0) {
                z = true;
            }
            return z;
        } catch (Exception e) {
            MyLog.e(e);
            return false;
        }
    }

    public static boolean isGlobalVersion() {
        try {
            return loadClass(null, "miui.os.Build").getField("IS_GLOBAL_BUILD").getBoolean(false);
        } catch (ClassNotFoundException e) {
            MyLog.e("miui.os.Build ClassNotFound");
            return false;
        } catch (Exception e2) {
            MyLog.e(e2);
            return false;
        }
    }

    public static Class<?> loadClass(Context context, String str) throws ClassNotFoundException {
        if (str == null || str.trim().length() == 0) {
            throw new ClassNotFoundException("class is empty");
        }
        boolean z = context != null;
        if (z && Build.VERSION.SDK_INT >= 29) {
            try {
                return context.getClassLoader().loadClass(str);
            } catch (ClassNotFoundException e) {
            }
        }
        try {
            return Class.forName(str);
        } catch (ClassNotFoundException e2) {
            MyLog.w(String.format("loadClass fail hasContext= %s, errMsg = %s", Boolean.valueOf(z), e2.getLocalizedMessage()));
            throw new ClassNotFoundException("loadClass fail ", e2);
        }
    }
}
