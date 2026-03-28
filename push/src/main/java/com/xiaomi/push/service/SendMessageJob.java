package com.xiaomi.push.service;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.slim.Blob;
import com.xiaomi.smack.XMPPException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/SendMessageJob.class */
class SendMessageJob extends XMPushService.Job {
    private Blob mBlob;
    private XMPushService pushService;

    public SendMessageJob(XMPushService xMPushService, Blob blob) {
        super(4);
        this.pushService = null;
        this.pushService = xMPushService;
        this.mBlob = blob;
    }

    @Override // com.xiaomi.push.service.XMPushService.Job
    public String getDesc() {
        return "send a message.";
    }

    @Override // com.xiaomi.push.service.XMPushService.Job
    public void process() {
        try {
            Blob blob = this.mBlob;
            if (blob != null) {
                this.pushService.sendPacket(blob);
            }
        } catch (XMPPException e) {
            MyLog.e(e);
            this.pushService.disconnect(10, e);
        }
    }
}
