package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/FCMPushHelper.class */
public class FCMPushHelper {
    public static void clearToken(Context context) {
        AssemblePushHelper.clearToken(context, AssemblePush.ASSEMBLE_PUSH_FCM);
    }

    public static void convertMessage(Intent intent) {
        AssemblePushHelper.convertMessage(intent);
    }

    public static boolean isFCMSwitchOpen(Context context) {
        return AssemblePushHelper.isOpenAssemblePushOnlineSwitch(context, AssemblePush.ASSEMBLE_PUSH_FCM) && MiPushClient.getOpenFCMPush(context);
    }

    public static void notifyFCMNotificationCome(Context context, Map<String, String> map) {
        PushMessageReceiver miPushReceiver;
        String str = map.get("pushMsg");
        if (TextUtils.isEmpty(str) || (miPushReceiver = AssemblePushHelper.getMiPushReceiver(context)) == null) {
            return;
        }
        miPushReceiver.onNotificationMessageArrived(context, AssemblePushHelper.parseMiPushMessage(str));
    }

    public static void notifyFCMPassThoughMessageCome(Context context, Map<String, String> map) {
        PushMessageReceiver miPushReceiver;
        String str = map.get("pushMsg");
        if (TextUtils.isEmpty(str) || (miPushReceiver = AssemblePushHelper.getMiPushReceiver(context)) == null) {
            return;
        }
        miPushReceiver.onReceivePassThroughMessage(context, AssemblePushHelper.parseMiPushMessage(str));
    }

    public static void reportFCMMessageDelete() {
        MiTinyDataClient.upload(AssemblePushHelper.getSPErrorKey(AssemblePush.ASSEMBLE_PUSH_FCM), "fcm", 1L, "some fcm messages was deleted ");
    }

    public static void uploadToken(Context context, String str) {
        AssemblePushHelper.uploadToken(context, AssemblePush.ASSEMBLE_PUSH_FCM, str);
    }
}
