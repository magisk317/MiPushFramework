package com.xiaomi.push.service;

import android.text.TextUtils;
import com.xiaomi.channel.commonutils.string.XMStringUtils;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/PacketHelper.class */
public class PacketHelper {
    private static long sCurMsgId = 0;
    private static String prefix = "";

    public static String generatePacketID() {
        if (TextUtils.isEmpty(prefix)) {
            prefix = XMStringUtils.generateRandomString(4);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        long j = sCurMsgId;
        sCurMsgId = 1 + j;
        sb.append(j);
        return sb.toString();
    }
}
