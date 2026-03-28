package com.xiaomi.push.service;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.clientReport.PushClientReportManager;
import com.xiaomi.push.service.clientReport.ReportConstants;
import com.xiaomi.slim.Blob;
import com.xiaomi.smack.XMPPException;
import com.xiaomi.smack.packet.Message;
import com.xiaomi.smack.packet.Packet;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionRegistration;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import com.xiaomi.xmsf.runtime.PushRegistrationState;
import com.xiaomi.xmsf.runtime.PushRuntime;
import java.util.Collection;
import org.apache.thrift.TException;

final class XMPushServicePacketDelegate {
    private final XMPushService service;

    XMPushServicePacketDelegate(XMPushService xMPushService) {
        this.service = xMPushService;
    }

    void handleSendMessageIntent(Intent intent) {
        Blob blob;
        String stringExtra = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME);
        String stringExtra2 = intent.getStringExtra(PushConstants.EXTRA_SESSION);
        Bundle bundleExtra = intent.getBundleExtra(PushConstants.EXTRA_PACKET);
        PushClientsManager pushClientsManager = PushClientsManager.getInstance();
        if (bundleExtra != null) {
            Message message = (Message) this.service.preparePacket(new Message(bundleExtra), stringExtra, stringExtra2);
            if (message == null) {
                return;
            }
            blob = Blob.from(message, pushClientsManager.getClientLoginInfoByChidAndUserId(message.getChannelId(), message.getFrom()).security);
        } else {
            byte[] byteArrayExtra = intent.getByteArrayExtra(PushConstants.EXTRA_RAW_PACKET);
            blob = null;
            if (byteArrayExtra != null) {
                long longExtra = intent.getLongExtra(PushConstants.EXTRA_USER_ID, 0L);
                String stringExtra3 = intent.getStringExtra(PushConstants.EXTRA_USER_RES);
                String stringExtra4 = intent.getStringExtra(PushConstants.EXTRA_CHID);
                PushClientsManager.ClientLoginInfo clientLoginInfoByChidAndUserId = pushClientsManager.getClientLoginInfoByChidAndUserId(stringExtra4, Long.toString(longExtra));
                if (clientLoginInfoByChidAndUserId != null) {
                    blob = new Blob();
                    try {
                        blob.setChannelId(Integer.parseInt(stringExtra4));
                    } catch (NumberFormatException unused) {
                    }
                    blob.setCmd(Blob.CMD_SECMSG, null);
                    blob.setFrom(longExtra, "xiaomi.com", stringExtra3);
                    blob.setPacketID(intent.getStringExtra(PushConstants.EXTRA_PACKET_ID));
                    blob.setPayload(byteArrayExtra, clientLoginInfoByChidAndUserId.security);
                }
            }
        }
        if (blob != null) {
            this.service.executeJobNow(new SendMessageJob(this.service, blob));
        }
    }

    void handleBatchSendMessageIntent(Intent intent) {
        Parcelable[] parcelableArrayExtra = intent.getParcelableArrayExtra(PushConstants.EXTRA_PACKETS);
        if (parcelableArrayExtra == null || parcelableArrayExtra.length == 0) {
            return;
        }
        String stringExtra = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME);
        String stringExtra2 = intent.getStringExtra(PushConstants.EXTRA_SESSION);
        Message[] messageArr = new Message[parcelableArrayExtra.length];
        intent.getBooleanExtra(PushConstants.EXTRA_ENCYPT, true);
        for (int i = 0; i < parcelableArrayExtra.length; i++) {
            messageArr[i] = new Message((Bundle) parcelableArrayExtra[i]);
            messageArr[i] = (Message) this.service.preparePacket(messageArr[i], stringExtra, stringExtra2);
            if (messageArr[i] == null) {
                return;
            }
        }
        PushClientsManager pushClientsManager = PushClientsManager.getInstance();
        Blob[] blobArr = new Blob[messageArr.length];
        for (int i2 = 0; i2 < messageArr.length; i2++) {
            Message message = messageArr[i2];
            blobArr[i2] = Blob.from(message, pushClientsManager.getClientLoginInfoByChidAndUserId(message.getChannelId(), message.getFrom()).security);
        }
        this.service.executeJobNow(new BatchSendMessageJob(this.service, blobArr));
    }

    void handlePacketIntent(Intent intent, Packet packet) {
        Blob blobBuildBlobForPacket = PushPacketRuntime.buildBlobForPacket(packet, intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME), intent.getStringExtra(PushConstants.EXTRA_SESSION), PushClientsManager.getInstance(), this.service.isConnected());
        if (blobBuildBlobForPacket != null) {
            this.service.executeJobNow(new SendMessageJob(this.service, blobBuildBlobForPacket));
        }
    }

    void registerForMiPushApp(byte[] bArr, String str) {
        if (bArr == null) {
            PushRuntime.observeRegistrationResult(str, false, "XMPushService.registerForMiPushApp", "null_payload");
            MIPushClientManager.notifyError(this.service, str, bArr, 70000003, "null payload");
            MyLog.w("register request without payload");
            return;
        }
        XmPushActionContainer xmPushActionContainer = new XmPushActionContainer();
        try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionContainer, bArr);
            if (xmPushActionContainer.action == ActionType.Registration) {
                XmPushActionRegistration xmPushActionRegistration = new XmPushActionRegistration();
                try {
                    XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionRegistration, xmPushActionContainer.getPushAction());
                    MIPushClientManager.registerApp(xmPushActionContainer.getPackageName(), bArr);
                    PushRuntime.observeRegistrationState(xmPushActionContainer.getPackageName(), PushRegistrationState.Registering, "XMPushService.registerForMiPushApp", "register_job_enqueued");
                    this.service.executeJob(new MIPushAppRegisterJob(this.service, xmPushActionContainer.getPackageName(), xmPushActionRegistration.getAppId(), xmPushActionRegistration.getToken(), bArr));
                    PushClientReportManager.getInstance(this.service.getApplicationContext()).reportEvent(xmPushActionContainer.getPackageName(), ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID, xmPushActionRegistration.getId(), ReportConstants.REGISTER_TYPE_SEND_TO_SERVER, null);
                    return;
                } catch (TException e) {
                    MyLog.e("app register error. " + e);
                    PushRuntime.observeRegistrationResult(str, false, "XMPushService.registerForMiPushApp", "payload_action_error");
                    MIPushClientManager.notifyError(this.service, str, bArr, 70000003, " data action error.");
                    return;
                }
            }
            PushRuntime.observeRegistrationResult(str, false, "XMPushService.registerForMiPushApp", "registration_action_required");
            MIPushClientManager.notifyError(this.service, str, bArr, 70000003, " registration action required.");
            MyLog.w("register request with invalid payload");
        } catch (TException e2) {
            MyLog.e("app register fail. " + e2);
            PushRuntime.observeRegistrationResult(str, false, "XMPushService.registerForMiPushApp", "container_decode_error");
            MIPushClientManager.notifyError(this.service, str, bArr, 70000003, " data container error.");
        }
    }

    void sendMessage(final String str, final byte[] bArr, boolean z) {
        Collection<PushClientsManager.ClientLoginInfo> allClientLoginInfoByChid = PushClientsManager.getInstance().getAllClientLoginInfoByChid("5");
        PushClientsManager.ClientLoginInfo clientLoginInfo = allClientLoginInfoByChid.isEmpty() ? null : allClientLoginInfoByChid.iterator().next();
        PushServiceMiPushPayloadDispatchPlan pushServiceMiPushPayloadDispatchPlan = PushServiceIntentRuntime.decideMiPushPayloadDispatch(!allClientLoginInfoByChid.isEmpty(), clientLoginInfo != null ? clientLoginInfo.status : null, z);
        PushRuntime.observeChannelEvent(str, pushServiceMiPushPayloadDispatchPlan.getEventAction(), "XMPushService.sendMessage");
        switch (pushServiceMiPushPayloadDispatchPlan.getAction()) {
            case QueueOnly:
                MIPushClientManager.addPendingMessages(str, bArr);
                return;
            case SendNow:
                this.service.executeJob(new XMPushService.Job(4) { // from class: com.xiaomi.push.service.XMPushServicePacketDelegate.1
                    @Override // com.xiaomi.push.service.XMPushService.Job
                    public String getDesc() {
                        return "send mi push message";
                    }

                    @Override // com.xiaomi.push.service.XMPushService.Job
                    public void process() {
                        try {
                            MIPushHelper.sendPacket(XMPushServicePacketDelegate.this.service, str, bArr);
                        } catch (XMPPException e) {
                            MyLog.e(e);
                            XMPushServicePacketDelegate.this.service.disconnect(10, e);
                        }
                    }
                });
                return;
            default:
                return;
        }
    }
}
