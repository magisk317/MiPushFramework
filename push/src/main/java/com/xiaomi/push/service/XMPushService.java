package com.xiaomi.push.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Messenger;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.android.Region;
import com.xiaomi.channel.commonutils.logger.LogTag;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.BuildSettings;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.channel.commonutils.misc.ThreadUtils;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.channel.commonutils.string.MD5;
import com.xiaomi.clientreport.data.Config;
import com.xiaomi.clientreport.util.ClientReportUtil;
import com.xiaomi.mipush.sdk.stat.db.MessageInfoContract;
import com.xiaomi.network.HostManager;
import com.xiaomi.push.log.LogUploader;
import com.xiaomi.push.protobuf.ChannelMessage;
import com.xiaomi.push.service.awake.module.AwakeManager;
import com.xiaomi.push.service.clientReport.PushClientReportHelper;
import com.xiaomi.push.service.clientReport.PushClientReportManager;
import com.xiaomi.push.service.clientReport.ReportConstants;
import com.xiaomi.push.service.timers.Alarm;
import com.xiaomi.slim.Blob;
import com.xiaomi.slim.SlimConnection;
import com.xiaomi.smack.Connection;
import com.xiaomi.smack.ConnectionConfiguration;
import com.xiaomi.smack.ConnectionListener;
import com.xiaomi.smack.PacketListener;
import com.xiaomi.smack.SmackConfiguration;
import com.xiaomi.smack.XMPPException;
import com.xiaomi.smack.filter.PacketFilter;
import com.xiaomi.smack.packet.IQ;
import com.xiaomi.smack.packet.Message;
import com.xiaomi.smack.packet.Packet;
import com.xiaomi.smack.packet.Presence;
import com.xiaomi.stats.StatsHandler;
import com.xiaomi.stats.StatsHelper;
import com.xiaomi.tinyData.TinyDataCacheProcessor;
import com.xiaomi.tinyData.TinyDataManager;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import com.xiaomi.xmpush.thrift.ConfigKey;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushActionRegistration;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import com.xiaomi.xmsf.runtime.PushChannelState;
import com.xiaomi.xmsf.runtime.PushRegistrationState;
import com.xiaomi.xmsf.runtime.PushRuntime;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.thrift.TException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/XMPushService.class */
public class XMPushService extends Service implements ConnectionListener {
    public static final String ACTION_CONNECTIVITY_INFO = "com.xiaomi.channel.CONNECTIVITY_INFO";
    public static final String ACTION_MILIAO_PUSH_STARTED = "com.xiaomi.channel.PUSH_STARTED";
    public static final int CHECK_ALIVE_INTERVAL = 30000;
    private static final String CHID_MILIAO = "1";
    public static final int CONNECTING_TIMEOUT = 15000;
    private static final String CONNECTIVITY_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";
    private static final String EXTREME_POWER_MODE = "EXTREME_POWER_MODE_ENABLE";
    public static final int LOGIN_TIMEOUT = 30000;
    public static final String MSG_DATA_KEY_HOST = "msg_data_hots";
    private static final String PACKAGE_NAME_MILIAO = "com.xiaomi.channel";
    private static final String SUPER_POWER_MODE = "power_supersave_mode_open";
    public static final int TIMER_RESET_CONNECTION = 30000;
    private static final boolean XMPPCONNECTION_DEBUG_ENABLED = true;
    private ConnectionConfiguration connConfig;
    private ClientEventDispatcher mClientEventDispatcher;
    private ConnectionChangeReceiver mConnectionChangeReceiver;
    private Connection mCurrentConnection;
    private ContentObserver mExtremePowerModeObserver;
    private ReconnectionManager mReconnManager;
    private String mRegion;
    private ScreenStateReceiver mScreenStateReceiver;
    private SlimConnection mSlimConnection;
    private ContentObserver mSuperPowerModeObserver;
    private final XMPushServiceLifecycleDelegate mLifecycleDelegate = new XMPushServiceLifecycleDelegate(this);
    private final XMPushServiceConnectionDelegate mConnectionDelegate = new XMPushServiceConnectionDelegate(this);
    private final XMPushServicePacketDelegate mPacketDelegate = new XMPushServicePacketDelegate(this);
    private final XMPushServiceIntentDelegate mIntentDelegate = new XMPushServiceIntentDelegate(this, this.mPacketDelegate);
    private int mFalldownStart = 0;
    private int mFalldownEnd = 0;
    private long lastAlive = 0;
    protected Class mJobClazz = XMJobService.class;
    private PacketSync mPacketSync = null;
    private JobScheduler mJobController = null;
    Messenger messenger = null;
    private Collection<NetworkListener> networkListeners = Collections.synchronizedCollection(new ArrayList());
    private ArrayList<PingCallBack> pingCallBacks = new ArrayList<>();
    private final PacketListener mPacketListener = new XMPushServiceInboundPacketListener(this);

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/XMPushService$Job.class */
    public static abstract class Job extends XMPushServiceJob {
        public Job(int i) {
            super(i);
        }
    }

    static {
        HostManager.addReservedHost(ConnectionConfiguration.XMPP_SERVER_CHINA_HOST_P, ConnectionConfiguration.XMPP_SERVER_CHINA_HOST_P);
    }

    void broadcastNetworkAvailable(boolean z) {
        this.mConnectionDelegate.broadcastNetworkAvailable(z);
    }

    boolean canOpenForegroundService() {
        return XMPushServiceEnvironment.canOpenForegroundService(this);
    }

    void checkAlive(boolean z) {
        this.mConnectionDelegate.checkAlive(z);
    }

    void clearPingCallbacks() {
        synchronized (this.pingCallBacks) {
            this.pingCallBacks.clear();
        }
    }

    void closeAllChannelByChid(String str, int i) {
        XMPushServiceChannelSupport.closeAllChannelByChid(this, str, i);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void connect() {
        this.mConnectionDelegate.connect();
    }

    void doAWLogic(Intent intent) {
        int i;
        try {
            AwakeManager.getInstance(getApplicationContext()).setSendDataIml(new PushLayerProcessIml());
            String stringExtra = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE);
            byte[] byteArrayExtra = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD);
            if (byteArrayExtra == null) {
                return;
            }
            XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionNotification, byteArrayExtra);
            String appId = xmPushActionNotification.getAppId();
            Map<String, String> extra = xmPushActionNotification.getExtra();
            if (extra != null) {
                String str = extra.get(PushConstants.EXTRA_AWAKE_APP_AWAKE_INFO);
                String str2 = extra.get(PushConstants.EXTRA_AWAKE_APP_ONLINE_CMD);
                if (!TextUtils.isEmpty(str2)) {
                    try {
                        i = Integer.parseInt(str2);
                    } catch (NumberFormatException e) {
                        i = 0;
                    }
                    if (!TextUtils.isEmpty(stringExtra) && !TextUtils.isEmpty(appId) && !TextUtils.isEmpty(str)) {
                        AwakeManager.getInstance(getApplicationContext()).wakeup(this, str, i, stringExtra, appId);
                    }
                }
            }
        } catch (TException e2) {
            MyLog.e("aw_logic: translate fail. " + e2.getMessage());
        }
    }

    void doAWPingCMD(Intent intent, int i) {
        byte[] byteArrayExtra = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD);
        boolean booleanExtra = intent.getBooleanExtra(PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE, true);
        XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
        try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionNotification, byteArrayExtra);
            ScheduledJobManager.getInstance(getApplicationContext()).addRepeatJob(new AwakeAppPingJob(xmPushActionNotification, new WeakReference(this), booleanExtra), i);
        } catch (TException e) {
            MyLog.e("aw_ping : send help app ping  error");
        }
    }

    void enableForegroundService() {
    }

    String ensureRegionAvaible() {
        return XMPushServiceEnvironment.ensureRegionAvailable(this);
    }

    void executeJobNow(Job job) {
        this.mJobController.executeJobNow(job);
    }

    int[] getFalldownTimeRange() {
        return XMPushServiceEnvironment.getFalldownTimeRange(this);
    }

    public static Notification getPushServiceNotification(Context context) {
        return XMPushServiceEnvironment.getPushServiceNotification(context);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleIntent(Intent intent) {
        this.mIntentDelegate.handleIntent(intent);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isExtremePowerSaveMode() {
        return XMPushServiceEnvironment.isExtremePowerSaveMode(this);
    }

    private boolean isInFalldownTimeRange() {
        return XMPushServiceEnvironment.isInFalldownTimeRange(this.mFalldownStart, this.mFalldownEnd);
    }

    boolean isPushEnabled() {
        return PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(getPackageName()) || !MIPushAppInfo.getInstance(this).isPushDisabled(getPackageName());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isSuperPowerModeEnable() {
        return XMPushServiceEnvironment.isSuperPowerModeEnable(this);
    }

    void networkChanged() {
        this.mLifecycleDelegate.networkChanged();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void postOnCreate() {
        this.mLifecycleDelegate.postOnCreate();
    }

    Packet preparePacket(Packet packet, String str, String str2) {
        return PushPacketRuntime.preparePacket(packet, str, str2, PushClientsManager.getInstance(), isConnected()).getPacket();
    }

    boolean shouldCheckAlive() {
        if (System.currentTimeMillis() - this.lastAlive < MessageInfoContract.TIMEOUT) {
            return false;
        }
        return Network.isConnected(this);
    }

    boolean shouldFalldown() {
        return XMPushServiceEnvironment.shouldFalldown(this, this.mFalldownStart, this.mFalldownEnd);
    }

    void unregisterReceiverSafely(BroadcastReceiver broadcastReceiver) {
        if (broadcastReceiver != null) {
            try {
                unregisterReceiver(broadcastReceiver);
            } catch (IllegalArgumentException e) {
                MyLog.e(e);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    void updateAlarmTimer() {
        if (!shouldReconnect()) {
            Alarm.stop();
        } else {
            if (Alarm.isAlive()) {
                return;
            }
            Alarm.registerPing(true);
        }
    }

    public void addPingCallBack(PingCallBack pingCallBack) {
        synchronized (this.pingCallBacks) {
            this.pingCallBacks.add(pingCallBack);
        }
    }

    public void batchSendPacket(Blob[] blobArr) throws XMPPException {
        this.mConnectionDelegate.batchSendPacket(blobArr);
    }

    public void batchSendPacket(Packet[] packetArr) throws XMPPException {
        this.mConnectionDelegate.batchSendPacket(packetArr);
    }

    public void closeChannel(String str, String str2, int i, String str3, String str4) {
        this.mConnectionDelegate.closeChannel(str, str2, i, str3, str4);
    }

    @Override // com.xiaomi.smack.ConnectionListener
    public void connectionClosed(Connection connection, int i, Exception exc) {
        this.mLifecycleDelegate.connectionClosed(connection, i, exc);
    }

    @Override // com.xiaomi.smack.ConnectionListener
    public void connectionStarted(Connection connection) {
        this.mLifecycleDelegate.connectionStarted(connection);
    }

    public ClientEventDispatcher createClientEventDispatcher() {
        return new ClientEventDispatcher();
    }

    public void disconnect(int i, Exception exc) {
        this.mConnectionDelegate.disconnect(i, exc);
    }

    public void executeJob(Job job) {
        executeJobDelayed(job, 0L);
    }

    public void executeJobDelayed(Job job, long j) {
        try {
            this.mJobController.executeJobDelayed(job, j);
        } catch (IllegalStateException e) {
            MyLog.w("can't execute job err = " + e.getMessage());
        }
    }

    public ClientEventDispatcher getClientEventDispatcher() {
        return this.mClientEventDispatcher;
    }

    void setClientEventDispatcher(ClientEventDispatcher clientEventDispatcher) {
        this.mClientEventDispatcher = clientEventDispatcher;
    }

    ConnectionConfiguration getConnectionConfiguration() {
        return this.connConfig;
    }

    void setConnectionConfiguration(ConnectionConfiguration connectionConfiguration) {
        this.connConfig = connectionConfiguration;
    }

    public Connection getCurrentConnection() {
        return this.mCurrentConnection;
    }

    void setCurrentConnection(Connection connection) {
        this.mCurrentConnection = connection;
    }

    void clearCurrentConnection() {
        this.mCurrentConnection = null;
    }

    JobScheduler getJobController() {
        return this.mJobController;
    }

    void setJobController(JobScheduler jobScheduler) {
        this.mJobController = jobScheduler;
    }

    Messenger getServiceMessenger() {
        return this.messenger;
    }

    void setServiceMessenger(Messenger messenger) {
        this.messenger = messenger;
    }

    SlimConnection getSlimConnection() {
        return this.mSlimConnection;
    }

    PacketListener getServicePacketListener() {
        return this.mPacketListener;
    }

    void setSlimConnection(SlimConnection slimConnection) {
        this.mSlimConnection = slimConnection;
    }

    ReconnectionManager getReconnectionManager() {
        return this.mReconnManager;
    }

    void setReconnectionManager(ReconnectionManager reconnectionManager) {
        this.mReconnManager = reconnectionManager;
    }

    PacketSync getPacketSync() {
        return this.mPacketSync;
    }

    void setPacketSync(PacketSync packetSync) {
        this.mPacketSync = packetSync;
    }

    String getRegionName() {
        return this.mRegion;
    }

    long getLastAliveAt() {
        return this.lastAlive;
    }

    void setLastAliveAt(long j) {
        this.lastAlive = j;
    }

    void setRegionName(String str) {
        this.mRegion = str;
    }

    ConnectionChangeReceiver getConnectionChangeReceiver() {
        return this.mConnectionChangeReceiver;
    }

    void clearConnectionChangeReceiver() {
        this.mConnectionChangeReceiver = null;
    }

    void setExtremePowerModeObserver(ContentObserver contentObserver) {
        this.mExtremePowerModeObserver = contentObserver;
    }

    ContentObserver getExtremePowerModeObserver() {
        return this.mExtremePowerModeObserver;
    }

    void setSuperPowerModeObserver(ContentObserver contentObserver) {
        this.mSuperPowerModeObserver = contentObserver;
    }

    ContentObserver getSuperPowerModeObserver() {
        return this.mSuperPowerModeObserver;
    }

    void setScreenStateReceiver(ScreenStateReceiver screenStateReceiver) {
        this.mScreenStateReceiver = screenStateReceiver;
    }

    ScreenStateReceiver getScreenStateReceiver() {
        return this.mScreenStateReceiver;
    }

    void clearScreenStateReceiver() {
        this.mScreenStateReceiver = null;
    }

    void setFalldownWindow(int i, int i2) {
        this.mFalldownStart = i;
        this.mFalldownEnd = i2;
    }

    void addNetworkListener(NetworkListener networkListener) {
        this.networkListeners.add(networkListener);
    }

    void clearNetworkListeners() {
        this.networkListeners.clear();
    }

    NetworkListener[] getNetworkListenersSnapshot() {
        return (NetworkListener[]) this.networkListeners.toArray(new NetworkListener[0]);
    }

    public boolean hasJob(int i) {
        return this.mJobController.hasJob(i);
    }

    public boolean hasJob(Job job) {
        return this.mJobController.hasJob(job.type, job);
    }

    public boolean isConnectAllowed() {
        return shouldReconnect();
    }

    public boolean isConnected() {
        Connection connection = this.mCurrentConnection;
        return connection != null && connection.isConnected();
    }

    public boolean isConnecting() {
        Connection connection = this.mCurrentConnection;
        return connection != null && connection.isConnecting();
    }

    public boolean isPushDisabled() {
        return XMPushServiceStateSupport.isPushDisabled(this);
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return this.messenger.getBinder();
    }

    void ensureConnectionChangeReceiver() {
        if (this.mConnectionChangeReceiver == null) {
            this.mConnectionChangeReceiver = new ConnectionChangeReceiver(this);
            registerReceiver(this.mConnectionChangeReceiver, new IntentFilter(CONNECTIVITY_ACTION));
        }
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        this.mLifecycleDelegate.onCreate();
    }

    @Override // android.app.Service
    public void onDestroy() {
        this.mLifecycleDelegate.onDestroy();
        super.onDestroy();
        MyLog.w("Service destroyed");
    }

    void onPong() {
        XMPushServicePingSupport.onPong(this.pingCallBacks);
    }

    @Override // android.app.Service
    public void onStart(Intent intent, int i) {
        this.mLifecycleDelegate.onStart(intent, i);
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int i, int i2) {
        return this.mLifecycleDelegate.onStartCommand(intent, i, i2);
    }

    @Override // com.xiaomi.smack.ConnectionListener
    public void reconnectionFailed(Connection connection, Exception exc) {
        this.mLifecycleDelegate.reconnectionFailed(connection, exc);
    }

    @Override // com.xiaomi.smack.ConnectionListener
    public void reconnectionSuccessful(Connection connection) {
        this.mLifecycleDelegate.reconnectionSuccessful(connection);
    }

    public void registerForMiPushApp(byte[] bArr, String str) {
        this.mPacketDelegate.registerForMiPushApp(bArr, str);
    }

    public void removeJobs(int i) {
        this.mJobController.removeJobs(i);
    }

    public void removeJobs(Job job) {
        this.mJobController.removeJobs(job.type, job);
    }

    public void removePingCallBack(PingCallBack pingCallBack) {
        synchronized (this.pingCallBacks) {
            this.pingCallBacks.remove(pingCallBack);
        }
    }

    public void scheduleConnect(boolean z) {
        this.mReconnManager.tryReconnect(z);
    }

    public void scheduleRebindChannel(PushClientsManager.ClientLoginInfo clientLoginInfo) {
        this.mConnectionDelegate.scheduleRebindChannel(clientLoginInfo);
    }

    void sendMessage(final String str, final byte[] bArr, boolean z) {
        this.mPacketDelegate.sendMessage(str, bArr, z);
    }

    public void sendPacket(Blob blob) throws XMPPException {
        this.mConnectionDelegate.sendPacket(blob);
    }

    public void sendPacket(Packet packet) throws XMPPException {
        this.mConnectionDelegate.sendPacket(packet);
    }

    void sendPongIfNeed() {
        this.mConnectionDelegate.sendPongIfNeed();
    }

    public void setConnectingTimeout() {
        this.mConnectionDelegate.setConnectingTimeout();
    }

    public boolean shouldReconnect() {
        return XMPushServiceStateSupport.shouldReconnect(this);
    }
}
