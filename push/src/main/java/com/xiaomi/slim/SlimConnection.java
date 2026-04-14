package com.xiaomi.slim;

import android.text.TextUtils;
import com.google.protobuf.micro.ByteStringMicro;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.mpcd.Constants;
import com.xiaomi.push.protobuf.ChannelMessage;
import com.xiaomi.push.service.PushClientsManager;
import com.xiaomi.push.service.PushSlimInboundAction;
import com.xiaomi.push.service.PushSlimInboundPlan;
import com.xiaomi.push.service.PushSlimPingPlan;
import io.github.magisk317.mipush.service.runtime.PushSlimConnectionRuntime;
import io.github.magisk317.mipush.service.runtime.PushSocketConnectionRuntime;
import com.xiaomi.push.service.ServiceConfig;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.smack.Connection;
import com.xiaomi.smack.ConnectionConfiguration;
import com.xiaomi.smack.SocketConnection;
import com.xiaomi.smack.XMPPException;
import com.xiaomi.smack.packet.Packet;
import com.xiaomi.smack.util.TrafficUtils;
import com.xiaomi.stats.StatsHelper;
import io.github.magisk317.mipush.runtime.PushRuntime;
import java.io.IOException;
import java.util.Iterator;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/slim/SlimConnection.class */
public class SlimConnection extends SocketConnection {
    private byte[] mKey;
    private BlobReader mReader;
    private Thread mReaderThread;
    private BlobWriter mWriter;

    public SlimConnection(XMPushService xMPushService, ConnectionConfiguration connectionConfiguration) {
        super(xMPushService, connectionConfiguration);
    }

    private Blob getPing(boolean z) {
        Ping ping = new Ping();
        if (z) {
            ping.setPacketID("1");
        }
        byte[] bArrRetriveStatsAsByte = StatsHelper.retriveStatsAsByte();
        if (bArrRetriveStatsAsByte != null) {
            ChannelMessage.XMMsgPing xMMsgPing = new ChannelMessage.XMMsgPing();
            xMMsgPing.setStats(ByteStringMicro.copyFrom(bArrRetriveStatsAsByte));
            ping.setPayload(xMMsgPing.toByteArray(), null);
        }
        return ping;
    }

    private void initReaderAndWriter() throws XMPPException {
        try {
            this.mReader = new BlobReader(this.socket.getInputStream(), this);
            this.mWriter = new BlobWriter(this.socket.getOutputStream(), this);
            Thread thread = new Thread("Blob Reader (" + this.connectionCounterValue + Constants.SEPARATOR_RIGHT_PARENTESIS) { // from class: com.xiaomi.slim.SlimConnection.1
                @Override // java.lang.Thread, java.lang.Runnable
                public void run() {
                    try {
                        SlimConnection.this.mReader.start();
                    } catch (Exception e) {
                        SlimConnection.this.notifyConnectionError(9, e);
                    }
                }
            };
            this.mReaderThread = thread;
            thread.start();
        } catch (Exception e) {
            throw new XMPPException("Error to init reader and writer", e);
        }
    }

    @Override // com.xiaomi.smack.SocketConnection, com.xiaomi.smack.Connection
    public void batchSend(Blob[] blobArr) {
        for (Blob blob : blobArr) {
            send(blob);
        }
    }

    @Override // com.xiaomi.smack.SocketConnection, com.xiaomi.smack.Connection
    @Deprecated
    public void batchSendPacket(Packet[] packetArr) {
        for (Packet packet : packetArr) {
            sendPacket(packet);
        }
    }

    @Override // com.xiaomi.smack.Connection
    public void bind(PushClientsManager.ClientLoginInfo clientLoginInfo) throws XMPPException {
        synchronized (this) {
            Binder.bind(clientLoginInfo, getChallenge(), this);
        }
    }

    byte[] getKey() {
        byte[] bArr;
        synchronized (this) {
            if (this.mKey == null && !TextUtils.isEmpty(this.challenge)) {
                this.mKey = PushSocketConnectionRuntime.deriveConnectionKey(this.challenge, ServiceConfig.getDeviceUUID());
                if (this.mKey != null) {
                    PushRuntime.observeChannelEvent(null, "slim_key_derived", "SlimConnection.getKey");
                }
            }
            bArr = this.mKey;
        }
        return bArr;
    }

    @Override // com.xiaomi.smack.SocketConnection
    protected void initConnection() throws XMPPException, IOException {
        synchronized (this) {
            initReaderAndWriter();
            this.mWriter.openStream();
        }
    }

    @Override // com.xiaomi.smack.Connection
    public boolean isBinaryConnection() {
        return true;
    }

    void notifyDataArrived(Blob blob) {
        if (blob == null) {
            return;
        }
        if (blob.hasErr()) {
            MyLog.w("[Slim] RCV blob chid=" + blob.getChannelId() + "; id=" + blob.getPacketID() + "; errCode=" + blob.getErrCode() + "; err=" + blob.getErrStr());
        }
        PushSlimInboundPlan inboundPlan = PushSlimConnectionRuntime.planInboundBlob(blob.getChannelId(), blob.getCmd());
        if (inboundPlan.getAction() == PushSlimInboundAction.PingReceived) {
            MyLog.w("[Slim] RCV ping id=" + blob.getPacketID());
        }
        if (inboundPlan.getShouldUpdateLastReceived()) {
            updateLastReceived();
        }
        if (inboundPlan.getEventAction() != null) {
            PushRuntime.observeChannelEvent(null, inboundPlan.getEventAction(), "SlimConnection.notifyDataArrived");
        }
        if (inboundPlan.getConnectionState() != null) {
            com.xiaomi.xmsf.runtime.PushRuntime.observeConnectionState(inboundPlan.getConnectionState(), "SlimConnection.notifyDataArrived", getHost(), inboundPlan.getConnectionReason(), System.currentTimeMillis());
        }
        if (inboundPlan.getDisconnectReasonCode() != null) {
            notifyConnectionError(inboundPlan.getDisconnectReasonCode().intValue(), null);
        }
        Iterator<Connection.ListenerWrapper> it = this.recvListeners.values().iterator();
        while (it.hasNext()) {
            it.next().notifyListener(blob);
        }
    }

    void notifyDataArrived(Packet packet) {
        if (packet == null) {
            return;
        }
        Iterator<Connection.ListenerWrapper> it = this.recvListeners.values().iterator();
        while (it.hasNext()) {
            it.next().notifyListener(packet);
        }
    }

    @Override // com.xiaomi.smack.Connection
    public void send(Blob blob) {
        BlobWriter blobWriter = this.mWriter;
        if (blobWriter == null) {
            throw new IllegalStateException("the writer is null.");
        }
        try {
            int iWrite = blobWriter.write(blob);
            this.writeAlive = System.currentTimeMillis();
            String packageName = blob.getPackageName();
            if (!TextUtils.isEmpty(packageName)) {
                try {
                    TrafficUtils.distributionTraffic(this.mPushService, packageName, iWrite, false, true, System.currentTimeMillis());
                } catch (Throwable th) {
                    MyLog.e(th);
                }
            }
            Iterator<Connection.ListenerWrapper> it = this.sendListeners.values().iterator();
            while (it.hasNext()) {
                it.next().notifyListener(blob);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to send blob", e);
        }
    }

    @Override // com.xiaomi.smack.Connection
    @Deprecated
    public void sendPacket(Packet packet) {
        send(Blob.from(packet, null));
    }

    @Override // com.xiaomi.smack.SocketConnection
    protected void sendPing(boolean z) {
        if (this.mWriter == null) {
            throw new IllegalStateException("The BlobWriter is null.");
        }
        Blob ping = getPing(z);
        PushSlimPingPlan pingPlan = PushSlimConnectionRuntime.planSendPing();
        MyLog.w("[Slim] SND ping id=" + ping.getPacketID());
        PushRuntime.observeChannelEvent(null, pingPlan.getEventAction(), "SlimConnection.sendPing");
        send(ping);
        updateLastSent();
    }

    @Override // com.xiaomi.smack.SocketConnection
    protected void shutdown(int i, Exception exc) {
        synchronized (this) {
            BlobReader blobReader = this.mReader;
            if (blobReader != null) {
                blobReader.shutdown();
                this.mReader = null;
            }
            BlobWriter blobWriter = this.mWriter;
            if (blobWriter != null) {
                try {
                    blobWriter.shutdown();
                } catch (Exception e) {
                    MyLog.e(e);
                }
                this.mWriter = null;
                this.mKey = null;
                super.shutdown(i, exc);
            } else {
                this.mKey = null;
                super.shutdown(i, exc);
            }
        }
    }

    @Override // com.xiaomi.smack.Connection
    public void unbind(String str, String str2) throws XMPPException {
        synchronized (this) {
            Binder.unbind(str, str2, this);
        }
    }
}
