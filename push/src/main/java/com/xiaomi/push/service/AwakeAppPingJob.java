package com.xiaomi.push.service;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.lang.ref.WeakReference;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/AwakeAppPingJob.class */
public class AwakeAppPingJob extends ScheduledJobManager.Job {
    private boolean mIsCache;
    private WeakReference<XMPushService> mXMPushServiceWR;
    private XmPushActionNotification mXmPushActionNotification;

    public AwakeAppPingJob(XmPushActionNotification xmPushActionNotification, WeakReference<XMPushService> weakReference, boolean z) {
        this.mIsCache = false;
        this.mXmPushActionNotification = xmPushActionNotification;
        this.mXMPushServiceWR = weakReference;
        this.mIsCache = z;
    }

    @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.Job
    public String getJobId() {
        return "22";
    }

    @Override // java.lang.Runnable
    public void run() {
        XMPushService xMPushService;
        WeakReference<XMPushService> weakReference = this.mXMPushServiceWR;
        if (weakReference == null || this.mXmPushActionNotification == null || (xMPushService = weakReference.get()) == null) {
            return;
        }
        this.mXmPushActionNotification.setId(PacketHelper.generatePacketID());
        this.mXmPushActionNotification.setRequireAck(false);
        MyLog.v("MoleInfo aw_ping : send aw_Ping msg " + this.mXmPushActionNotification.getId());
        try {
            String packageName = this.mXmPushActionNotification.getPackageName();
            xMPushService.sendMessage(packageName, XmPushThriftSerializeUtils.convertThriftObjectToBytes(MIPushHelper.generateRequestContainer(packageName, this.mXmPushActionNotification.getAppId(), this.mXmPushActionNotification, ActionType.Notification)), this.mIsCache);
        } catch (Exception e) {
            MyLog.e("MoleInfo aw_ping : send help app ping error" + e.toString());
        }
    }
}
