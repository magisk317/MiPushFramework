package com.xiaomi.push.service;

import android.content.Intent;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.BuildSettings;
import com.xiaomi.push.service.timers.Alarm;
import com.xiaomi.slim.SlimConnection;
import com.xiaomi.smack.ConnectionConfiguration;
import com.xiaomi.stats.StatsHandler;
import com.xiaomi.tinyData.TinyDataCacheProcessor;
import com.xiaomi.tinyData.TinyDataManager;

final class XMPushServiceLifecycleDelegate {
    private final XMPushService service;
    private final XMPushServiceLifecycleInfrastructure infrastructure;
    private final XMPushServiceLifecycleRuntime runtime;

    XMPushServiceLifecycleDelegate(XMPushService xMPushService) {
        this.service = xMPushService;
        this.infrastructure = new XMPushServiceLifecycleInfrastructure(xMPushService);
        this.runtime = new XMPushServiceLifecycleRuntime(xMPushService);
    }

    void onCreate() {
        MyLog.init(this.service.getApplicationContext());
        SystemUtils.initialize(this.service);
        MIPushAccount mIPushAccount = PushAccountRuntime.applyStoredAccountEnvironment(this.service, "XMPushService.onCreate");
        if (mIPushAccount != null) {
            BuildSettings.setEnvType(mIPushAccount.envType);
        }
        this.infrastructure.installMessenger();
        PushHostManagerFactory.init(this.service);
        ConnectionConfiguration connectionConfiguration = this.infrastructure.createConnectionConfiguration();
        connectionConfiguration.setDebuggerEnabled(true);
        this.service.setConnectionConfiguration(connectionConfiguration);
        SlimConnection slimConnection = new SlimConnection(this.service, connectionConfiguration);
        slimConnection.addConnectionListener(this.service);
        this.service.setSlimConnection(slimConnection);
        this.service.setClientEventDispatcher(this.service.createClientEventDispatcher());
        Alarm.initialize(this.service);
        this.service.setPacketSync(new PacketSync(this.service));
        this.service.setReconnectionManager(new ReconnectionManager(this.service));
        new CommonPacketExtensionProvider().register();
        StatsHandler.getInstance().init(this.service);
        this.service.setJobController(new JobScheduler("Connection Controller Thread"));
        this.runtime.configureClientChangeListener();
        if (this.service.canOpenForegroundService()) {
            this.service.enableForegroundService();
        }
        TinyDataManager.getInstance(this.service).addUploader(new LongConnUploader(this.service), TinyDataManager.UPLOADER_PUSH_CHANNEL);
        this.service.addPingCallBack(new TinyDataCacheProcessor(this.service));
        this.service.executeJob(new InitJob(this.service));
        this.service.addNetworkListener(Sync.getInstance(this.service));
        if (this.service.isPushEnabled()) {
            this.service.ensureConnectionChangeReceiver();
        }
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(this.service.getPackageName())) {
            this.infrastructure.installPowerModeObservers();
            this.infrastructure.installFalldownReceiver();
        }
        this.infrastructure.persistCreationLog(mIPushAccount);
    }

    void onDestroy() {
        ConnectionChangeReceiver connectionChangeReceiver = this.service.getConnectionChangeReceiver();
        if (connectionChangeReceiver != null) {
            this.service.unregisterReceiverSafely(connectionChangeReceiver);
            this.service.clearConnectionChangeReceiver();
        }
        ScreenStateReceiver screenStateReceiver = this.service.getScreenStateReceiver();
        if (screenStateReceiver != null) {
            this.service.unregisterReceiverSafely(screenStateReceiver);
            this.service.clearScreenStateReceiver();
        }
        this.infrastructure.unregisterPowerModeObservers();
        this.service.clearNetworkListeners();
        this.service.getJobController().removeAllJobs();
        this.service.executeJob(new XMPushService.Job(2) { // from class: com.xiaomi.push.service.XMPushServiceLifecycleDelegate.1
            @Override // com.xiaomi.push.service.XMPushService.Job
            public String getDesc() {
                return "disconnect for service destroy.";
            }

            @Override // com.xiaomi.push.service.XMPushService.Job
            public void process() {
                if (XMPushServiceLifecycleDelegate.this.service.getCurrentConnection() != null) {
                    XMPushServiceLifecycleDelegate.this.service.getCurrentConnection().disconnect(15, null);
                    XMPushServiceLifecycleDelegate.this.service.clearCurrentConnection();
                }
            }
        });
        this.service.executeJob(new KillJob(this.service));
        PushClientsManager.getInstance().removeAllClientChangeListeners();
        PushClientsManager.getInstance().resetAllClients(this.service, 15);
        PushClientsManager.getInstance().removeActiveClients();
        this.service.getSlimConnection().removeConnectionListener(this.service);
        ServiceConfig.getInstance().clear();
        Alarm.stop();
        this.service.clearPingCallbacks();
    }

    void onStart(Intent intent, int i) {
        long currentTimeMillis = System.currentTimeMillis();
        if (intent == null) {
            MyLog.e("onStart() with intent NULL");
        } else {
            MyLog.w(String.format("onStart() with intent.Action = %s, chid = %s, pkg = %s|%s", intent.getAction(), intent.getStringExtra(PushConstants.EXTRA_CHANNEL_ID), intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME), intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)));
        }
        if (intent != null && intent.getAction() != null) {
            if (PushServiceConstants.ACTION_TIMER.equalsIgnoreCase(intent.getAction()) || PushServiceConstants.ACTION_CHECK_ALIVE.equalsIgnoreCase(intent.getAction())) {
                if (this.service.getJobController().isBlocked()) {
                    MyLog.e("ERROR, the job controller is blocked.");
                    PushClientsManager.getInstance().resetAllClients(this.service, 14);
                    this.service.stopSelf();
                } else {
                    this.service.executeJob(new IntentJob(this.service, intent));
                }
            } else if (!PushServiceConstants.ACTION_NETWORK_STATUS_CHANGED.equalsIgnoreCase(intent.getAction())) {
                this.service.executeJob(new IntentJob(this.service, intent));
            }
        }
        long currentTimeMillis2 = System.currentTimeMillis() - currentTimeMillis;
        if (currentTimeMillis2 > 50) {
            MyLog.v("[Prefs] spend " + currentTimeMillis2 + " ms, too more times.");
        }
    }

    int onStartCommand(Intent intent, int i, int i2) {
        onStart(intent, i2);
        return 1;
    }

    void networkChanged() {
        this.runtime.networkChanged();
    }

    void connectionClosed(com.xiaomi.smack.Connection connection, int i, Exception exc) {
        this.runtime.connectionClosed(connection, i, exc);
    }

    void connectionStarted(com.xiaomi.smack.Connection connection) {
        this.runtime.connectionStarted(connection);
    }

    void postOnCreate() {
        this.runtime.postOnCreate();
    }

    void reconnectionFailed(com.xiaomi.smack.Connection connection, Exception exc) {
        this.runtime.reconnectionFailed(connection, exc);
    }

    void reconnectionSuccessful(com.xiaomi.smack.Connection connection) {
        this.runtime.reconnectionSuccessful(connection);
    }
}
