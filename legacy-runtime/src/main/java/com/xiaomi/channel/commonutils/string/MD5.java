package com.xiaomi.channel.commonutils.string;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/string/MD5.class */
public class MD5 {
    private static final String HEX_PREFIX = "0";

    public static String MD5_16(String str) {
        return MD5_32(str).subSequence(8, 24).toString();
    }

    public static String MD5_32(String str) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            StringBuffer stringBuffer = new StringBuffer();
            messageDigest.update(str.getBytes(), 0, str.length());
            for (byte b : messageDigest.digest()) {
                stringBuffer.append(byte2Hex(b));
            }
            return stringBuffer.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static String byte2Hex(byte b) {
        int i = (b & 127) + (b < 0 ? 128 : 0);
        StringBuilder sb = new StringBuilder();
        sb.append(i < 16 ? HEX_PREFIX : "");
        sb.append(Integer.toHexString(i).toLowerCase());
        return sb.toString();
    }
}
