package com.xiaomi.channel.commonutils.android;

import android.content.Context;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/PermissionUtils.class */
public class PermissionUtils {
    public static final String readPhoneState = "android.permission.READ_PHONE_STATE";
    public static final String writeExternalStorage = "android.permission.WRITE_EXTERNAL_STORAGE";

    public static boolean checkSelfPermission(Context context, String str) {
        return context.getPackageManager().checkPermission(str, context.getPackageName()) == 0;
    }
}
