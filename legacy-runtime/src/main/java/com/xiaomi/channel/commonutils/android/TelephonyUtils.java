package com.xiaomi.channel.commonutils.android;

import android.content.Context;
import android.telephony.TelephonyManager;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/TelephonyUtils.class */
public class TelephonyUtils {
    public static boolean isChinaMobile(Context context) {
        String simOperator = ((TelephonyManager) context.getSystemService("phone")).getSimOperator();
        return "46000".equals(simOperator) || "46002".equals(simOperator) || "46007".equals(simOperator);
    }

    public static boolean isChinaTelecom(Context context) {
        return "46003".equals(((TelephonyManager) context.getSystemService("phone")).getSimOperator());
    }

    public static boolean isChinaUnicom(Context context) {
        return "46001".equals(((TelephonyManager) context.getSystemService("phone")).getSimOperator());
    }
}
