package com.xiaomi.channel.commonutils.android;

import com.xiaomi.channel.commonutils.logger.MyLog;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/SystemProperties.class */
public class SystemProperties {
    public static String get(String str) {
        return get(str, "");
    }

    public static String get(String str, String str2) {
        try {
            return (String) SystemUtils.loadClass(null, "android.os.SystemProperties").getMethod("get", String.class, String.class).invoke(null, str, str2);
        } catch (Exception e) {
            MyLog.w("SystemProperties.get: " + e);
            return str2;
        }
    }
}
