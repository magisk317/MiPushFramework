package com.xiaomi.channel.commonutils.misc;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/ByteUtils.class */
public class ByteUtils {
    private ByteUtils() {
    }

    public static byte[] parseInt(int i) {
        return new byte[]{(byte) (i >> 24), (byte) (i >> 16), (byte) (i >> 8), (byte) i};
    }

    public static int toInt(byte[] bArr) {
        if (bArr.length == 4) {
            return 0 | ((bArr[0] & 255) << 24) | ((bArr[1] & 255) << 16) | ((bArr[2] & 255) << 8) | (bArr[3] & 255);
        }
        throw new IllegalArgumentException("the length of bytes must be 4");
    }
}
