package com.xiaomi.mipush.sdk;

import android.content.Context;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.push.service.OnlineConfigHelper;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.ConfigListType;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.XmPushActionCheckClientInfo;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/OcVersionCheckJob.class */
public class OcVersionCheckJob extends ScheduledJobManager.Job {
    private Context context;

    public OcVersionCheckJob(Context context) {
        this.context = context;
    }

    @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.Job
    public String getJobId() {
        return "2";
    }

    @Override // java.lang.Runnable
    public void run() {
        OnlineConfig onlineConfig = OnlineConfig.getInstance(this.context);
        XmPushActionCheckClientInfo xmPushActionCheckClientInfo = new XmPushActionCheckClientInfo();
        xmPushActionCheckClientInfo.setMiscConfigVersion(OnlineConfigHelper.getVersion(onlineConfig, ConfigListType.MISC_CONFIG));
        xmPushActionCheckClientInfo.setPluginConfigVersion(OnlineConfigHelper.getVersion(onlineConfig, ConfigListType.PLUGIN_CONFIG));
        XmPushActionNotification xmPushActionNotification = new XmPushActionNotification("-1", false);
        xmPushActionNotification.setType(NotificationType.DailyCheckClientConfig.value);
        xmPushActionNotification.setBinaryExtra(XmPushThriftSerializeUtils.convertThriftObjectToBytes(xmPushActionCheckClientInfo));
        PushServiceClient.getInstance(this.context).sendMessage(xmPushActionNotification, ActionType.Notification, null);
    }
}
