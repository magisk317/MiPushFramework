package com.xiaomi.push.service;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.slim.Blob;
import com.xiaomi.smack.packet.CommonPacketExtension;
import com.xiaomi.smack.packet.Message;
import com.xiaomi.smack.packet.Packet;
import com.xiaomi.smack.util.TrafficUtils;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionAckMessage;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import org.apache.thrift.TBase;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/MIPushEventProcessor.class */
public class MIPushEventProcessor {
    public static XmPushActionContainer buildContainer(byte[] bArr) {
        XmPushActionContainer xmPushActionContainer = new XmPushActionContainer();
        try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionContainer, bArr);
            return xmPushActionContainer;
        } catch (Throwable th) {
            MyLog.e(th);
            return null;
        }
    }

    public static Intent buildIntent(byte[] bArr, long j) {
        XmPushActionContainer xmPushActionContainerBuildContainer = buildContainer(bArr);
        if (xmPushActionContainerBuildContainer == null) {
            return null;
        }
        Intent intent = new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE);
        intent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArr);
        intent.putExtra(PushConstants.MESSAGE_RECEIVE_TIME, Long.toString(j));
        intent.setPackage(xmPushActionContainerBuildContainer.packageName);
        return intent;
    }

    public static XmPushActionContainer constructAckMessage(Context context, XmPushActionContainer xmPushActionContainer) {
        XmPushActionAckMessage xmPushActionAckMessage = new XmPushActionAckMessage();
        xmPushActionAckMessage.setAppId(xmPushActionContainer.getAppid());
        PushMetaInfo metaInfo = xmPushActionContainer.getMetaInfo();
        if (metaInfo != null) {
            xmPushActionAckMessage.setId(metaInfo.getId());
            xmPushActionAckMessage.setMessageTs(metaInfo.getMessageTs());
            if (!TextUtils.isEmpty(metaInfo.getTopic())) {
                xmPushActionAckMessage.setTopic(metaInfo.getTopic());
            }
        }
        xmPushActionAckMessage.setDeviceStatus(XmPushThriftSerializeUtils.getDeviceStatus(context, xmPushActionContainer));
        XmPushActionContainer xmPushActionContainerGenerateRequestContainer = MIPushHelper.generateRequestContainer(xmPushActionContainer.getPackageName(), xmPushActionContainer.getAppid(), xmPushActionAckMessage, ActionType.AckMessage);
        PushMetaInfo pushMetaInfoDeepCopy = xmPushActionContainer.getMetaInfo().deepCopy();
        pushMetaInfoDeepCopy.putToExtra(PushConstants.MESSAGE_ACK_TIME, Long.toString(System.currentTimeMillis()));
        xmPushActionContainerGenerateRequestContainer.setMetaInfo(pushMetaInfoDeepCopy);
        return xmPushActionContainerGenerateRequestContainer;
    }

    public static void postProcessMIPushMessage(XMPushService xMPushService, String str, byte[] bArr, Intent intent) throws Throwable {
        MIPushEventProcessorSupport.postProcessMIPushMessage(xMPushService, str, bArr, intent);
    }

    private static void processMIPushMessage(XMPushService xMPushService, byte[] bArr, long j) {
        MIPushEventProcessorSupport.processMIPushMessage(xMPushService, bArr, j);
    }

    public void processChannelOpenResult(Context context, PushClientsManager.ClientLoginInfo clientLoginInfo, boolean z, int i, String str) {
        MIPushAccount mIPushAccount;
        if (z || (mIPushAccount = PushAccountRuntime.loadAccount(context, "MIPushEventProcessor.processChannelOpenResult")) == null || !"token-expired".equals(str)) {
            return;
        }
        PushAccountRuntime.registerAccount(context, mIPushAccount.packageName, mIPushAccount.appId, mIPushAccount.appToken, "MIPushEventProcessor.processChannelOpenResult");
    }

    public void processNewPacket(XMPushService xMPushService, Blob blob, PushClientsManager.ClientLoginInfo clientLoginInfo) {
        try {
            processMIPushMessage(xMPushService, blob.getDecryptedPayload(clientLoginInfo.security), blob.getSerializedSize());
        } catch (IllegalArgumentException e) {
            MyLog.e(e);
        }
    }

    public void processNewPacket(XMPushService xMPushService, Packet packet, PushClientsManager.ClientLoginInfo clientLoginInfo) {
        if (!(packet instanceof Message)) {
            MyLog.w("not a mipush message");
            return;
        }
        Message message = (Message) packet;
        CommonPacketExtension extension = message.getExtension("s");
        if (extension != null) {
            try {
                processMIPushMessage(xMPushService, RC4Cryption.decrypt(RC4Cryption.generateKeyForRC4(clientLoginInfo.security, message.getPacketID()), extension.getText()), TrafficUtils.getTrafficFlow(packet.toXML()));
            } catch (IllegalArgumentException e) {
                MyLog.e(e);
            }
        }
    }
}
