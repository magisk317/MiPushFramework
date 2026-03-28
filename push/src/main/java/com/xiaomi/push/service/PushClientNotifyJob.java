package com.xiaomi.push.service;

import com.xiaomi.channel.commonutils.logger.MyLog;

final class PushClientNotifyJob extends XMPushService.Job {
    String errorType;
    private final PushClientsManager.ClientLoginInfo info;
    int notifyType;
    int reason;
    String reasonMessage;

    PushClientNotifyJob(PushClientsManager.ClientLoginInfo clientLoginInfo) {
        super(0);
        this.info = clientLoginInfo;
    }

    XMPushService.Job build(int i, int i2, String str, String str2) {
        this.notifyType = i;
        this.reason = i2;
        this.errorType = str2;
        this.reasonMessage = str;
        return this;
    }

    @Override
    public String getDesc() {
        return "notify job";
    }

    @Override
    public void process() {
        if (this.info.shouldNotifyClient(this.notifyType, this.reason, this.errorType)) {
            this.info.notifyClientStatus(this.notifyType, this.reason, this.reasonMessage, this.errorType);
            return;
        }
        MyLog.i(" ignore notify client :" + this.info.chid);
    }
}
