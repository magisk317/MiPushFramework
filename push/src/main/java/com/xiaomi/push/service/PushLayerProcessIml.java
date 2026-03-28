package com.xiaomi.push.service;

import android.content.Context;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.awake.AwakeDataHelper;
import com.xiaomi.push.service.awake.module.AwakeManager;
import com.xiaomi.push.service.awake.module.IProcessData;
import com.xiaomi.tinyData.TinyDataManager;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.util.HashMap;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/PushLayerProcessIml.class */
public class PushLayerProcessIml implements IProcessData {
    @Override // com.xiaomi.push.service.awake.module.IProcessData
    public void sendByTinyData(Context context, HashMap<String, String> map) {
        TinyDataManager tinyDataManager = TinyDataManager.getInstance(context);
        if (tinyDataManager != null) {
            tinyDataManager.upload(PushConstants.CATEGORY_AWAKE, PushConstants.WAKE_UP_APP, 1L, AwakeDataHelper.getString(map));
        }
    }

    @Override // com.xiaomi.push.service.awake.module.IProcessData
    public void sendDirectly(Context context, HashMap<String, String> map) {
        XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
        xmPushActionNotification.setAppId(AwakeManager.getInstance(context).getAppId());
        xmPushActionNotification.setPackageName(AwakeManager.getInstance(context).getPackageName());
        xmPushActionNotification.setType(NotificationType.AwakeAppResponse.value);
        xmPushActionNotification.setId(PacketHelper.generatePacketID());
        xmPushActionNotification.extra = map;
        byte[] bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(MIPushHelper.generateRequestContainer(xmPushActionNotification.getPackageName(), xmPushActionNotification.getAppId(), xmPushActionNotification, ActionType.Notification));
        if (!(context instanceof XMPushService)) {
            MyLog.w("MoleInfo : context is not correct in pushLayer " + xmPushActionNotification.getId());
            return;
        }
        MyLog.w("MoleInfo : send data directly in pushLayer " + xmPushActionNotification.getId());
        ((XMPushService) context).sendMessage(context.getPackageName(), bArrConvertThriftObjectToBytes, true);
    }

    @Override // com.xiaomi.push.service.awake.module.IProcessData
    public void shouldDoLast(Context context, HashMap<String, String> map) {
        MyLog.w("MoleInfo：\u3000" + AwakeDataHelper.obfuscateLogContent(map));
    }
}
