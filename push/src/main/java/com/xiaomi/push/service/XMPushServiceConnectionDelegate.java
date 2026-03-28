package com.xiaomi.push.service;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.slim.Blob;
import com.xiaomi.smack.Connection;
import com.xiaomi.smack.SmackConfiguration;
import com.xiaomi.smack.XMPPException;
import com.xiaomi.smack.filter.PacketFilter;
import com.xiaomi.smack.packet.Packet;
import com.xiaomi.xmsf.runtime.PushRuntime;

final class XMPushServiceConnectionDelegate {
    private final XMPushService service;

    XMPushServiceConnectionDelegate(XMPushService xMPushService) {
        this.service = xMPushService;
    }

    void connect() {
        Connection currentConnection = this.service.getCurrentConnection();
        PushConnectionAttemptPlan planConnect = PushServiceConnectionRuntime.planConnect(currentConnection != null && currentConnection.isConnecting(), currentConnection != null && currentConnection.isConnected());
        PushRuntime.observeChannelEvent(null, planConnect.getEventAction(), "XMPushServiceConnectionDelegate.connect");
        if (planConnect.getAction() == PushConnectionAttemptAction.SkipConnecting) {
            MyLog.e("try to connect while connecting.");
            return;
        }
        if (planConnect.getAction() == PushConnectionAttemptAction.SkipConnected) {
            MyLog.e("try to connect while is connected.");
            return;
        }
        this.service.getConnectionConfiguration().setConnectionPoint(Network.getActiveConnPoint(this.service));
        connectBySlim();
        if (this.service.getCurrentConnection() == null) {
            PushClientsManager.getInstance().notifyConnectionFailed(this.service);
            this.service.broadcastNetworkAvailable(false);
        }
    }

    void broadcastNetworkAvailable(boolean z) {
        try {
            if (com.xiaomi.channel.commonutils.android.SystemUtils.isBootCompleted()) {
                if (z) {
                    this.service.sendBroadcast(new android.content.Intent("miui.intent.action.NETWORK_CONNECTED"));
                    for (NetworkListener networkListener : this.service.getNetworkListenersSnapshot()) {
                        networkListener.onNetwrokAvaible();
                    }
                } else {
                    this.service.sendBroadcast(new android.content.Intent("miui.intent.action.NETWORK_BLOCKED"));
                }
            }
        } catch (Exception e) {
            MyLog.e(e);
        }
    }

    void checkAlive(boolean z) {
        this.service.setLastAliveAt(System.currentTimeMillis());
        PushCheckAlivePlan planCheckAlive = PushServiceConnectionRuntime.planCheckAlive(this.service.isConnected(), Network.hasNetwork(this.service));
        PushRuntime.observeChannelEvent(null, planCheckAlive.getEventAction(), "XMPushServiceConnectionDelegate.checkAlive");
        if (planCheckAlive.getAction() == PushCheckAliveAction.ScheduleConnect) {
            this.service.scheduleConnect(true);
        } else if (planCheckAlive.getAction() == PushCheckAliveAction.Ping) {
            this.service.executeJobNow(new PingJob(this.service, z));
        } else {
            this.service.executeJobNow(new DisconnectJob(this.service, 17, null));
            this.service.scheduleConnect(true);
        }
    }

    void disconnect(int i, Exception exc) {
        StringBuilder sb = new StringBuilder();
        sb.append("disconnect ");
        sb.append(this.service.hashCode());
        sb.append(", ");
        Connection currentConnection = this.service.getCurrentConnection();
        sb.append(currentConnection == null ? null : Integer.valueOf(currentConnection.hashCode()));
        MyLog.w(sb.toString());
        if (currentConnection != null) {
            currentConnection.disconnect(i, exc);
            this.service.clearCurrentConnection();
        }
        this.service.removeJobs(7);
        this.service.removeJobs(4);
        PushClientsManager.getInstance().resetAllClients(this.service, i);
    }

    void closeChannel(String str, String str2, int i, String str3, String str4) {
        PushClientsManager.ClientLoginInfo clientLoginInfoByChidAndUserId = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(str, str2);
        if (clientLoginInfoByChidAndUserId != null) {
            this.service.executeJob(new UnbindJob(this.service, clientLoginInfoByChidAndUserId, i, str4, str3));
        }
        PushClientsManager.getInstance().deactivateClient(str, str2);
    }

    void scheduleRebindChannel(PushClientsManager.ClientLoginInfo clientLoginInfo) {
        if (clientLoginInfo == null) {
            return;
        }
        long nextRetryInterval = clientLoginInfo.getNextRetryInterval();
        MyLog.w("schedule rebind job in " + (nextRetryInterval / 1000));
        this.service.executeJobDelayed(new BindJob(this.service, clientLoginInfo), nextRetryInterval);
    }

    void batchSendPacket(Blob[] blobArr) throws XMPPException {
        Connection currentConnection = requireConnection();
        currentConnection.batchSend(blobArr);
    }

    void batchSendPacket(Packet[] packetArr) throws XMPPException {
        Connection currentConnection = requireConnection();
        currentConnection.batchSendPacket(packetArr);
    }

    void sendPacket(Blob blob) throws XMPPException {
        Connection currentConnection = requireConnection();
        currentConnection.send(blob);
    }

    void sendPacket(Packet packet) throws XMPPException {
        Connection currentConnection = requireConnection();
        currentConnection.sendPacket(packet);
    }

    void setConnectingTimeout() {
        this.service.executeJobDelayed(new XMPushService.Job(10) { // from class: com.xiaomi.push.service.XMPushServiceConnectionDelegate.1
            @Override // com.xiaomi.push.service.XMPushService.Job
            public String getDesc() {
                return "disconnect because of connecting timeout";
            }

            @Override // com.xiaomi.push.service.XMPushService.Job
            public void process() {
                if (XMPushServiceConnectionDelegate.this.service.isConnecting()) {
                    XMPushServiceConnectionDelegate.this.service.disconnect(18, null);
                }
            }
        }, 15000L);
    }

    void sendPongIfNeed() {
        if (System.currentTimeMillis() - this.service.getLastAliveAt() >= SmackConfiguration.getCheckAliveInterval() && Network.isConnected(this.service)) {
            checkAlive(true);
        }
    }

    private void connectBySlim() {
        try {
            this.service.getSlimConnection().addPacketListener(this.service.getServicePacketListener(), new PacketFilter() { // from class: com.xiaomi.push.service.XMPushServiceConnectionDelegate.2
                @Override // com.xiaomi.smack.filter.PacketFilter
                public boolean accept(Packet packet) {
                    return true;
                }
            });
            this.service.getSlimConnection().connect();
            this.service.setCurrentConnection(this.service.getSlimConnection());
        } catch (XMPPException e) {
            MyLog.e("fail to create Slim connection", e);
            this.service.getSlimConnection().disconnect(3, e);
        }
    }

    private Connection requireConnection() throws XMPPException {
        Connection currentConnection = this.service.getCurrentConnection();
        if (currentConnection == null) {
            throw new XMPPException("try send msg while connection is null.");
        }
        return currentConnection;
    }
}
