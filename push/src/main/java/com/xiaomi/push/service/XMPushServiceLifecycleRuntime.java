package com.xiaomi.push.service;

import com.magisk317.service.XMPushServiceLifecycleBridge;
import com.magisk317.service.XMPushServiceListener;
import android.net.ConnectivityManager;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.Region;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.push.log.LogUploader;
import com.xiaomi.push.service.timers.Alarm;
import com.xiaomi.smack.Connection;
import com.xiaomi.smack.ConnectionConfiguration;
import com.xiaomi.smack.util.TrafficUtils;
import com.xiaomi.stats.StatsHandler;
import com.xiaomi.xmsf.runtime.PushConnectionState;
import com.xiaomi.xmsf.runtime.PushRuntime;
import java.util.Iterator;

final class XMPushServiceLifecycleRuntime {
    private final XMPushService service;

    XMPushServiceLifecycleRuntime(XMPushService xMPushService) {
        this.service = xMPushService;
    }

    void configureClientChangeListener() {
        PushClientsManager pushClientsManager = PushClientsManager.getInstance();
        pushClientsManager.removeAllClientChangeListeners();
        pushClientsManager.addClientChangeListener(new PushClientsManager.ClientChangeListener() { // from class: com.xiaomi.push.service.XMPushServiceLifecycleRuntime.1
            @Override
            public void onChange() {
                XMPushServiceLifecycleRuntime.this.service.updateAlarmTimer();
                if (PushClientsManager.getInstance().getActiveClientCount() <= 0) {
                    XMPushServiceLifecycleRuntime.this.service.executeJob(new DisconnectJob(XMPushServiceLifecycleRuntime.this.service, 12, null));
                }
            }
        });
    }

    void networkChanged() {
        String activeNetworkName = Network.getActiveNetworkName(this.service);
        if (!TextUtils.isEmpty(activeNetworkName) && !"null".equals(activeNetworkName)) {
            MyLog.w("network changed,[type: " + activeNetworkName + "]");
        } else {
            MyLog.w("network changed, no active network");
        }
        if (StatsHandler.getContext() != null) {
            StatsHandler.getContext().statsChannelIfNeed();
        }
        TrafficUtils.notifyNetworkChanage(this.service);
        this.service.getSlimConnection().clearCachedStatus();
        if (Network.hasNetwork(this.service)) {
            if (this.service.isConnected() && this.service.shouldCheckAlive()) {
                this.service.checkAlive(false);
            }
            if (!this.service.isConnected() && !this.service.isConnecting()) {
                this.service.getJobController().removeJobs(1);
                this.service.executeJob(new ConnectJob(this.service));
            }
            LogUploader.getInstance(this.service).checkUpload();
        } else {
            this.service.executeJob(new DisconnectJob(this.service, 2, null));
        }
        this.service.updateAlarmTimer();
    }

    void postOnCreate() {
        AppRegionStorage appRegionStorage = AppRegionStorage.getInstance(this.service.getApplicationContext());
        String region = appRegionStorage.getRegion();
        MyLog.w("region of cache is " + region);
        String strEnsureRegionAvaible = region;
        if (TextUtils.isEmpty(region)) {
            strEnsureRegionAvaible = this.service.ensureRegionAvaible();
        }
        if (TextUtils.isEmpty(strEnsureRegionAvaible)) {
            this.service.setRegionName(Region.China.name());
        } else {
            this.service.setRegionName(strEnsureRegionAvaible);
            appRegionStorage.setRegion(strEnsureRegionAvaible);
            ConnectionConfiguration.setXmppServerHost(XMPushServiceEnvironment.resolveXmppRegionHost(this.service.getRegionName()));
        }
        if (Region.China.name().equals(this.service.getRegionName())) {
            ConnectionConfiguration.setXmppServerHost(XMPushServiceEnvironment.resolveXmppRegionHost(this.service.getRegionName()));
        }
        if (this.service.isPushEnabled()) {
            final XMPushService.Job job = new XMPushService.Job(11) { // from class: com.xiaomi.push.service.XMPushServiceLifecycleRuntime.2
                @Override
                public String getDesc() {
                    return "prepare the mi push account.";
                }

                @Override
                public void process() {
                    MIPushHelper.prepareMIPushAccount(XMPushServiceLifecycleRuntime.this.service);
                    if (Network.hasNetwork(XMPushServiceLifecycleRuntime.this.service)) {
                        XMPushServiceLifecycleRuntime.this.service.scheduleConnect(true);
                    }
                }
            };
            this.service.executeJob(job);
            PushAccountRuntime.setAccountChangeListener("XMPushService.postOnCreate", new Runnable() { // from class: com.xiaomi.push.service.XMPushServiceLifecycleRuntime.3
                @Override
                public void run() {
                    XMPushServiceLifecycleRuntime.this.service.executeJob(job);
                }
            });
        }
        try {
            if (SystemUtils.isBootCompleted()) {
                this.service.getClientEventDispatcher().notifyServiceStarted(this.service);
            }
        } catch (Exception e) {
            MyLog.e(e);
        }
    }

    void connectionClosed(Connection connection, int i, Exception exc) {
        StatsHandler.getContext().connectionClosed(connection, i, exc);
        XMPushServiceLifecycleBridge.onConnectionStatusChanged(XMPushServiceListener.ConnectionStatus.disconnected);
        PushConnectionClosedPlan planConnectionClosed = PushServiceConnectionRuntime.planConnectionClosed(this.service.shouldFalldown());
        PushRuntime.observeChannelEvent(null, planConnectionClosed.getEventAction(), "XMPushServiceLifecycleRuntime.connectionClosed");
        if (planConnectionClosed.getShouldScheduleReconnect()) {
            this.service.scheduleConnect(false);
        }
    }

    void connectionStarted(Connection connection) {
        MyLog.v("begin to connect...");
        XMPushServiceLifecycleBridge.onConnectionStatusChanged(XMPushServiceListener.ConnectionStatus.connecting);
        PushRuntime.observeConnectionState(PushConnectionState.Connecting, "XMPushServiceLifecycleRuntime.connectionStarted", connection == null ? null : connection.getHost(), "listener_started");
        StatsHandler.getContext().connectionStarted(connection);
    }

    void reconnectionFailed(Connection connection, Exception exc) {
        StatsHandler.getContext().reconnectionFailed(connection, exc);
        XMPushServiceLifecycleBridge.onConnectionStatusChanged(XMPushServiceListener.ConnectionStatus.disconnected);
        PushReconnectionFailurePlan planReconnectionFailure = PushServiceConnectionRuntime.planReconnectionFailure(this.service.shouldFalldown());
        PushRuntime.observeChannelEvent(null, planReconnectionFailure.getEventAction(), "XMPushServiceLifecycleRuntime.reconnectionFailed");
        if (planReconnectionFailure.getShouldBroadcastUnavailable()) {
            this.service.broadcastNetworkAvailable(false);
        }
        if (planReconnectionFailure.getShouldScheduleReconnect()) {
            this.service.scheduleConnect(false);
        }
    }

    void reconnectionSuccessful(Connection connection) {
        StatsHandler.getContext().reconnectionSuccessful(connection);
        XMPushServiceLifecycleBridge.onConnectionStatusChanged(XMPushServiceListener.ConnectionStatus.connected);
        PushReconnectionSuccessPlan planReconnectionSuccess = PushServiceConnectionRuntime.planReconnectionSuccess(Alarm.isAlive(), this.service.shouldFalldown());
        PushRuntime.observeChannelEvent(null, planReconnectionSuccess.getEventAction(), "XMPushServiceLifecycleRuntime.reconnectionSuccessful");
        PushRuntime.observeConnectionState(PushConnectionState.Connected, "XMPushServiceLifecycleRuntime.reconnectionSuccessful", connection == null ? null : connection.getHost(), "listener_connected");
        if (planReconnectionSuccess.getShouldBroadcastAvailable()) {
            this.service.broadcastNetworkAvailable(true);
        }
        if (planReconnectionSuccess.getShouldResetReconnectState()) {
            this.service.getReconnectionManager().onConnectSucceeded();
        }
        if (planReconnectionSuccess.getShouldRegisterAlarm()) {
            MyLog.w("reconnection successful, reactivate alarm.");
            Alarm.registerPing(true);
        }
        if (planReconnectionSuccess.getShouldBindAllClients()) {
            Iterator<PushClientsManager.ClientLoginInfo> it = PushClientsManager.getInstance().getAllClients().iterator();
            while (it.hasNext()) {
                this.service.executeJob(new BindJob(this.service, it.next()));
            }
        }
    }
}
