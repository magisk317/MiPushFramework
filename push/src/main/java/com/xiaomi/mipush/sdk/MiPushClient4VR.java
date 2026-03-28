package com.xiaomi.mipush.sdk;

import android.content.Context;
import com.xiaomi.push.service.PacketHelper;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/MiPushClient4VR.class */
public class MiPushClient4VR {
    private static final String EXTRA_VR_DATA = "data";

    public static void uploadData(Context context, String str) {
        XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
        xmPushActionNotification.setType(NotificationType.VRUpload.value);
        xmPushActionNotification.setAppId(AppInfoHolder.getInstance(context).getAppID());
        xmPushActionNotification.setPackageName(context.getPackageName());
        xmPushActionNotification.putToExtra(EXTRA_VR_DATA, str);
        xmPushActionNotification.setId(PacketHelper.generatePacketID());
        PushServiceClient.getInstance(context).sendMessage(xmPushActionNotification, ActionType.Notification, null);
    }
}
