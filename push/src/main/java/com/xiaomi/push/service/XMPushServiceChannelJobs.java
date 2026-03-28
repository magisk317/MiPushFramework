package com.xiaomi.push.service;

import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.stats.StatsHelper;
import com.xiaomi.smack.Connection;
import com.xiaomi.smack.XMPPException;

final class BindJob extends XMPushService.Job {
    private final XMPushService service;
    PushClientsManager.ClientLoginInfo loginInfo;

    BindJob(XMPushService xMPushService, PushClientsManager.ClientLoginInfo clientLoginInfo) {
        super(9);
        this.service = xMPushService;
        this.loginInfo = clientLoginInfo;
    }

    @Override
    public String getDesc() {
        return "bind the client. " + this.loginInfo.chid;
    }

    @Override
    public void process() {
        try {
            if (this.service.isConnected()) {
                PushClientsManager.ClientLoginInfo clientLoginInfoByChidAndUserId = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(this.loginInfo.chid, this.loginInfo.userId);
                if (clientLoginInfoByChidAndUserId == null) {
                    MyLog.w("ignore bind because the channel " + this.loginInfo.chid + " is removed ");
                } else if (clientLoginInfoByChidAndUserId.status == PushClientsManager.ClientStatus.unbind) {
                    clientLoginInfoByChidAndUserId.setStatus(PushClientsManager.ClientStatus.binding, 0, 0, null, null);
                    Connection currentConnection = this.service.getCurrentConnection();
                    if (currentConnection != null) {
                        currentConnection.bind(clientLoginInfoByChidAndUserId);
                        StatsHelper.statsBind(this.service, clientLoginInfoByChidAndUserId);
                    }
                } else {
                    MyLog.w("trying duplicate bind, ingore! " + clientLoginInfoByChidAndUserId.status);
                }
            } else {
                MyLog.e("trying bind while the connection is not created, quit!");
            }
        } catch (Exception e) {
            MyLog.e(e);
            this.service.disconnect(10, e);
        } catch (Throwable unused) {
        }
    }
}

final class BindTimeoutJob extends XMPushService.Job {
    public static final int BIND_TIMEOUT = 60000;
    private final PushClientsManager.ClientLoginInfo loginInfo;

    BindTimeoutJob(PushClientsManager.ClientLoginInfo clientLoginInfo) {
        super(12);
        this.loginInfo = clientLoginInfo;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BindTimeoutJob) {
            return TextUtils.equals(((BindTimeoutJob) obj).loginInfo.chid, this.loginInfo.chid);
        }
        return false;
    }

    @Override
    public String getDesc() {
        return "bind time out. chid=" + this.loginInfo.chid;
    }

    @Override
    public int hashCode() {
        return this.loginInfo.chid.hashCode();
    }

    @Override
    public void process() {
        this.loginInfo.setStatus(PushClientsManager.ClientStatus.unbind, 1, 21, null, null);
    }
}

final class PingJob extends XMPushService.Job {
    private final XMPushService service;
    boolean isPong;

    PingJob(XMPushService xMPushService) {
        super(4);
        this.service = xMPushService;
    }

    PingJob(XMPushService xMPushService, boolean z) {
        super(4);
        this.service = xMPushService;
        this.isPong = z;
    }

    @Override
    public String getDesc() {
        return "send ping..";
    }

    @Override
    public void process() {
        if (this.service.isConnected()) {
            try {
                if (!this.isPong) {
                    StatsHelper.pingStarted();
                }
                Connection currentConnection = this.service.getCurrentConnection();
                if (currentConnection != null) {
                    currentConnection.ping(this.isPong);
                }
            } catch (XMPPException e) {
                MyLog.e(e);
                this.service.disconnect(10, e);
            }
        }
    }
}

final class ReBindJob extends XMPushService.Job {
    private final XMPushService service;
    PushClientsManager.ClientLoginInfo loginInfo;

    ReBindJob(XMPushService xMPushService, PushClientsManager.ClientLoginInfo clientLoginInfo) {
        super(4);
        this.service = xMPushService;
        this.loginInfo = clientLoginInfo;
    }

    @Override
    public String getDesc() {
        return "rebind the client. " + this.loginInfo.chid;
    }

    @Override
    public void process() {
        try {
            this.loginInfo.setStatus(PushClientsManager.ClientStatus.unbind, 1, 16, null, null);
            Connection currentConnection = this.service.getCurrentConnection();
            if (currentConnection != null) {
                currentConnection.unbind(this.loginInfo.chid, this.loginInfo.userId);
                this.loginInfo.setStatus(PushClientsManager.ClientStatus.binding, 1, 16, null, null);
                currentConnection.bind(this.loginInfo);
            }
        } catch (XMPPException e) {
            MyLog.e(e);
            this.service.disconnect(10, e);
        }
    }
}

final class UnbindJob extends XMPushService.Job {
    private final XMPushService service;
    String kickType;
    PushClientsManager.ClientLoginInfo loginInfo;
    int notifyType;
    String reason;

    UnbindJob(XMPushService xMPushService, PushClientsManager.ClientLoginInfo clientLoginInfo, int i, String str, String str2) {
        super(9);
        this.service = xMPushService;
        this.loginInfo = clientLoginInfo;
        this.notifyType = i;
        this.kickType = str;
        this.reason = str2;
    }

    @Override
    public String getDesc() {
        return "unbind the channel. " + this.loginInfo.chid;
    }

    @Override
    public void process() {
        if (this.loginInfo.status != PushClientsManager.ClientStatus.unbind && this.service.getCurrentConnection() != null) {
            try {
                this.service.getCurrentConnection().unbind(this.loginInfo.chid, this.loginInfo.userId);
            } catch (XMPPException e) {
                MyLog.e(e);
                this.service.disconnect(10, e);
            }
        }
        this.loginInfo.setStatus(PushClientsManager.ClientStatus.unbind, this.notifyType, 0, this.reason, this.kickType);
    }
}
