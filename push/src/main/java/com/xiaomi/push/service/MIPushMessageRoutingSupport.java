package com.xiaomi.push.service;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.smack.XMPPException;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MIPushMessageRoutingSupport {
    private MIPushMessageRoutingSupport() {
    }

    static boolean isIntentAvailable(Context context, Intent intent) {
        try {
            List<ResolveInfo> listQueryBroadcastReceivers = context.getPackageManager().queryBroadcastReceivers(intent, 32);
            return listQueryBroadcastReceivers != null && !listQueryBroadcastReceivers.isEmpty();
        } catch (Exception unused) {
            return true;
        }
    }

    static boolean isMIUIOldAdsSDKMessage(XmPushActionContainer xmPushActionContainer) {
        if (xmPushActionContainer.getMetaInfo() == null || xmPushActionContainer.getMetaInfo().getExtra() == null) {
            return false;
        }
        return "1".equals(xmPushActionContainer.getMetaInfo().getExtra().get("obslete_ads_message"));
    }

    static boolean isMIUIPushMessage(XmPushActionContainer xmPushActionContainer) {
        return PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(xmPushActionContainer.packageName) && xmPushActionContainer.getMetaInfo() != null && xmPushActionContainer.getMetaInfo().getExtra() != null && xmPushActionContainer.getMetaInfo().getExtra().containsKey(MIPushNotificationHelper.MIUI_PACKAGE_NAME);
    }

    static boolean isMIUIPushSupported(Context context, String str) {
        Intent intent = new Intent("com.xiaomi.mipush.miui.CLICK_MESSAGE");
        intent.setPackage(str);
        Intent intent2 = new Intent("com.xiaomi.mipush.miui.RECEIVE_MESSAGE");
        intent2.setPackage(str);
        try {
            android.content.pm.PackageManager packageManager = context.getPackageManager();
            List<ResolveInfo> listQueryBroadcastReceivers = packageManager.queryBroadcastReceivers(intent2, 32);
            List<ResolveInfo> listQueryIntentServices = packageManager.queryIntentServices(intent, 32);
            return !listQueryBroadcastReceivers.isEmpty() || !listQueryIntentServices.isEmpty();
        } catch (Exception e) {
            MyLog.e(e);
            return false;
        }
    }

    static boolean predefinedNotification(XmPushActionContainer xmPushActionContainer) {
        Map<String, String> extra = xmPushActionContainer.getMetaInfo().getExtra();
        return extra != null && extra.containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT);
    }

    static boolean shouldSendBroadcast(XMPushService xMPushService, String str, XmPushActionContainer xmPushActionContainer, PushMetaInfo pushMetaInfo) {
        boolean z = true;
        if (pushMetaInfo != null && pushMetaInfo.getExtra() != null && pushMetaInfo.getExtra().containsKey(PushConstants.EXTRA_PARAM_CHECK_ALIVE) && pushMetaInfo.getExtra().containsKey(PushConstants.EXTRA_PARAM_AWAKE)) {
            XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
            xmPushActionNotification.setAppId(xmPushActionContainer.getAppid());
            xmPushActionNotification.setPackageName(str);
            xmPushActionNotification.setType(NotificationType.AwakeSystemApp.value);
            xmPushActionNotification.setId(pushMetaInfo.getId());
            xmPushActionNotification.extra = new HashMap();
            boolean zIsAppRunning = AppInfoUtils.isAppRunning(xMPushService.getApplicationContext(), str);
            xmPushActionNotification.extra.put(PushConstants.EXTRA_PARAM_APP_RUNNING, Boolean.toString(zIsAppRunning));
            if (!zIsAppRunning) {
                boolean z2 = Boolean.parseBoolean(pushMetaInfo.getExtra().get(PushConstants.EXTRA_PARAM_AWAKE));
                xmPushActionNotification.extra.put(PushConstants.EXTRA_PARAM_AWAKED, Boolean.toString(z2));
                z = z2;
            }
            try {
                MIPushHelper.sendPacket(xMPushService, MIPushHelper.generateRequestContainer(xmPushActionContainer.getPackageName(), xmPushActionContainer.getAppid(), xmPushActionNotification, ActionType.Notification));
            } catch (XMPPException e) {
                MyLog.e(e);
            }
        }
        return z;
    }
}
