package com.xiaomi.smack;

import android.os.SystemClock;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.mipush.sdk.Constants;
import com.xiaomi.network.Fallback;
import com.xiaomi.network.HostManager;
import com.xiaomi.push.service.PushSocketFailurePlan;
import com.xiaomi.push.service.PushSocketHostSelectionPlan;
import com.xiaomi.push.service.PushShortConnectionPlan;
import com.xiaomi.push.service.PushSocketConnectionRuntime;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.slim.Blob;
import com.xiaomi.smack.packet.Packet;
import com.xiaomi.smack.util.TaskExecutor;
import com.xiaomi.stats.StatsHelper;
import com.xiaomi.xmsf.runtime.PushRuntime;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/SocketConnection.class */
public abstract class SocketConnection extends Connection {
    private static final int CONNECTION_TIMEOUT = 8000;
    private static final int MAX_SHORT_CONN_COUNT = 2;
    private static final int SHORT_CONNECTION_THRESHOLD = 300000;
    private String connectedHost;
    String connectionID;
    private int curShortConnCount;
    protected Exception failedException;
    protected volatile long lastConnectedTime;
    protected volatile long lastPingReceived;
    protected volatile long lastPingSent;
    protected XMPushService pushService;
    protected Socket socket;

    public SocketConnection(XMPushService xMPushService, ConnectionConfiguration connectionConfiguration) {
        super(xMPushService, connectionConfiguration);
        this.failedException = null;
        this.connectionID = null;
        this.lastPingSent = 0L;
        this.lastPingReceived = 0L;
        this.lastConnectedTime = 0L;
        this.pushService = xMPushService;
    }

    private void connectDirectly(String str, int i) throws Throwable {
        this.failedException = null;
        StringBuilder sb = new StringBuilder();
        Integer numPs = MyLog.ps("get bucket for host : " + str);
        Fallback fallback = getFallback(str);
        MyLog.pe(numPs);
        ArrayList<String> arrayList = fallback != null ? fallback.getHosts(true) : new ArrayList<>();
        PushSocketHostSelectionPlan hostSelectionPlan = PushSocketConnectionRuntime.resolveCandidateHosts(str, arrayList);
        PushRuntime.observeChannelEvent(null, hostSelectionPlan.getEventAction(), "SocketConnection.connectDirectly");
        this.lastConnectedTime = 0L;
        String activeConnPoint = Network.getActiveConnPoint(this.pushService);
        boolean z = false;
        for (String str2 : hostSelectionPlan.getCandidateHosts()) {
            long jCurrentTimeMillis = System.currentTimeMillis();
            this.connTimes++;
            try {
                MyLog.w("begin to connect to " + str2);
                this.socket = createSocket();
                this.socket.connect(com.xiaomi.network.Host.from(str2, i), 8000);
                MyLog.w("tcp connected");
                this.socket.setTcpNoDelay(true);
                this.connectedHost = str2;
                initConnection();
                long jCurrentTimeMillis2 = System.currentTimeMillis();
                this.connectTime = jCurrentTimeMillis2 - jCurrentTimeMillis;
                this.connectionPoint = activeConnPoint;
                if (fallback != null) {
                    fallback.succeedHost(str2, this.connectTime, 0L);
                }
                this.lastConnectedTime = SystemClock.elapsedRealtime();
                MyLog.w("connected to " + str2 + " in " + this.connectTime);
                z = true;
                break;
            } catch (Exception e) {
                this.failedException = e;
                try {
                    Socket socket = this.socket;
                    if (socket != null) {
                        socket.close();
                    }
                } catch (Exception e2) {
                }
                MyLog.e("SMACK: Could not connect to:" + str2);
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("SMACK: Could not connect to ");
                sb.append(str2);
                sb.append(" port:");
                sb.append(i);
                sb.append(" err:");
                sb.append(this.failedException.getClass().getSimpleName());
                StatsHelper.connectFail(str2, this.failedException);
                if (fallback != null) {
                    fallback.failedHost(str2, System.currentTimeMillis() - jCurrentTimeMillis, 0L, this.failedException);
                }
                PushSocketFailurePlan failurePlan = PushSocketConnectionRuntime.planFailureRetry(activeConnPoint, Network.getActiveConnPoint(this.pushService));
                PushRuntime.observeChannelEvent(null, failurePlan.getEventAction(), "SocketConnection.connectDirectly");
                if (!failurePlan.getShouldContinue()) {
                    break;
                }
            }
        }
        HostManager.getInstance().persist();
        if (z) {
            return;
        }
        throw new XMPPException(sb.toString());
    }

    private void connectUsingConfiguration(ConnectionConfiguration connectionConfiguration) throws IOException {
        try {
            connectDirectly(connectionConfiguration.getHost(), connectionConfiguration.getPort());
        } catch (IOException e) {
            throw e;
        } catch (Throwable th) {
            if (th instanceof RuntimeException) {
                throw (RuntimeException) th;
            }
            throw new IOException(th);
        }
    }

    @Override // com.xiaomi.smack.Connection
    public void batchSend(Blob[] blobArr) throws XMPPException {
        throw new XMPPException("Don't support send Blob");
    }

    @Override // com.xiaomi.smack.Connection
    public void batchSendPacket(Packet[] packetArr) throws XMPPException {
        for (Packet packet : packetArr) {
            sendPacket(packet);
        }
    }

    @Override // com.xiaomi.smack.Connection
    public void connect() throws XMPPException {
        synchronized (this) {
            try {
                if (!isConnected() && !isConnecting()) {
                    setConnectionStatus(0, 0, null);
                    connectUsingConfiguration(this.config);
                    return;
                }
                MyLog.w("WARNING: current xmpp has connected");
            } catch (IOException e) {
                throw new XMPPException(e);
            }
        }
    }

    public Socket createSocket() {
        return new Socket();
    }

    @Override // com.xiaomi.smack.Connection
    public void disconnect(int i, Exception exc) {
        shutdown(i, exc);
        if ((exc != null || i == 18) && this.lastConnectedTime != 0) {
            sinkdownHost(exc);
        }
    }

    public String getChallenge() {
        return this.challenge;
    }

    Fallback getFallback(final String str) {
        Fallback fallbacksByHost = HostManager.getInstance().getFallbacksByHost(str, false);
        if (!fallbacksByHost.isEffective()) {
            TaskExecutor.execute(new Runnable() { // from class: com.xiaomi.smack.SocketConnection.3
                @Override // java.lang.Runnable
                public void run() {
                    HostManager.getInstance().getFallbacksByHost(str, true);
                }
            });
        }
        return fallbacksByHost;
    }

    @Override // com.xiaomi.smack.Connection
    public String getHost() {
        return this.connectedHost;
    }

    protected void initConnection() throws XMPPException, IOException {
        synchronized (this) {
        }
    }

    public void notifyConnectionError(final int i, final Exception exc) {
        this.pushService.executeJob(new XMPushService.Job(2) { // from class: com.xiaomi.smack.SocketConnection.2
            @Override // com.xiaomi.push.service.XMPushService.Job
            public String getDesc() {
                return "shutdown the connection. " + i + ", " + exc;
            }

            @Override // com.xiaomi.push.service.XMPushService.Job
            public void process() {
                SocketConnection.this.pushService.disconnect(i, exc);
            }
        });
    }

    @Override // com.xiaomi.smack.Connection
    public void ping(boolean z) throws XMPPException {
        final long jCurrentTimeMillis = System.currentTimeMillis();
        sendPing(z);
        if (z) {
            return;
        }
        this.pushService.executeJobDelayed(new XMPushService.Job(13) { // from class: com.xiaomi.smack.SocketConnection.1
            @Override // com.xiaomi.push.service.XMPushService.Job
            public String getDesc() {
                return "check the ping-pong." + jCurrentTimeMillis;
            }

            @Override // com.xiaomi.push.service.XMPushService.Job
            public void process() {
                Thread.yield();
                if (!SocketConnection.this.isConnected() || SocketConnection.this.isReadAlive(jCurrentTimeMillis)) {
                    return;
                }
                SocketConnection.this.pushService.disconnect(22, null);
            }
        }, 10000L);
    }

    protected abstract void sendPing(boolean z) throws XMPPException;

    protected void shutdown(int i, Exception exc) {
        synchronized (this) {
            if (getConnectionStatus() == 2) {
                return;
            }
            setConnectionStatus(2, i, exc);
            this.challenge = "";
            try {
                this.socket.close();
            } catch (Throwable th) {
            }
            this.lastPingSent = 0L;
            this.lastPingReceived = 0L;
        }
    }

    protected void sinkdownHost(Exception exc) {
        PushShortConnectionPlan shortConnectionPlan = PushSocketConnectionRuntime.evaluateShortConnection(SystemClock.elapsedRealtime(), this.lastConnectedTime, Network.hasNetwork(this.pushService), this.curShortConnCount, Constants.ASSEMBLE_PUSH_NETWORK_INTERVAL, 2);
        this.curShortConnCount = shortConnectionPlan.getNextShortConnCount();
        PushRuntime.observeChannelEvent(null, shortConnectionPlan.getEventAction(), "SocketConnection.sinkdownHost");
        if (shortConnectionPlan.getShouldSinkDown()) {
            String host = getHost();
            MyLog.w("max short conn time reached, sink down current host:" + host);
            sinkdownHost(host, 0L, exc);
        }
    }

    protected void sinkdownHost(String str, long j, Exception exc) {
        Fallback fallbacksByHost = HostManager.getInstance().getFallbacksByHost(ConnectionConfiguration.getXmppServerHost(), false);
        if (fallbacksByHost != null) {
            fallbacksByHost.failedHost(str, j, 0L, exc);
            HostManager.getInstance().persist();
        }
    }

    public void updateLastReceived() {
        this.lastPingReceived = SystemClock.elapsedRealtime();
    }

    public void updateLastSent() {
        this.lastPingSent = SystemClock.elapsedRealtime();
    }
}
