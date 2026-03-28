package com.xiaomi.push.service;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.stats.StatsHandler;
import com.xiaomi.xmsf.runtime.PushRuntime;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/ReconnectionManager.class */
class ReconnectionManager {
    private XMPushService mPushService;
    private PushReconnectState state = PushReconnectRuntime.initialState();

    public ReconnectionManager(XMPushService xMPushService) {
        this.mPushService = xMPushService;
    }

    public void onConnectSucceeded() {
        this.state = PushReconnectRuntime.onConnectSucceeded(System.currentTimeMillis());
        this.mPushService.removeJobs(1);
        PushRuntime.observeChannelEvent(null, "reconnect_succeeded", "ReconnectionManager.onConnectSucceeded");
    }

    public void tryReconnect(boolean z) {
        PushReconnectAttemptPlan planReconnect = PushReconnectRuntime.planReconnect(this.state, z, this.mPushService.shouldReconnect(), this.mPushService.hasJob(1), System.currentTimeMillis());
        this.state = planReconnect.getNextState();
        PushRuntime.observeChannelEvent(null, planReconnect.getEventAction(), "ReconnectionManager.tryReconnect");
        if (planReconnect.getAction() == PushReconnectAction.SkipNoReconnect) {
            MyLog.v("should not reconnect as no client or network.");
            return;
        }
        if (planReconnect.getAction() == PushReconnectAction.SkipExistingJob) {
            return;
        }
        if (planReconnect.getAction() == PushReconnectAction.Immediate) {
            this.mPushService.removeJobs(1);
            XMPushService xMPushService = this.mPushService;
            xMPushService.getClass();
            xMPushService.executeJob(new ConnectJob(xMPushService));
            return;
        }
        MyLog.w("schedule reconnect in " + planReconnect.getDelayMs() + "ms");
        XMPushService xMPushService2 = this.mPushService;
        xMPushService2.getClass();
        xMPushService2.executeJobDelayed(new ConnectJob(xMPushService2), (long) planReconnect.getDelayMs());
        if (planReconnect.getShouldDumpNativeNetInfo() && StatsHandler.getInstance().isAllowStats()) {
            NetworkCheckup.dumpNativeNetInfo();
        }
        if (planReconnect.getShouldRunConnectivityTest()) {
            NetworkCheckup.connectivityTest();
        }
    }
}
