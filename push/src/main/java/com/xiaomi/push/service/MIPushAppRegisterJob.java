package com.xiaomi.push.service;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.PushClientsManager;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.smack.XMPPException;
import com.xiaomi.xmsf.runtime.PushChannelState;
import com.xiaomi.xmsf.runtime.PushRegistrationState;
import com.xiaomi.xmsf.runtime.PushRuntime;
import com.xiaomi.xmsf.runtime.PushRuntimeChannelTracker;
import java.util.Collection;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/MIPushAppRegisterJob.class */
public class MIPushAppRegisterJob extends XMPushService.Job {
    private String appId;
    private String appToken;
    private String packageName;
    private byte[] payload;
    private XMPushService pushService;

    public MIPushAppRegisterJob(XMPushService xMPushService, String str, String str2, String str3, byte[] bArr) {
        super(9);
        this.pushService = xMPushService;
        this.packageName = str;
        this.payload = bArr;
        this.appId = str2;
        this.appToken = str3;
    }

    @Override // com.xiaomi.push.service.XMPushService.Job
    public String getDesc() {
        return "register app";
    }

    @Override // com.xiaomi.push.service.XMPushService.Job
    public void process() {
        PushClientsManager.ClientLoginInfo next;
        MIPushAccount mIPushAccount = PushAccountRuntime.loadAccount(this.pushService, "MIPushAppRegisterJob.process");
        MIPushAccount mIPushAccountRegister = mIPushAccount;
        if (mIPushAccount == null) {
            mIPushAccountRegister = PushAccountRuntime.registerAccount(this.pushService, this.packageName, this.appId, this.appToken, "MIPushAppRegisterJob.process");
        }
        if (mIPushAccountRegister == null) {
            MyLog.e("no account for mipush");
            PushRuntime.observeAccountEvent("account_missing", "MIPushAppRegisterJob.process");
            PushRuntime.observeRegistrationResult(this.packageName, false, "MIPushAppRegisterJob.process", "no_account");
            MIPushClientManager.notifyRegisterError(this.pushService, 70000002, "no account.");
            return;
        }
        PushRuntime.observeAccountEvent("account_ready", "MIPushAppRegisterJob.process");
        Collection<PushClientsManager.ClientLoginInfo> allClientLoginInfoByChid = PushClientsManager.getInstance().getAllClientLoginInfoByChid("5");
        if (allClientLoginInfoByChid.isEmpty()) {
            next = mIPushAccountRegister.toClientLoginInfo(this.pushService);
            MIPushHelper.prepareClientLoginInfo(this.pushService, next);
            PushClientsManager.getInstance().addActiveClient(next);
            PushRuntimeChannelTracker.syncNow("MIPushAppRegisterJob.process:add_client");
        } else {
            next = allClientLoginInfoByChid.iterator().next();
        }
        if (!this.pushService.isConnected()) {
            PushRuntime.observeChannelState(this.packageName, next.chid, next.userId, next.session, PushChannelState.Binding, "MIPushAppRegisterJob.process", null, "schedule_connect_for_register");
            this.pushService.scheduleConnect(true);
            return;
        }
        try {
            if (next.status == PushClientsManager.ClientStatus.binded) {
                PushRuntime.observeRegistrationState(this.packageName, PushRegistrationState.Registering, "MIPushAppRegisterJob.process", "registration_payload_sent");
                MIPushHelper.sendPacket(this.pushService, this.packageName, this.payload);
            } else if (next.status == PushClientsManager.ClientStatus.unbind) {
                PushRuntime.observeChannelState(this.packageName, next.chid, next.userId, next.session, PushChannelState.Binding, "MIPushAppRegisterJob.process", null, "bind_for_register");
                this.pushService.executeJob(new BindJob(this.pushService, next));
            }
        } catch (XMPPException e2) {
            MyLog.e("meet error, disconnect connection. " + e2);
            PushRuntime.observeRegistrationResult(this.packageName, false, "MIPushAppRegisterJob.process", "send_packet_failed");
            this.pushService.disconnect(10, e2);
        }
    }
}
