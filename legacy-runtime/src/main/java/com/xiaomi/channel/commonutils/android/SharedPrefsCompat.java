package com.xiaomi.channel.commonutils.android;

import android.content.SharedPreferences;
import android.os.Build;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/SharedPrefsCompat.class */
public final class SharedPrefsCompat {
    public static void apply(SharedPreferences.Editor editor) {
        if (Build.VERSION.SDK_INT > 8) {
            editor.apply();
        } else {
            editor.commit();
        }
    }
}
