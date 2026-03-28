package com.xiaomi.channel.commonutils.misc;

import android.content.Context;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/DebugUtils.class */
public class DebugUtils {
    static final char[] HEXCHARS = "0123456789ABCDEF".toCharArray();

    public static String bytes2Hex(byte[] bArr, int i, int i2) {
        StringBuilder sb = new StringBuilder(i2 * 2);
        for (int i3 = 0; i3 < i2; i3++) {
            int i4 = bArr[i + i3] & 255;
            char[] cArr = HEXCHARS;
            sb.append(cArr[i4 >> 4]);
            sb.append(cArr[i4 & 15]);
        }
        return sb.toString();
    }

    public static boolean isTesting(Context context) {
        return DebugSwitch.sDebugServerHost;
    }
}
