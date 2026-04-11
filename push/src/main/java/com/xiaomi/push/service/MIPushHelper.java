package com.xiaomi.push.service;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.DeviceInfo;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.msa.MsaIdManager;
import com.xiaomi.push.clientreport.PerfMessageHelper;
import com.xiaomi.push.service.PushClientsManager;
import com.xiaomi.push.service.Sync;
import com.xiaomi.slim.Blob;
import com.xiaomi.smack.Connection;
import com.xiaomi.smack.XMPPException;
import com.xiaomi.smack.packet.Packet;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.Target;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.nio.ByteBuffer;
import java.util.HashMap;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/MIPushHelper.class */
final class MIPushHelper {
    static final int MIPUSH_CHANNEL_ID = 5;
    private static final String SYNC_GROUP_MSAID = "MSAID";
    private static final String SYNC_KEY_MSAID = "msaid";

    MIPushHelper() {
    }

    static Blob constructBlob(MIPushAccount mIPushAccount, Context context, XmPushActionContainer xmPushActionContainer) {
        try {
            Blob blob = new Blob();
            blob.setChannelId(5);
            blob.setFrom(mIPushAccount.account);
            blob.setPackageName(getSourcePkgName(xmPushActionContainer));
            blob.setCmd(Blob.CMD_SECMSG, "message");
            String str = mIPushAccount.account;
            xmPushActionContainer.target.userId = str.substring(0, str.indexOf("@"));
            xmPushActionContainer.target.resource = str.substring(str.indexOf("/") + 1);
            blob.setPayload(XmPushThriftSerializeUtils.convertThriftObjectToBytes(xmPushActionContainer), mIPushAccount.security);
            blob.setPayloadType((short) 1);
            MyLog.w("try send mi push message. packagename:" + xmPushActionContainer.packageName + " action:" + xmPushActionContainer.action);
            return blob;
        } catch (NullPointerException e) {
            MyLog.e(e);
            return null;
        }
    }

    static Blob constructBlob(XMPushService xMPushService, byte[] bArr) {
        XmPushActionContainer xmPushActionContainer = new XmPushActionContainer();
        try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionContainer, bArr);
            return constructBlob(PushAccountRuntime.loadAccount(xMPushService, "MIPushHelper.constructBlobPayload"), xMPushService, xmPushActionContainer);
        } catch (TException e) {
            MyLog.e(e);
            return null;
        }
    }

    static <T extends TBase<T, ?>> XmPushActionContainer constructResponseContainer(String str, String str2, T t, ActionType actionType) {
        return generateContainer(str, str2, t, actionType, false);
    }

    static XmPushActionContainer contructAppAbsentMessage(String str, String str2) {
        XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
        xmPushActionNotification.setAppId(str2);
        xmPushActionNotification.setType("package uninstalled");
        xmPushActionNotification.setId(Packet.nextID());
        xmPushActionNotification.setRequireAck(false);
        return generateRequestContainer(str, str2, xmPushActionNotification, ActionType.Notification);
    }

    private static <T extends TBase<T, ?>> XmPushActionContainer generateContainer(String str, String str2, T t, ActionType actionType, boolean z) {
        byte[] bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(t);
        XmPushActionContainer xmPushActionContainer = new XmPushActionContainer();
        Target target = new Target();
        target.channelId = 5L;
        target.userId = "fakeid";
        xmPushActionContainer.setTarget(target);
        xmPushActionContainer.setPushAction(ByteBuffer.wrap(bArrConvertThriftObjectToBytes));
        xmPushActionContainer.setAction(actionType);
        xmPushActionContainer.setIsRequest(z);
        xmPushActionContainer.setPackageName(str);
        xmPushActionContainer.setEncryptAction(false);
        xmPushActionContainer.setAppid(str2);
        return xmPushActionContainer;
    }

    static <T extends TBase<T, ?>> XmPushActionContainer generateRequestContainer(String str, String str2, T t, ActionType actionType) {
        return generateContainer(str, str2, t, actionType, true);
    }

    static String getReceiverPermission(String str) {
        return str + ".permission.MIPUSH_RECEIVE";
    }

    private static String getSourcePkgName(XmPushActionContainer xmPushActionContainer) {
        if (xmPushActionContainer.metaInfo != null && xmPushActionContainer.metaInfo.internal != null) {
            String str = xmPushActionContainer.metaInfo.internal.get(PushConstants.EXTRA_TRAFFIC_SOURCE_PKG);
            if (!TextUtils.isEmpty(str)) {
                return str;
            }
        }
        return xmPushActionContainer.packageName;
    }

    static void prepareClientLoginInfo(final XMPushService xMPushService, PushClientsManager.ClientLoginInfo clientLoginInfo) {
        clientLoginInfo.watch(null);
        clientLoginInfo.addClientStatusListener(new PushClientsManager.ClientLoginInfo.ClientStatusListener() { // from class: com.xiaomi.push.service.MIPushHelper.3
            @Override // com.xiaomi.push.service.PushClientsManager.ClientLoginInfo.ClientStatusListener
            public void onChange(PushClientsManager.ClientStatus clientStatus, PushClientsManager.ClientStatus clientStatus2, int i) {
                if (clientStatus2 == PushClientsManager.ClientStatus.binded) {
                    MIPushClientManager.processPendingRegistrationRequest(xMPushService);
                    MIPushClientManager.processPendingMessages(xMPushService);
                } else if (clientStatus2 == PushClientsManager.ClientStatus.unbind) {
                    MIPushClientManager.notifyRegisterError(xMPushService, 70000001, " the push is not connected.");
                }
            }
        });
    }

    static void prepareMIPushAccount(final XMPushService xMPushService) {
        final MIPushAccount mIPushAccount = PushAccountRuntime.loadAccount(xMPushService, "MIPushHelper.prepareMIPushAccount");
        if (mIPushAccount != null) {
            PushAccountRuntime.attachAccountClient(xMPushService, mIPushAccount, "MIPushHelper.prepareMIPushAccount");
            Sync.getInstance(xMPushService).schedSync(new Sync.SyncJob("GAID", 172800L) { // from class: com.xiaomi.push.service.MIPushHelper.1
                @Override // com.xiaomi.push.service.Sync.SyncJob
                public void sync(Sync sync) {
                    String string = sync.getString("GAID", "gaid");
                    String gaid = DeviceInfo.getGaid(xMPushService);
                    MyLog.v("gaid :" + gaid);
                    if (TextUtils.isEmpty(gaid) || TextUtils.equals(string, gaid)) {
                        return;
                    }
                    sync.put("GAID", "gaid", gaid);
                    XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
                    xmPushActionNotification.setAppId(mIPushAccount.appId);
                    xmPushActionNotification.setType(NotificationType.ClientInfoUpdate.value);
                    xmPushActionNotification.setId(PacketHelper.generatePacketID());
                    xmPushActionNotification.setExtra(new HashMap<>());
                    xmPushActionNotification.getExtra().put("gaid", gaid);
                    byte[] bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(MIPushHelper.generateRequestContainer(xMPushService.getPackageName(), mIPushAccount.appId, xmPushActionNotification, ActionType.Notification));
                    XMPushService xMPushService2 = xMPushService;
                    xMPushService2.sendMessage(xMPushService2.getPackageName(), bArrConvertThriftObjectToBytes, true);
                }
            });
            syncMsaid(xMPushService, mIPushAccount, 172800);
        }
    }

    static void sendPacket(XMPushService xMPushService, XmPushActionContainer xmPushActionContainer) throws XMPPException {
        PerfMessageHelper.collectUpStream(xmPushActionContainer.getPackageName(), xMPushService.getApplicationContext(), xmPushActionContainer, -1);
        Connection currentConnection = xMPushService.getCurrentConnection();
        if (currentConnection == null) {
            throw new XMPPException("try send msg while connection is null.");
        }
        if (!currentConnection.isBinaryConnection()) {
            throw new XMPPException("Don't support XMPP connection.");
        }
        Blob blobConstructBlob = constructBlob(PushAccountRuntime.loadAccount(xMPushService, "MIPushHelper.sendPacketContainer"), xMPushService, xmPushActionContainer);
        if (blobConstructBlob != null) {
            currentConnection.send(blobConstructBlob);
        }
    }

    static void sendPacket(XMPushService xMPushService, String str, byte[] bArr) throws XMPPException {
        PerfMessageHelper.collectUpStream(str, xMPushService.getApplicationContext(), bArr);
        Connection currentConnection = xMPushService.getCurrentConnection();
        if (currentConnection == null) {
            throw new XMPPException("try send msg while connection is null.");
        }
        if (!currentConnection.isBinaryConnection()) {
            throw new XMPPException("Don't support XMPP connection.");
        }
        Blob blobConstructBlob = constructBlob(xMPushService, bArr);
        if (blobConstructBlob != null) {
            currentConnection.send(blobConstructBlob);
        } else {
            MIPushClientManager.notifyError(xMPushService, str, bArr, 70000003, "not a valid message");
        }
    }

    private static void syncMsaid(final XMPushService xMPushService, final MIPushAccount mIPushAccount, int i) {
        Sync.getInstance(xMPushService).schedSync(new Sync.SyncJob(SYNC_GROUP_MSAID, i) { // from class: com.xiaomi.push.service.MIPushHelper.2
            @Override // com.xiaomi.push.service.Sync.SyncJob
            public void sync(Sync sync) {
                MsaIdManager msaIdManager = MsaIdManager.getInstance(xMPushService);
                String string = sync.getString(MIPushHelper.SYNC_GROUP_MSAID, MIPushHelper.SYNC_KEY_MSAID);
                String str = msaIdManager.getUDID() + msaIdManager.getOAID() + msaIdManager.getVAID() + msaIdManager.getAAID();
                if (TextUtils.isEmpty(str) || TextUtils.equals(string, str)) {
                    return;
                }
                sync.put(MIPushHelper.SYNC_GROUP_MSAID, MIPushHelper.SYNC_KEY_MSAID, str);
                XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
                xmPushActionNotification.setAppId(mIPushAccount.appId);
                xmPushActionNotification.setType(NotificationType.ClientInfoUpdate.value);
                xmPushActionNotification.setId(PacketHelper.generatePacketID());
                xmPushActionNotification.setExtra(new HashMap<>());
                msaIdManager.fillData(xmPushActionNotification.getExtra());
                byte[] bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(MIPushHelper.generateRequestContainer(xMPushService.getPackageName(), mIPushAccount.appId, xmPushActionNotification, ActionType.Notification));
                XMPushService xMPushService2 = xMPushService;
                xMPushService2.sendMessage(xMPushService2.getPackageName(), bArrConvertThriftObjectToBytes, true);
            }
        });
    }
}
