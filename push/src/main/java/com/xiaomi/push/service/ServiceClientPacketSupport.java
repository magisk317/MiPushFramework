package com.xiaomi.push.service;

import android.os.Bundle;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.profile.MessageProfiling;
import com.xiaomi.smack.packet.CommonPacketExtension;

final class ServiceClientPacketSupport {
    private ServiceClientPacketSupport() {
    }

    static Bundle[] buildMessageBundles(com.xiaomi.smack.packet.Message[] messageArr) {
        Bundle[] bundleArr = new Bundle[messageArr.length];
        for (int i = 0; i < messageArr.length; i++) {
            attachProfiling(messageArr[i]);
            MyLog.v("SEND:" + messageArr[i].toXML());
            bundleArr[i] = messageArr[i].toBundle();
        }
        return bundleArr;
    }

    static Bundle buildMessageBundle(com.xiaomi.smack.packet.Message message) {
        attachProfiling(message);
        MyLog.v("SEND:" + message.toXML());
        return message.toBundle();
    }

    static Bundle buildPacketBundle(com.xiaomi.smack.packet.Packet packet) {
        Bundle bundle = packet.toBundle();
        if (bundle != null) {
            MyLog.v("SEND:" + packet.toXML());
        }
        return bundle;
    }

    private static void attachProfiling(com.xiaomi.smack.packet.Message message) {
        String prefString = MessageProfiling.getPrefString();
        if (TextUtils.isEmpty(prefString)) {
            return;
        }
        String[] strArr = (String[]) null;
        CommonPacketExtension commonPacketExtension = new CommonPacketExtension("pf", (String) null, strArr, strArr);
        CommonPacketExtension commonPacketExtension2 = new CommonPacketExtension("sent", (String) null, strArr, strArr);
        commonPacketExtension2.setText(prefString);
        commonPacketExtension.appendChild(commonPacketExtension2);
        message.addExtension(commonPacketExtension);
    }
}
