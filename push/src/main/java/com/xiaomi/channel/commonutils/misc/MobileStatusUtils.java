package com.xiaomi.channel.commonutils.misc;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import com.xiaomi.channel.commonutils.logger.MyLog;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/MobileStatusUtils.class */
public class MobileStatusUtils {
    private MobileStatusUtils() {
    }

    public static boolean isCharging(Context context) {
        Intent intentRegisterReceiver;
        try {
            intentRegisterReceiver = context.registerReceiver(null, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
        } catch (Exception e) {
            intentRegisterReceiver = null;
        }
        boolean z = false;
        if (intentRegisterReceiver == null) {
            return false;
        }
        int intExtra = intentRegisterReceiver.getIntExtra("status", -1);
        if (intExtra == 2 || intExtra == 5) {
            z = true;
        }
        return z;
    }

    public static boolean isScreenLocked(Context context) {
        try {
            KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService("keyguard");
            return keyguardManager != null && keyguardManager.isKeyguardLocked();
        } catch (Exception e) {
            MyLog.e(e);
            return false;
        }
    }
}
