package com.xiaomi.push.service;

import android.text.TextUtils;
import com.google.protobuf.micro.InvalidProtocolBufferMicroException;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.network.Fallback;
import com.xiaomi.network.HostManager;
import com.xiaomi.push.log.LogUploader;
import com.xiaomi.push.protobuf.ChannelMessage;
import com.xiaomi.push.service.PushClientsManager;
import com.xiaomi.push.thrift.ChannelStatsType;
import com.xiaomi.slim.Blob;
import com.xiaomi.smack.ConnectionConfiguration;
import com.xiaomi.smack.packet.CommonPacketExtension;
import com.xiaomi.smack.packet.IQ;
import com.xiaomi.smack.packet.Message;
import com.xiaomi.smack.packet.Packet;
import com.xiaomi.smack.util.TrafficUtils;
import com.xiaomi.stats.StatsHelper;
import com.xiaomi.xmsf.runtime.PushConnectionState;
import com.xiaomi.xmsf.runtime.PushRuntime;
import com.xiaomi.xmsf.runtime.PushRuntimeChannelTracker;
import java.util.Date;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/PacketSync.class */
public class PacketSync {
    private XMPushService mService;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/PacketSync$PacketReceiveHandler.class */
    public interface PacketReceiveHandler {
        boolean Handle(Blob blob);

        boolean Handle(Packet packet);
    }

    PacketSync(XMPushService xMPushService) {
        this.mService = xMPushService;
    }

    private void dispatchNetFlow(Blob blob) {
        PushClientsManager.ClientLoginInfo clientLoginInfoByChidAndUserId;
        String fullUserName = blob.getFullUserName();
        String string = Integer.toString(blob.getChannelId());
        if (TextUtils.isEmpty(fullUserName) || TextUtils.isEmpty(string) || (clientLoginInfoByChidAndUserId = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(string, fullUserName)) == null) {
            return;
        }
        try {
            TrafficUtils.distributionTraffic(this.mService, clientLoginInfoByChidAndUserId.pkgName, blob.getSerializedSize(), true, true, System.currentTimeMillis());
        } catch (Throwable th) {
            MyLog.e(th);
        }
    }

    private void dispatchNetFlow(Packet packet) {
        PushClientsManager.ClientLoginInfo clientLoginInfoByChidAndUserId;
        String to = packet.getTo();
        String channelId = packet.getChannelId();
        if (TextUtils.isEmpty(to) || TextUtils.isEmpty(channelId) || (clientLoginInfoByChidAndUserId = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(channelId, to)) == null) {
            return;
        }
        try {
            TrafficUtils.distributionTraffic(this.mService, clientLoginInfoByChidAndUserId.pkgName, TrafficUtils.getTrafficFlow(packet.toXML()), true, true, System.currentTimeMillis());
        } catch (Throwable th) {
            MyLog.e(th);
        }
    }

    private void processRedirectMessage(CommonPacketExtension commonPacketExtension) {
        PushRedirectPlan redirectPlan = PushPacketSyncRuntime.resolveRedirect(commonPacketExtension == null ? null : commonPacketExtension.getText());
        if (!redirectPlan.getShouldReconnect()) {
            return;
        }
        Fallback fallbacksByHost = HostManager.getInstance().getFallbacksByHost(ConnectionConfiguration.getXmppServerHost(), false);
        if (fallbacksByHost == null) {
            return;
        }
        fallbacksByHost.addPreferredHost(redirectPlan.getPreferredHosts().toArray(new String[0]));
        PushRuntime.observeChannelEvent(null, "server_redirect", "PacketSync.processRedirectMessage");
        PushRuntime.observeConnectionState(PushConnectionState.Disconnected, "PacketSync.processRedirectMessage", ConnectionConfiguration.getXmppServerHost(), "redirect_host_update");
        this.mService.disconnect(20, null);
        this.mService.scheduleConnect(true);
    }

    private void handleBindResult(String str, String str2, ChannelMessage.XMMsgBindResp xMMsgBindResp) {
        PushClientsManager.ClientLoginInfo clientLoginInfoByChidAndUserId = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(str, str2);
        if (clientLoginInfoByChidAndUserId == null) {
            return;
        }
        PushBindResultPlan bindResultPlan = PushPacketSyncRuntime.resolveBindResult(xMMsgBindResp.getResult(), xMMsgBindResp.getErrorType(), xMMsgBindResp.getErrorReason());
        PushRuntime.observeChannelEvent(clientLoginInfoByChidAndUserId.pkgName, bindResultPlan.getEventAction(), "PacketSync.handleBindResult");
        if (bindResultPlan.getShouldReportInvalidSig()) {
            MyLog.w("SMACK: bind error invalid-sig token = " + clientLoginInfoByChidAndUserId.token + " sec = " + clientLoginInfoByChidAndUserId.security);
            StatsHelper.stats(0, ChannelStatsType.BIND_INVALID_SIG.getValue(), 1, null, 0);
        }
        if (bindResultPlan.getShouldScheduleRebind()) {
            this.mService.scheduleRebindChannel(clientLoginInfoByChidAndUserId);
        }
        if (bindResultPlan.getClientStatus() != null && bindResultPlan.getNotifyType() != null) {
            clientLoginInfoByChidAndUserId.setStatus(bindResultPlan.getClientStatus(), bindResultPlan.getNotifyType().intValue(), bindResultPlan.getStatusReasonCode(), bindResultPlan.getStatusReasonMessage(), bindResultPlan.getStatusErrorType());
        }
        if (bindResultPlan.getShouldDeactivateClient()) {
            PushClientsManager.getInstance().deactivateClient(str, str2);
        }
        if (bindResultPlan.getRuntimeState() != null) {
            PushRuntime.observeChannelState(clientLoginInfoByChidAndUserId.pkgName, str, clientLoginInfoByChidAndUserId.userId, clientLoginInfoByChidAndUserId.session, bindResultPlan.getRuntimeState(), "PacketSync.handleBindResult", Integer.valueOf(bindResultPlan.getStatusReasonCode()), bindResultPlan.getStatusReasonMessage());
        }
        PushRuntimeChannelTracker.syncNow("PacketSync.handleBindResult");
        if (!xMMsgBindResp.getResult()) {
            MyLog.w("SMACK: channel bind failed, chid=" + str + " reason=" + xMMsgBindResp.getErrorReason());
            return;
        }
        MyLog.w("SMACK: channel bind succeeded, chid=" + str);
    }

    private void handleKick(String str, String str2, String str3, String str4) {
        PushKickPlan kickPlan = PushPacketSyncRuntime.resolveKick(str3, str4);
        MyLog.w("kicked by server, chid=" + str + " res= " + PushClientsManager.ClientLoginInfo.getResource(str2) + " type=" + str3 + " reason=" + str4);
        PushClientsManager.ClientLoginInfo clientLoginInfoByChidAndUserId = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(str, str2);
        if (clientLoginInfoByChidAndUserId != null) {
            PushRuntime.observeChannelEvent(clientLoginInfoByChidAndUserId.pkgName, kickPlan.getEventAction(), "PacketSync.handleKick");
            PushRuntime.observeChannelState(clientLoginInfoByChidAndUserId.pkgName, str, clientLoginInfoByChidAndUserId.userId, clientLoginInfoByChidAndUserId.session, kickPlan.getRuntimeState(), "PacketSync.handleKick", Integer.valueOf(kickPlan.getStatusReasonCode()), kickPlan.getStatusReasonMessage());
        }
        if (kickPlan.getShouldCloseChannel()) {
            this.mService.closeChannel(str, str2, 3, str4, str3);
            if (kickPlan.getShouldDeactivateClient()) {
                PushClientsManager.getInstance().deactivateClient(str, str2);
            }
        } else if (clientLoginInfoByChidAndUserId != null && kickPlan.getShouldScheduleRebind()) {
            this.mService.scheduleRebindChannel(clientLoginInfoByChidAndUserId);
            clientLoginInfoByChidAndUserId.setStatus(PushClientsManager.ClientStatus.unbind, 3, kickPlan.getStatusReasonCode(), kickPlan.getStatusReasonMessage(), kickPlan.getStatusErrorType());
        }
        PushRuntimeChannelTracker.syncNow("PacketSync.handleKick");
    }

    public void handleBlob(Blob blob) throws InvalidProtocolBufferMicroException {
        String cmd = blob.getCmd();
        switch (blob.getChannelId()) {
            case 0:
                if (Blob.CMD_PING.equals(cmd)) {
                    byte[] payload = blob.getPayload();
                    if (payload != null && payload.length > 0) {
                        ChannelMessage.XMMsgPing from = ChannelMessage.XMMsgPing.parseFrom(payload);
                        if (from.hasPsc()) {
                            ServiceConfig.getInstance().handle(from.getPsc());
                        }
                    }
                    if (!PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(this.mService.getPackageName())) {
                        this.mService.sendPongIfNeed();
                    }
                    if ("1".equals(blob.getPacketID())) {
                        MyLog.w("received a server ping");
                    } else {
                        StatsHelper.pingEnded();
                    }
                    this.mService.onPong();
                } else if (!Blob.CMD_SYNC.equals(cmd)) {
                    if (Blob.CMD_NOTIFY.equals(blob.getCmd())) {
                        ChannelMessage.XMMsgNotify from2 = ChannelMessage.XMMsgNotify.parseFrom(blob.getPayload());
                        MyLog.w("notify by server err = " + from2.getErrCode() + " desc = " + from2.getErrStr());
                    }
                } else if (Blob.SUBCMD_CONF.equals(blob.getSubcmd())) {
                    ServiceConfig.getInstance().handle(ChannelMessage.PushServiceConfigMsg.parseFrom(blob.getPayload()));
                } else if (TextUtils.equals("U", blob.getSubcmd())) {
                    ChannelMessage.XMMsgU from3 = ChannelMessage.XMMsgU.parseFrom(blob.getPayload());
                    LogUploader.getInstance(this.mService).upload(from3.getUrl(), from3.getToken(), new Date(from3.getStart()), new Date(from3.getEnd()), from3.getMaxlen() * 1024, from3.getForce());
                    Blob blob2 = new Blob();
                    blob2.setChannelId(0);
                    blob2.setCmd(blob.getCmd(), "UCA");
                    blob2.setPacketID(blob.getPacketID());
                    XMPushService xMPushService = this.mService;
                    xMPushService.executeJob(new SendMessageJob(xMPushService, blob2));
                } else if (TextUtils.equals("P", blob.getSubcmd())) {
                    ChannelMessage.XMMsgP from4 = ChannelMessage.XMMsgP.parseFrom(blob.getPayload());
                    Blob blob3 = new Blob();
                    blob3.setChannelId(0);
                    blob3.setCmd(blob.getCmd(), "PCA");
                    blob3.setPacketID(blob.getPacketID());
                    ChannelMessage.XMMsgP xMMsgP = new ChannelMessage.XMMsgP();
                    if (from4.hasCookie()) {
                        xMMsgP.setCookie(from4.getCookie());
                    }
                    blob3.setPayload(xMMsgP.toByteArray(), null);
                    XMPushService xMPushService2 = this.mService;
                    xMPushService2.executeJob(new SendMessageJob(xMPushService2, blob3));
                    MyLog.w("ACK msgP: id = " + blob.getPacketID());
                }
                break;
            default:
                String string = Integer.toString(blob.getChannelId());
                if (Blob.CMD_SECMSG.equals(blob.getCmd())) {
                    if (!blob.hasErr()) {
                        this.mService.getClientEventDispatcher().notifyPacketArrival(this.mService, string, blob);
                    } else {
                        MyLog.w("Recv SECMSG errCode = " + blob.getErrCode() + " errStr = " + blob.getErrStr());
                    }
                    break;
                } else if (!Blob.CMD_BIND.equals(cmd)) {
                    if (Blob.CMD_KICK.equals(cmd)) {
                        ChannelMessage.XMMsgKick from5 = ChannelMessage.XMMsgKick.parseFrom(blob.getPayload());
                        handleKick(string, blob.getFullUserName(), from5.getType(), from5.getReason());
                    }
                    break;
                } else {
                    ChannelMessage.XMMsgBindResp from6 = ChannelMessage.XMMsgBindResp.parseFrom(blob.getPayload());
                    handleBindResult(string, blob.getFullUserName(), from6);
                    break;
                }
        }
    }

    public void onBlobReceive(Blob blob) {
        if (5 != blob.getChannelId()) {
            dispatchNetFlow(blob);
        }
        try {
            handleBlob(blob);
        } catch (Exception e) {
            MyLog.e("handle Blob chid = " + blob.getChannelId() + " cmd = " + blob.getCmd() + " packetid = " + blob.getPacketID() + " failure ", e);
        }
    }

    public void onPacketReceive(Packet packet) {
        if (!"5".equals(packet.getChannelId())) {
            dispatchNetFlow(packet);
        }
        String channelId = packet.getChannelId();
        String str = channelId;
        if (TextUtils.isEmpty(channelId)) {
            str = "1";
            packet.setChannelId("1");
        }
        if (str.equals(Blob.CLIENT_PING_ID)) {
            MyLog.w("Received wrong packet with chid = 0 : " + packet.toXML());
        }
        if (packet instanceof IQ) {
            CommonPacketExtension extension = packet.getExtension("kick");
            if (extension != null) {
                handleKick(str, packet.getTo(), extension.getAttributeValue("type"), extension.getAttributeValue("reason"));
                return;
            }
        } else if (packet instanceof Message) {
            Message message = (Message) packet;
            if ("redir".equals(message.getType())) {
                CommonPacketExtension extension2 = message.getExtension("hosts");
                if (extension2 != null) {
                    processRedirectMessage(extension2);
                    return;
                }
                return;
            }
        }
        this.mService.getClientEventDispatcher().notifyPacketArrival(this.mService, str, packet);
    }
}
