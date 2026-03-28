package com.xiaomi.push.service;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.slim.Blob;
import com.xiaomi.smack.XMPPException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/BatchSendMessageJob.class */
class BatchSendMessageJob extends XMPushService.Job {
    private Blob[] mBlobs;
    private XMPushService pushService;

    public BatchSendMessageJob(XMPushService xMPushService, Blob[] blobArr) {
        super(4);
        this.pushService = null;
        this.pushService = xMPushService;
        this.mBlobs = blobArr;
    }

    @Override // com.xiaomi.push.service.XMPushService.Job
    public String getDesc() {
        return "batch send message.";
    }

    @Override // com.xiaomi.push.service.XMPushService.Job
    public void process() {
        try {
            Blob[] blobArr = this.mBlobs;
            if (blobArr != null) {
                this.pushService.batchSendPacket(blobArr);
            }
        } catch (XMPPException e) {
            MyLog.e(e);
            this.pushService.disconnect(10, e);
        }
    }
}
