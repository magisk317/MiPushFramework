package com.xiaomi.push.service;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.string.Base64Coder;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/EncryptUtils.class */
public class EncryptUtils {
    private static final byte[] IV = {100, 23, 84, 114, 72, 0, 4, 97, 73, 97, 2, 52, 84, 102, 18, 32};

    public static String decrypt(String str, String str2) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(Base64Coder.decode(str), "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(2, secretKeySpec, new IvParameterSpec(IV));
            return new String(cipher.doFinal(Base64Coder.decode(str2)), "UTF-8");
        } catch (Exception e) {
            MyLog.e(e);
            return null;
        }
    }

    public static String encrypt(String str, String str2) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(Base64Coder.decode(str), "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(1, secretKeySpec, new IvParameterSpec(IV));
        return new String(Base64Coder.encode(cipher.doFinal(str2.getBytes("UTF-8"))));
    }
}
