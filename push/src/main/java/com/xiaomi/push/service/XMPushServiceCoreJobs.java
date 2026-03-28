package com.xiaomi.push.service;

import android.content.Intent;
import com.xiaomi.channel.commonutils.logger.LogTag;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.slim.Blob;
import com.xiaomi.smack.XMPPException;
import com.xiaomi.smack.packet.Packet;

final class BlobReceiveJob extends XMPushService.Job {
    private final XMPushService service;
    private final Blob blob;

    BlobReceiveJob(XMPushService xMPushService, Blob blob) {
        super(8);
        this.service = xMPushService;
        this.blob = blob;
    }

    @Override
    public String getDesc() {
        return "receive a message.";
    }

    @Override
    public void process() {
        this.service.getPacketSync().onBlobReceive(this.blob);
    }
}

final class PacketReceiveJob extends XMPushService.Job {
    private final XMPushService service;
    private final Packet packet;

    PacketReceiveJob(XMPushService xMPushService, Packet packet) {
        super(8);
        this.service = xMPushService;
        this.packet = packet;
    }

    @Override
    public String getDesc() {
        return "receive a message.";
    }

    @Override
    public void process() {
        this.service.getPacketSync().onPacketReceive(this.packet);
    }
}

final class ConnectJob extends XMPushService.Job {
    private final XMPushService service;

    ConnectJob(XMPushService xMPushService) {
        super(1);
        this.service = xMPushService;
    }

    @Override
    public String getDesc() {
        return "do reconnect..";
    }

    @Override
    public void process() {
        if (this.service.shouldReconnect()) {
            this.service.connect();
        } else {
            MyLog.w("should not connect. quit the job.");
        }
    }
}

final class DisconnectJob extends XMPushService.Job {
    private final XMPushService service;
    public Exception e;
    public int reason;

    DisconnectJob(XMPushService xMPushService, int i, Exception exc) {
        super(2);
        this.service = xMPushService;
        this.reason = i;
        this.e = exc;
    }

    @Override
    public String getDesc() {
        return "disconnect the connection.";
    }

    @Override
    public void process() {
        this.service.disconnect(this.reason, this.e);
    }
}

final class InitJob extends XMPushService.Job {
    private final XMPushService service;

    InitJob(XMPushService xMPushService) {
        super(XMPushService.Job.TYPE_INIT);
        this.service = xMPushService;
    }

    @Override
    public String getDesc() {
        return "Init Job";
    }

    @Override
    public void process() {
        this.service.postOnCreate();
    }
}

final class IntentJob extends XMPushService.Job {
    private final XMPushService service;
    private final Intent intent;

    IntentJob(XMPushService xMPushService, Intent intent) {
        super(15);
        this.service = xMPushService;
        this.intent = intent;
    }

    @Override
    public String getDesc() {
        return "Handle intent action = " + this.intent.getAction();
    }

    @Override
    public void process() {
        this.service.handleIntent(this.intent);
    }
}

final class KillJob extends XMPushService.Job {
    private final XMPushService service;

    KillJob(XMPushService xMPushService) {
        super(5);
        this.service = xMPushService;
    }

    @Override
    public String getDesc() {
        return "ask the job queue to quit";
    }

    @Override
    public void process() {
        this.service.getJobController().quit();
    }
}

final class ResetConnectionJob extends XMPushService.Job {
    private final XMPushService service;

    ResetConnectionJob(XMPushService xMPushService) {
        super(3);
        this.service = xMPushService;
    }

    @Override
    public String getDesc() {
        return "reset the connection.";
    }

    @Override
    public void process() {
        this.service.disconnect(11, null);
        if (this.service.shouldReconnect()) {
            this.service.connect();
        }
    }
}
