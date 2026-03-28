package com.xiaomi.stats;

import com.xiaomi.push.service.PushClientsManager;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.push.thrift.ChannelStatsType;
import com.xiaomi.push.thrift.StatsEvent;
import com.xiaomi.smack.Connection;
import com.xiaomi.stats.StatsAnalyser;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/stats/BindTracker.class */
class BindTracker implements PushClientsManager.ClientLoginInfo.ClientStatusListener {
    private PushClientsManager.ClientLoginInfo client;
    private Connection connection;
    private XMPushService pushService;
    private int reason;
    private boolean tracked = false;
    private PushClientsManager.ClientStatus status = PushClientsManager.ClientStatus.binding;

    /* JADX INFO: renamed from: com.xiaomi.stats.BindTracker$2, reason: invalid class name */
    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/stats/BindTracker$2.class */
    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$com$xiaomi$push$service$PushClientsManager$ClientStatus;

        static {
            int[] iArr = new int[PushClientsManager.ClientStatus.values().length];
            $SwitchMap$com$xiaomi$push$service$PushClientsManager$ClientStatus = iArr;
            try {
                iArr[PushClientsManager.ClientStatus.unbind.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$xiaomi$push$service$PushClientsManager$ClientStatus[PushClientsManager.ClientStatus.binding.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$xiaomi$push$service$PushClientsManager$ClientStatus[PushClientsManager.ClientStatus.binded.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
        }
    }

    BindTracker(XMPushService xMPushService, PushClientsManager.ClientLoginInfo clientLoginInfo) {
        this.pushService = xMPushService;
        this.client = clientLoginInfo;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void done() {
        untrack();
        if (this.tracked && this.reason != 11) {
            StatsEvent statsEventCreateStatsEvent = StatsHandler.getInstance().createStatsEvent();
            switch (AnonymousClass2.$SwitchMap$com$xiaomi$push$service$PushClientsManager$ClientStatus[this.status.ordinal()]) {
                case 1:
                    int i = this.reason;
                    if (i == 17) {
                        statsEventCreateStatsEvent.type = ChannelStatsType.BIND_TCP_READ_TIMEOUT.getValue();
                    } else if (i != 21) {
                        try {
                            StatsAnalyser.TypeWraper typeWraperFromBind = StatsAnalyser.fromBind(StatsHandler.getContext().getCaughtException());
                            statsEventCreateStatsEvent.type = typeWraperFromBind.type.getValue();
                            statsEventCreateStatsEvent.setAnnotation(typeWraperFromBind.annotation);
                        } catch (NullPointerException e) {
                            statsEventCreateStatsEvent = null;
                        }
                    } else {
                        statsEventCreateStatsEvent.type = ChannelStatsType.BIND_TIMEOUT.getValue();
                    }
                    break;
                case 3:
                    statsEventCreateStatsEvent.type = ChannelStatsType.BIND_SUCCESS.getValue();
                    break;
            }
            if (statsEventCreateStatsEvent != null) {
                statsEventCreateStatsEvent.setHost(this.connection.getHost());
                statsEventCreateStatsEvent.setUser(this.client.userId);
                statsEventCreateStatsEvent.value = 1;
                try {
                    statsEventCreateStatsEvent.setChid((byte) Integer.parseInt(this.client.chid));
                } catch (NumberFormatException e2) {
                }
                StatsHandler.getInstance().add(statsEventCreateStatsEvent);
            }
        }
    }

    private void untrack() {
        this.client.removeClientStatusListener(this);
    }

    @Override // com.xiaomi.push.service.PushClientsManager.ClientLoginInfo.ClientStatusListener
    public void onChange(PushClientsManager.ClientStatus clientStatus, PushClientsManager.ClientStatus clientStatus2, int i) {
        if (!this.tracked && clientStatus == PushClientsManager.ClientStatus.binding) {
            this.status = clientStatus2;
            this.reason = i;
            this.tracked = true;
        }
        this.pushService.executeJob(new XMPushService.Job(4) { // from class: com.xiaomi.stats.BindTracker.1
            @Override // com.xiaomi.push.service.XMPushService.Job
            public String getDesc() {
                return "Handling bind stats";
            }

            @Override // com.xiaomi.push.service.XMPushService.Job
            public void process() {
                BindTracker.this.done();
            }
        });
    }

    void track() {
        this.client.addClientStatusListener(this);
        this.connection = this.pushService.getCurrentConnection();
    }
}
