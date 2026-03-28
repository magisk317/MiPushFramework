package com.xiaomi.mipush.sdk;

import android.content.Context;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.PacketHelper;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.awake.AwakeDataHelper;
import com.xiaomi.push.service.awake.AwakeUploadHelper;
import com.xiaomi.push.service.awake.module.AwakeManager;
import com.xiaomi.push.service.awake.module.IProcessData;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import java.util.HashMap;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/AppLayerProcessDataIml.class */
public class AppLayerProcessDataIml implements IProcessData {
    @Override // com.xiaomi.push.service.awake.module.IProcessData
    public void sendByTinyData(Context context, HashMap<String, String> map) {
        MiTinyDataClient.upload(PushConstants.CATEGORY_AWAKE, PushConstants.WAKE_UP_APP, 1L, AwakeDataHelper.getString(map));
        MyLog.w("MoleInfo：\u3000send data in app layer");
    }

    @Override // com.xiaomi.push.service.awake.module.IProcessData
    public void sendDirectly(Context context, HashMap<String, String> map) {
        XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
        xmPushActionNotification.setAppId(AwakeManager.getInstance(context).getAppId());
        xmPushActionNotification.setPackageName(AwakeManager.getInstance(context).getPackageName());
        xmPushActionNotification.setType(NotificationType.AwakeAppResponse.value);
        xmPushActionNotification.setId(PacketHelper.generatePacketID());
        xmPushActionNotification.extra = map;
        PushServiceClient.getInstance(context).sendMessage(xmPushActionNotification, ActionType.Notification, true, null, true);
        MyLog.w("MoleInfo：\u3000send data in app layer");
    }

    @Override // com.xiaomi.push.service.awake.module.IProcessData
    public void shouldDoLast(Context context, HashMap<String, String> map) {
        MyLog.w("MoleInfo：\u3000" + AwakeDataHelper.obfuscateLogContent(map));
        String str = map.get(AwakeUploadHelper.KEY_EVENT_TYPE);
        String str2 = map.get(AwakeUploadHelper.KEY_AWAKE_INFO);
        if (String.valueOf(1007).equals(str)) {
            AwakeHelper.sendPingByWakeUpApp(context, str2);
        }
    }
}
