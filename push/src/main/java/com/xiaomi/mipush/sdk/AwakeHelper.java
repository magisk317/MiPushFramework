package com.xiaomi.mipush.sdk;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.push.service.PacketHelper;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.awake.AwakeUploadHelper;
import com.xiaomi.push.service.awake.module.AwakeManager;
import com.xiaomi.push.service.awake.module.HelpType;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.ConfigKey;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.util.HashMap;
import org.apache.thrift.TBase;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/AwakeHelper.class */
public class AwakeHelper {
    public static final int PING = 9999;

    public static void doAWork(final Context context, Intent intent, Uri uri) {
        if (context == null) {
            return;
        }
        PushServiceClient.getInstance(context).awakePushService();
        if (AwakeManager.getInstance(context.getApplicationContext()).getSendDataIml() == null) {
            AwakeManager.getInstance(context.getApplicationContext()).setPackageInfo(AppInfoHolder.getInstance(context.getApplicationContext()).getAppID(), context.getPackageName(), OnlineConfig.getInstance(context.getApplicationContext()).getIntValue(ConfigKey.AwakeInfoUploadWaySwitch.getValue(), 0), new AppLayerProcessDataIml());
            OnlineConfig.getInstance(context).addOCUpdateCallbacks(new OnlineConfig.OCUpdateCallback(102, "awake online config") { // from class: com.xiaomi.mipush.sdk.AwakeHelper.2
                @Override // com.xiaomi.push.service.OnlineConfig.OCUpdateCallback
                protected void onCallback() {
                    AwakeManager.getInstance(context).setOnLineCmd(OnlineConfig.getInstance(context).getIntValue(ConfigKey.AwakeInfoUploadWaySwitch.getValue(), 0));
                }
            });
        }
        if ((context instanceof Activity) && intent != null) {
            AwakeManager.getInstance(context.getApplicationContext()).sendResult(HelpType.ACTIVITY, context, intent, null);
            return;
        }
        if (!(context instanceof Service) || intent == null) {
            if (uri == null || TextUtils.isEmpty(uri.toString())) {
                return;
            }
            AwakeManager.getInstance(context.getApplicationContext()).sendResult(HelpType.PROVIDER, context, null, uri.toString());
            return;
        }
        if (PushConstants.ACTION_WAKEUP.equals(intent.getAction())) {
            AwakeManager.getInstance(context.getApplicationContext()).sendResult(HelpType.SERVICE_COMPONENT, context, intent, null);
        } else {
            AwakeManager.getInstance(context.getApplicationContext()).sendResult(HelpType.SERVICE_ACTION, context, intent, null);
        }
    }

    public static void doAwAppLogic(Context context, String str, int i, String str2) {
        XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
        xmPushActionNotification.setAppId(str);
        xmPushActionNotification.setExtra(new HashMap<>());
        xmPushActionNotification.getExtra().put(PushConstants.EXTRA_AWAKE_APP_ONLINE_CMD, String.valueOf(i));
        xmPushActionNotification.getExtra().put(PushConstants.EXTRA_AWAKE_APP_AWAKE_INFO, str2);
        xmPushActionNotification.setId(PacketHelper.generatePacketID());
        byte[] bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(xmPushActionNotification);
        if (bArrConvertThriftObjectToBytes == null) {
            MyLog.w("send message fail, because msgBytes is null.");
            return;
        }
        Intent intent = new Intent();
        intent.setAction(PushConstants.ACTION_AWAKE_APP_LOGIC);
        intent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArrConvertThriftObjectToBytes);
        PushServiceClient.getInstance(context).sendDataCommon(intent);
    }

    private static void doSendPingByWakedUpApp(final Context context, final XmPushActionNotification xmPushActionNotification) {
        boolean booleanValue = OnlineConfig.getInstance(context).getBooleanValue(ConfigKey.AwakeAppPingSwitch.getValue(), false);
        int intValue = OnlineConfig.getInstance(context).getIntValue(ConfigKey.AwakeAppPingFrequency.getValue(), 0);
        int i = intValue;
        if (intValue >= 0) {
            i = intValue;
            if (intValue < 30) {
                MyLog.v("aw_ping: frquency need > 30s.");
                i = 30;
            }
        }
        if (i < 0) {
            booleanValue = false;
        }
        if (!MIUIUtils.isMIUI()) {
            sendAwakeAppPingMessage(context, xmPushActionNotification, booleanValue, i);
        } else if (booleanValue) {
            ScheduledJobManager.getInstance(context.getApplicationContext()).addRepeatJob(new ScheduledJobManager.Job() { // from class: com.xiaomi.mipush.sdk.AwakeHelper.1
                @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.Job
                public String getJobId() {
                    return "22";
                }

                @Override // java.lang.Runnable
                public void run() {
                    XmPushActionNotification xmPushActionNotification2 = xmPushActionNotification;
                    if (xmPushActionNotification2 != null) {
                        xmPushActionNotification2.setId(PacketHelper.generatePacketID());
                        PushServiceClient.getInstance(context.getApplicationContext()).sendMessage(xmPushActionNotification, ActionType.Notification, true, null, true);
                    }
                }
            }, i);
        }
    }

    public static final <T extends TBase<T, ?>> void sendAwakeAppPingMessage(Context context, T t, boolean z, int i) {
        byte[] bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(t);
        if (bArrConvertThriftObjectToBytes == null) {
            MyLog.w("send message fail, because msgBytes is null.");
            return;
        }
        Intent intent = new Intent();
        intent.setAction(PushConstants.ACTION_AWAKE_APP_PING);
        intent.putExtra(PushConstants.EXTRA_AWAKE_APP_PING_SWITCH, z);
        intent.putExtra(PushConstants.EXTRA_AWAKE_APP_PING_FREQUENCY, i);
        intent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArrConvertThriftObjectToBytes);
        intent.putExtra(PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE, true);
        PushServiceClient.getInstance(context).sendDataCommon(intent);
    }

    public static void sendPingByWakeUpApp(Context context, String str) {
        MyLog.w("aw_ping : send aw_ping cmd and content to push service from 3rd app");
        HashMap<String, String> map = new HashMap<>();
        map.put(AwakeUploadHelper.KEY_AWAKE_INFO, str);
        map.put(AwakeUploadHelper.KEY_EVENT_TYPE, String.valueOf(9999));
        map.put(AwakeUploadHelper.KEY_DESCRIPTION, "ping message");
        XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
        xmPushActionNotification.setAppId(AppInfoHolder.getInstance(context).getAppID());
        xmPushActionNotification.setPackageName(context.getPackageName());
        xmPushActionNotification.setType(NotificationType.AwakeAppResponse.value);
        xmPushActionNotification.setId(PacketHelper.generatePacketID());
        xmPushActionNotification.extra = map;
        doSendPingByWakedUpApp(context, xmPushActionNotification);
    }
}
