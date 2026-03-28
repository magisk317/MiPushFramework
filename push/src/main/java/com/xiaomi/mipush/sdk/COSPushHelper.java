package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import com.xiaomi.channel.commonutils.logger.MyLog;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/COSPushHelper.class */
public class COSPushHelper {
    private static volatile boolean mNeedRegister = false;
    private static long mLastTime = 0;

    public static void convertMessage(Intent intent) {
        AssemblePushHelper.convertMessage(intent);
    }

    public static void doInNetworkChange(Context context) {
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        if (getNeedRegister()) {
            long j = mLastTime;
            if (j <= 0 || j + Constants.ASSEMBLE_PUSH_NETWORK_INTERVAL <= jElapsedRealtime) {
                mLastTime = jElapsedRealtime;
                registerCOSAssemblePush(context);
            }
        }
    }

    public static boolean getNeedRegister() {
        return mNeedRegister;
    }

    public static boolean hasNetwork(Context context) {
        return AssemblePushHelper.hasNetwork(context);
    }

    public static void onNotificationMessageCome(Context context, String str) {
    }

    public static void onPassThoughMessageCome(Context context, String str) {
    }

    public static void registerCOSAssemblePush(Context context) {
        AbstractPushManager manager = AssemblePushCollectionsManager.getInstance(context).getManager(AssemblePush.ASSEMBLE_PUSH_COS);
        if (manager != null) {
            MyLog.w("ASSEMBLE_PUSH :  register cos when network change!");
            manager.register();
        }
    }

    public static void setNeedRegister(boolean z) {
        synchronized (COSPushHelper.class) {
            try {
                mNeedRegister = z;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public static void uploadToken(Context context, String str) {
        AssemblePushHelper.uploadToken(context, AssemblePush.ASSEMBLE_PUSH_COS, str);
    }
}
