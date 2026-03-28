package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.PushConstants;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/FTOSPushHelper.class */
public class FTOSPushHelper {
    private static long mLastTime = 0;
    private static volatile boolean mNeedRegister = false;

    public static void doInNetworkChange(Context context) {
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        if (getNeedRegister()) {
            long j = mLastTime;
            if (j <= 0 || j + Constants.ASSEMBLE_PUSH_NETWORK_INTERVAL <= jElapsedRealtime) {
                mLastTime = jElapsedRealtime;
                registerFTOSAssemblePush(context);
            }
        }
    }

    public static boolean getNeedRegister() {
        return mNeedRegister;
    }

    public static boolean hasNetwork(Context context) {
        return AssemblePushHelper.hasNetwork(context);
    }

    public static void notifyFTOSNotificationClicked(Context context, Map<String, String> map) {
        PushMessageReceiver miPushReceiver;
        if (map == null || !map.containsKey("pushMsg")) {
            return;
        }
        String str = map.get("pushMsg");
        if (TextUtils.isEmpty(str) || (miPushReceiver = AssemblePushHelper.getMiPushReceiver(context)) == null) {
            return;
        }
        MiPushMessage miPushMessage = AssemblePushHelper.parseMiPushMessage(str);
        if (miPushMessage.getExtra().containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT)) {
            return;
        }
        miPushReceiver.onNotificationMessageClicked(context, miPushMessage);
    }

    private static void registerFTOSAssemblePush(Context context) {
        AbstractPushManager manager = AssemblePushCollectionsManager.getInstance(context).getManager(AssemblePush.ASSEMBLE_PUSH_FTOS);
        if (manager != null) {
            MyLog.w("ASSEMBLE_PUSH :  register fun touch os when network change!");
            manager.register();
        }
    }

    public static void setNeedRegister(boolean z) {
        mNeedRegister = z;
    }

    public static void uploadToken(Context context, String str) {
        AssemblePushHelper.uploadToken(context, AssemblePush.ASSEMBLE_PUSH_FTOS, str);
    }
}
