package com.xiaomi.push.service;

import android.content.Context;
import android.os.IBinder;
import android.os.Messenger;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/PushClientsManager.class */
public class PushClientsManager {
    private static PushClientsManager sInstance;
    private final PushClientsCollection clients = new PushClientsCollection();

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/PushClientsManager$ClientChangeListener.class */
    public interface ClientChangeListener {
        void onChange();
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/PushClientsManager$ClientLoginInfo.class */
    public static class ClientLoginInfo {
        public static final int TYPE_CHANNEL_CLOSE = 2;
        public static final int TYPE_CHANNEL_NO_NOTIFY = 0;
        public static final int TYPE_CHANNEL_OPEN_RESULT = 1;
        public static final int TYPE_CHANNEL_SERVER_KICK = 3;
        public String authMethod;
        public String chid;
        public String clientExtra;
        public String cloudExtra;
        public Context context;
        public boolean kick;
        public ClientEventDispatcher mClientEventDispatcher;
        private XMPushService mPushService;
        Messenger peer;
        public String pkgName;
        public String security;
        public String session;
        public String token;
        public String userId;
        ClientStatus status = ClientStatus.unbind;
        int currentRetrys = 0;
        final List<ClientStatusListener> statusChangeListeners = new ArrayList<>();
        ClientStatus notifiedStatus = null;
        boolean hasPeerSupport = false;
        final BindTimeoutJob timeOutJob = new BindTimeoutJob(this);
        IBinder.DeathRecipient peerWatcher = null;
        final PushClientNotifyJob notifyClientJob = new PushClientNotifyJob(this);

        /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/PushClientsManager$ClientLoginInfo$ClientStatusListener.class */
        public interface ClientStatusListener {
            void onChange(ClientStatus clientStatus, ClientStatus clientStatus2, int i);
        }

        public ClientLoginInfo() {
        }

        public ClientLoginInfo(XMPushService xMPushService) {
            this.mPushService = xMPushService;
            addClientStatusListener(new ClientStatusListener() { // from class: com.xiaomi.push.service.PushClientsManager.ClientLoginInfo.1
                @Override // com.xiaomi.push.service.PushClientsManager.ClientLoginInfo.ClientStatusListener
                public void onChange(ClientStatus clientStatus, ClientStatus clientStatus2, int i) {
                    if (clientStatus2 == ClientStatus.binding) {
                        ClientLoginInfo.this.mPushService.executeJobDelayed(ClientLoginInfo.this.timeOutJob, 60000L);
                    } else {
                        ClientLoginInfo.this.mPushService.removeJobs(ClientLoginInfo.this.timeOutJob);
                    }
                }
            });
        }

        public static String getResource(String str) {
            if (TextUtils.isEmpty(str)) {
                return "";
            }
            int iLastIndexOf = str.lastIndexOf("/");
            return iLastIndexOf != -1 ? str.substring(iLastIndexOf + 1) : "";
        }

        XMPushService getPushService() {
            return this.mPushService;
        }

        void notifyClientStatus(int i, int i2, String str, String str2) {
            PushClientStatusSupport.notifyClientStatus(this, i, i2, str, str2);
        }

        boolean shouldNotifyClient(int i, int i2, String str) {
            return PushClientStatusSupport.shouldNotifyClient(this, i, i2, str);
        }

        public void addClientStatusListener(ClientStatusListener clientStatusListener) {
            synchronized (this.statusChangeListeners) {
                this.statusChangeListeners.add(clientStatusListener);
            }
        }

        public String getDesc(int i) {
            return PushClientStatusSupport.getDesc(i);
        }

        public long getNextRetryInterval() {
            return (((long) ((Math.random() * 20.0d) - 10.0d)) + ((long) ((this.currentRetrys + 1) * 15))) * 1000;
        }

        public void removeClientStatusListener(ClientStatusListener clientStatusListener) {
            synchronized (this.statusChangeListeners) {
                this.statusChangeListeners.remove(clientStatusListener);
            }
        }

        public void setStatus(ClientStatus clientStatus, int i, int i2, String str, String str2) {
            int i3;
            PushClientStatusSupport.notifyStatusListeners(this, clientStatus, i2);
            ClientStatus clientStatus2 = this.status;
            if (clientStatus2 != clientStatus) {
                MyLog.w(String.format("update the client %7$s status. %1$s->%2$s %3$s %4$s %5$s %6$s", clientStatus2, clientStatus, getDesc(i), PushConstants.getErrorDesc(i2), str, str2, this.chid));
                this.status = clientStatus;
            }
            if (this.mClientEventDispatcher == null) {
                MyLog.e("status changed while the client dispatcher is missing");
                return;
            }
            if (clientStatus == ClientStatus.binding) {
                return;
            }
            i3 = PushClientStatusSupport.computeNotifyDelay(this);
            this.mPushService.removeJobs(this.notifyClientJob);
            if (PushClientStatusSupport.isSpecialError(this, i, i2, str2)) {
                notifyClientStatus(i, i2, str, str2);
            } else {
                this.mPushService.executeJobDelayed(this.notifyClientJob.build(i, i2, str, str2), i3);
            }
        }

        void unwatch() {
            try {
                Messenger messenger = this.peer;
                if (messenger != null && this.peerWatcher != null) {
                    messenger.getBinder().unlinkToDeath(this.peerWatcher, 0);
                }
            } catch (Exception e) {
            }
            this.notifiedStatus = null;
        }

        void watch(Messenger messenger) {
            unwatch();
            try {
                if (messenger != null) {
                    this.peer = messenger;
                    this.hasPeerSupport = true;
                    this.peerWatcher = new PushClientPeerWatcher(this, messenger);
                    messenger.getBinder().linkToDeath(this.peerWatcher, 0);
                } else {
                    MyLog.i("peer linked with old sdk chid = " + this.chid);
                }
            } catch (Exception e) {
                MyLog.i("peer linkToDeath err: " + e.getMessage());
                this.peer = null;
                this.hasPeerSupport = false;
            }
        }

        void onPeerDied(final Messenger messenger) {
            this.mPushService.executeJobDelayed(new XMPushService.Job(0) {
                @Override
                public String getDesc() {
                    return "clear peer job";
                }

                @Override
                public void process() {
                    if (messenger == ClientLoginInfo.this.peer) {
                        MyLog.i("clean peer, chid = " + ClientLoginInfo.this.chid);
                        ClientLoginInfo.this.peer = null;
                    }
                }
            }, 0L);
            if ("9".equals(this.chid) && PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(this.mPushService.getPackageName())) {
                this.mPushService.executeJobDelayed(new XMPushService.Job(0) {
                    @Override
                    public String getDesc() {
                        return "check peer job";
                    }

                    @Override
                    public void process() {
                        PushClientsManager.ClientLoginInfo clientLoginInfoByChidAndUserId = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(ClientLoginInfo.this.chid, ClientLoginInfo.this.userId);
                        if (clientLoginInfoByChidAndUserId != null && clientLoginInfoByChidAndUserId.peer == null) {
                            ClientLoginInfo.this.mPushService.closeChannel(ClientLoginInfo.this.chid, ClientLoginInfo.this.userId, 2, null, null);
                        }
                    }
                }, 60000L);
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/PushClientsManager$ClientStatus.class */
    public enum ClientStatus {
        unbind,
        binding,
        binded
    }

    private PushClientsManager() {
    }

    public static PushClientsManager getInstance() {
        PushClientsManager pushClientsManager;
        synchronized (PushClientsManager.class) {
            try {
                if (sInstance == null) {
                    sInstance = new PushClientsManager();
                }
                pushClientsManager = sInstance;
            } catch (Throwable th) {
                throw th;
            }
        }
        return pushClientsManager;
    }

    static String getSmtpLocalPart(String str) {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        int iIndexOf = str.indexOf("@");
        return iIndexOf > 0 ? str.substring(0, iIndexOf) : str;
    }

    public void addActiveClient(ClientLoginInfo clientLoginInfo) {
        synchronized (this) {
            this.clients.addActiveClient(clientLoginInfo);
        }
    }

    public void addClientChangeListener(ClientChangeListener clientChangeListener) {
        synchronized (this) {
            this.clients.addClientChangeListener(clientChangeListener);
        }
    }

    public void deactivateAllClientByChid(String str) {
        synchronized (this) {
            this.clients.deactivateAllClientByChid(str);
        }
    }

    public void deactivateClient(String str, String str2) {
        synchronized (this) {
            this.clients.deactivateClient(str, str2);
        }
    }

    public int getActiveClientCount() {
        int size;
        synchronized (this) {
            size = this.clients.getActiveClientCount();
        }
        return size;
    }

    public Collection<ClientLoginInfo> getAllClientLoginInfoByChid(String str) {
        synchronized (this) {
            return this.clients.getAllClientLoginInfoByChid(str);
        }
    }

    public ArrayList<ClientLoginInfo> getAllClients() {
        ArrayList<ClientLoginInfo> arrayList;
        synchronized (this) {
            arrayList = this.clients.getAllClients();
        }
        return arrayList;
    }

    public ClientLoginInfo getClientLoginInfoByChidAndUserId(String str, String str2) {
        synchronized (this) {
            return this.clients.getClientLoginInfoByChidAndUserId(str, str2);
        }
    }

    public void notifyConnectionFailed(Context context) {
        synchronized (this) {
            PushClientsStateSupport.notifyConnectionFailed(this.clients.getActiveClientMaps());
        }
    }

    public List<String> queryChannelIdByPackage(String str) {
        ArrayList<String> arrayList;
        synchronized (this) {
            arrayList = new ArrayList<>(PushClientsStateSupport.queryChannelIdByPackage(this.clients.getActiveClientMaps(), str));
        }
        return arrayList;
    }

    public void removeActiveClients() {
        synchronized (this) {
            this.clients.removeActiveClients();
        }
    }

    public void removeAllClientChangeListeners() {
        synchronized (this) {
            this.clients.removeAllClientChangeListeners();
        }
    }

    public void removeClientChangeListener(ClientChangeListener clientChangeListener) {
        synchronized (this) {
            this.clients.removeClientChangeListener(clientChangeListener);
        }
    }

    public void resetAllClients(Context context, int i) {
        synchronized (this) {
            PushClientsStateSupport.resetAllClients(this.clients.getActiveClientMaps(), i);
        }
    }
}
