package com.xiaomi.mipush.sdk;

import android.content.Context;
import com.xiaomi.push.mpcd.CDActionProvider;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/CDActionProviderImpl.class */
public class CDActionProviderImpl implements CDActionProvider {
    private Context mContext;

    public CDActionProviderImpl(Context context) {
        this.mContext = context;
    }

    @Override // com.xiaomi.push.mpcd.CDActionProvider
    public String getRegSecret() {
        return AppInfoHolder.getInstance(this.mContext).getRegSecret();
    }

    @Override // com.xiaomi.push.mpcd.CDActionProvider
    public void uploadNotification(XmPushActionNotification xmPushActionNotification, ActionType actionType, PushMetaInfo pushMetaInfo) {
        PushServiceClient.getInstance(this.mContext).sendMessage(xmPushActionNotification, actionType, pushMetaInfo);
    }
}
