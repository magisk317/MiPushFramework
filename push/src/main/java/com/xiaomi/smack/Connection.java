package com.xiaomi.smack;

import android.util.Pair;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.channel.commonutils.string.MD5;
import com.xiaomi.measite.smack.AndroidDebugger;
import com.xiaomi.push.service.PushConnectionListenerEvent;
import com.xiaomi.push.service.PushConnectionStatusPlan;
import io.github.magisk317.mipush.service.runtime.PushConnectionStatusRuntime;
import com.xiaomi.push.service.PushClientsManager;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.slim.Blob;
import com.xiaomi.smack.debugger.SmackDebugger;
import com.xiaomi.smack.filter.PacketFilter;
import com.xiaomi.smack.packet.Packet;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/Connection.class */
public abstract class Connection {
    public static boolean DEBUG_ENABLED = false;
    private static final long EFFECTIVE_STATUS = 1800000;
    public static final int ERR_BOSH = 499;
    public static final int ERR_TCP_BROKEN_PIPE = 110;
    public static final int ERR_TCP_CONNREFUSED = 103;
    public static final int ERR_TCP_CONNRESET = 109;
    public static final int ERR_TCP_INVALARG = 106;
    public static final int ERR_TCP_NETUNREACH = 102;
    public static final int ERR_TCP_NOACCESS = 101;
    public static final int ERR_TCP_NOROUTETOHOST = 104;
    public static final int ERR_TCP_OTHER = 199;
    public static final int ERR_TCP_READ_TIMEOUT = 108;
    public static final int ERR_TCP_TIMEOUT = 105;
    public static final int ERR_TCP_UKNOWNHOST = 107;
    public static final int ERR_UNKNOWN = 0;
    public static final int ERR_XMPP = 399;
    private static final int MAX_STATUS_CNT = 6;
    public static final long PING_TIMEOUT = 10000;
    private static final AtomicInteger connectionCounter = new AtomicInteger(0);
    protected ConnectionConfiguration config;
    protected XMPushService mPushService;
    protected int connTimes = 0;
    protected long connectTime = -1;
    protected volatile long lastPingSent = 0;
    protected volatile long lastPingReceived = 0;
    private LinkedList<Pair<Integer, Long>> mCachedStatus = new LinkedList<>();
    private final Collection<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    protected final Map<PacketListener, ListenerWrapper> recvListeners = new ConcurrentHashMap<>();
    protected final Map<PacketListener, ListenerWrapper> sendListeners = new ConcurrentHashMap<>();
    protected SmackDebugger debugger = null;
    protected String challenge = "";
    protected String connectionPoint = "";
    private int connectStatus = 2;
    protected final int connectionCounterValue = connectionCounter.getAndIncrement();
    private long readAlive = 0;
    protected long writeAlive = 0;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/Connection$ListenerWrapper.class */
    public static class ListenerWrapper {
        private PacketFilter packetFilter;
        private PacketListener packetListener;

        public ListenerWrapper(PacketListener packetListener, PacketFilter packetFilter) {
            this.packetListener = packetListener;
            this.packetFilter = packetFilter;
        }

        public void notifyListener(Blob blob) {
            this.packetListener.process(blob);
        }

        public void notifyListener(Packet packet) {
            PacketFilter packetFilter = this.packetFilter;
            if (packetFilter == null || packetFilter.accept(packet)) {
                this.packetListener.processPacket(packet);
            }
        }
    }

    static {
        DEBUG_ENABLED = false;
        try {
            DEBUG_ENABLED = Boolean.getBoolean("smack.debugEnabled");
        } catch (Exception e) {
        }
        SmackConfiguration.getVersion();
    }

    protected Connection(XMPushService xMPushService, ConnectionConfiguration connectionConfiguration) {
        this.config = connectionConfiguration;
        this.mPushService = xMPushService;
        initDebugger();
    }

    private void addStatus(int i) {
        synchronized (this.mCachedStatus) {
            if (i == 1) {
                this.mCachedStatus.clear();
            } else {
                this.mCachedStatus.add(new Pair<>(Integer.valueOf(i), Long.valueOf(System.currentTimeMillis())));
                if (this.mCachedStatus.size() > 6) {
                    this.mCachedStatus.remove(0);
                }
            }
        }
    }

    private String getDesc(int i) {
        return i == 1 ? "connected" : i == 0 ? "connecting" : i == 2 ? "disconnected" : "unknown";
    }

    public void addConnectionListener(ConnectionListener connectionListener) {
        if (connectionListener == null || this.connectionListeners.contains(connectionListener)) {
            return;
        }
        this.connectionListeners.add(connectionListener);
    }

    public void addPacketListener(PacketListener packetListener, PacketFilter packetFilter) {
        if (packetListener == null) {
            throw new NullPointerException("Packet listener is null.");
        }
        this.recvListeners.put(packetListener, new ListenerWrapper(packetListener, packetFilter));
    }

    public void addPacketSendingListener(PacketListener packetListener, PacketFilter packetFilter) {
        if (packetListener == null) {
            throw new NullPointerException("Packet listener is null.");
        }
        this.sendListeners.put(packetListener, new ListenerWrapper(packetListener, packetFilter));
    }

    public abstract void batchSend(Blob[] blobArr) throws XMPPException;

    public abstract void batchSendPacket(Packet[] packetArr) throws XMPPException;

    public abstract void bind(PushClientsManager.ClientLoginInfo clientLoginInfo) throws XMPPException;

    public void clearCachedStatus() {
        synchronized (this.mCachedStatus) {
            this.mCachedStatus.clear();
        }
    }

    public abstract void connect() throws XMPPException;

    public void disconnect() {
        disconnect(0, null);
    }

    public abstract void disconnect(int i, Exception exc);

    protected void firePacketSendingListeners(Packet packet) {
        Iterator<ListenerWrapper> it = this.sendListeners.values().iterator();
        while (it.hasNext()) {
            it.next().notifyListener(packet);
        }
    }

    public ConnectionConfiguration getConfiguration() {
        return this.config;
    }

    public int getConnTryTimes() {
        return this.connTimes;
    }

    public long getConnectTime() {
        return this.connectTime;
    }

    public Collection<ConnectionListener> getConnectionListeners() {
        return this.connectionListeners;
    }

    public String getConnectionPoint() {
        return this.config.getConnectionPoint();
    }

    public int getConnectionStatus() {
        return this.connectStatus;
    }

    public String getCurrentNetwork() {
        return this.connectionPoint;
    }

    public String getHost() {
        return this.config.getHost();
    }

    public long getLastPingRecv() {
        return this.lastPingReceived;
    }

    protected Map<PacketListener, ListenerWrapper> getPacketListeners() {
        return this.recvListeners;
    }

    protected Map<PacketListener, ListenerWrapper> getPacketSendingListeners() {
        return this.sendListeners;
    }

    public int getPort() {
        return this.config.getPort();
    }

    public String getServiceName() {
        return this.config.getServiceName();
    }

    protected void initDebugger() {
        if (this.config.isDebuggerEnabled() && this.debugger == null) {
            String property = null;
            try {
                property = System.getProperty("smack.debuggerClass");
            } catch (Throwable th) {
            }
            Class<?> cls = null;
            if (property != null) {
                try {
                    cls = Class.forName(property);
                } catch (Exception e) {
                    e.printStackTrace();
                    cls = null;
                }
            }
            if (cls == null) {
                this.debugger = new AndroidDebugger(this);
                return;
            }
            try {
                this.debugger = (SmackDebugger) cls.getConstructor(Connection.class, Writer.class, Reader.class).newInstance(this);
            } catch (Exception e2) {
                throw new IllegalArgumentException("Can't initialize the configured debugger!", e2);
            }
        }
    }

    public boolean isAlwaysFailed() {
        boolean z;
        synchronized (this.mCachedStatus) {
            ArrayList<Pair<Integer, Long>> arrayList = new ArrayList<>();
            for (Pair<Integer, Long> pair : this.mCachedStatus) {
                if (System.currentTimeMillis() - ((Long) pair.second).longValue() < EFFECTIVE_STATUS) {
                    arrayList.add(pair);
                }
            }
            this.mCachedStatus.clear();
            this.mCachedStatus.addAll(arrayList);
            z = this.mCachedStatus.size() >= 6;
        }
        return z;
    }

    public boolean isBinaryConnection() {
        return false;
    }

    public boolean isConnected() {
        boolean z = true;
        if (this.connectStatus != 1) {
            z = false;
        }
        return z;
    }

    public boolean isConnecting() {
        return this.connectStatus == 0;
    }

    public boolean isReadAlive(long j) {
        boolean z;
        synchronized (this) {
            z = this.readAlive >= j;
        }
        return z;
    }

    protected boolean isReconnectionAllowed() {
        return this.config.isReconnectionAllowed();
    }

    public abstract void ping(boolean z) throws XMPPException;

    public void removeConnectionListener(ConnectionListener connectionListener) {
        this.connectionListeners.remove(connectionListener);
    }

    public void removePacketListener(PacketListener packetListener) {
        this.recvListeners.remove(packetListener);
    }

    public void removePacketSendingListener(PacketListener packetListener) {
        this.sendListeners.remove(packetListener);
    }

    public void resetConnTryTimes() {
        this.connTimes = 0;
    }

    public void resetConnectTime() {
        this.connectTime = -1L;
    }

    public abstract void send(Blob blob) throws XMPPException;

    public abstract void sendPacket(Packet packet) throws XMPPException;

    public void setChallenge(String str) {
        synchronized (this) {
            if (this.connectStatus == 0) {
                MyLog.w("setChallenge hash = " + MD5.MD5_32(str).substring(0, 8));
                this.challenge = str;
                setConnectionStatus(1, 0, null);
            } else {
                MyLog.w("ignore setChallenge because connection was disconnected");
            }
        }
    }

    public void setConnectionStatus(int i, int i2, Exception exc) {
        int i3 = this.connectStatus;
        if (i != i3) {
            MyLog.w(String.format("update the connection status. %1$s -> %2$s : %3$s ", getDesc(i3), getDesc(i), PushConstants.getErrorDesc(i2)));
        }
        if (Network.hasNetwork(this.mPushService)) {
            addStatus(i);
        }
        PushConnectionStatusPlan planStatusChange = PushConnectionStatusRuntime.planStatusChange(i3, i);
        if (planStatusChange.getWarningMessage() != null) {
            MyLog.w(planStatusChange.getWarningMessage());
        }
        if (planStatusChange.getShouldRemoveConnectingTimeout()) {
            this.mPushService.removeJobs(10);
        }
        this.connectStatus = i;
        if (planStatusChange.getListenerEvent() == PushConnectionListenerEvent.ConnectionStarted) {
            Iterator<ConnectionListener> it2 = this.connectionListeners.iterator();
            while (it2.hasNext()) {
                it2.next().connectionStarted(this);
            }
            return;
        }
        if (planStatusChange.getListenerEvent() == PushConnectionListenerEvent.ReconnectionSuccessful) {
            Iterator<ConnectionListener> it = this.connectionListeners.iterator();
            while (it.hasNext()) {
                it.next().reconnectionSuccessful(this);
            }
            return;
        }
        if (planStatusChange.getListenerEvent() == PushConnectionListenerEvent.ReconnectionFailed) {
            Iterator<ConnectionListener> it3 = this.connectionListeners.iterator();
            while (it3.hasNext()) {
                it3.next().reconnectionFailed(this, exc == null ? new CancellationException("disconnect while connecting") : exc);
            }
            return;
        }
        if (planStatusChange.getListenerEvent() == PushConnectionListenerEvent.ConnectionClosed) {
            Iterator<ConnectionListener> it4 = this.connectionListeners.iterator();
            while (it4.hasNext()) {
                it4.next().connectionClosed(this, i2, exc);
            }
        }
    }

    public void setReadAlive() {
        synchronized (this) {
            this.readAlive = System.currentTimeMillis();
        }
    }

    public abstract void unbind(String str, String str2) throws XMPPException;
}
