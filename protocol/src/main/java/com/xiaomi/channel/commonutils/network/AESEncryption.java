package com.xiaomi.channel.commonutils.network;

import com.xiaomi.channel.commonutils.string.UrlBase64Coder;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/network/AESEncryption.class */
public class AESEncryption {
    private static final String HEX_PREFIX = "0";

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/network/AESEncryption$AESDecodeException.class */
    public static class AESDecodeException extends Exception {
        private static final long serialVersionUID = 8671822568355001199L;

        public AESDecodeException(String str) {
            super(str);
        }

        public AESDecodeException(String str, Exception exc) {
            super(str, exc);
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/network/AESEncryption$AESEncodeException.class */
    public static class AESEncodeException extends Exception {
        private static final long serialVersionUID = 4826692804389845727L;

        public AESEncodeException(String str) {
            super(str);
        }

        public AESEncodeException(String str, Exception exc) {
            super(str, exc);
        }
    }

    public static String byte2hex(byte[] bArr) {
        String str = "";
        for (byte b : bArr) {
            String hexString = Integer.toHexString(b & 255);
            str = hexString.length() == 1 ? str + HEX_PREFIX + hexString : str + hexString;
        }
        return str.toUpperCase();
    }

    public static byte[] decrypt(byte[] bArr, byte[] bArr2) throws AESDecodeException {
        try {
            if (bArr2 == null) {
                throw new AESEncodeException("Key为空null");
            }
            if (bArr2.length != 16) {
                throw new AESEncodeException("Key长度不是16位");
            }
            SecretKeySpec secretKeySpec = new SecretKeySpec(bArr2, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                messageDigest.update(bArr2, 0, bArr2.length);
                cipher.init(2, secretKeySpec, new IvParameterSpec(messageDigest.digest()));
                return cipher.doFinal(bArr);
            } catch (NoSuchAlgorithmException e) {
                return null;
            }
        } catch (Exception e2) {
            throw new AESDecodeException("AES加密错误", e2);
        }
    }

    public static byte[] encrypt(String str, byte[] bArr) throws AESEncodeException {
        try {
            if (bArr == null) {
                throw new AESEncodeException("Key为空null");
            }
            if (bArr.length != 16) {
                throw new AESEncodeException("Key长度不是16位");
            }
            SecretKeySpec secretKeySpec = new SecretKeySpec(bArr, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                messageDigest.update(bArr, 0, bArr.length);
                cipher.init(1, secretKeySpec, new IvParameterSpec(messageDigest.digest()));
                return cipher.doFinal(str.getBytes());
            } catch (NoSuchAlgorithmException e) {
                return null;
            }
        } catch (Exception e2) {
            throw new AESEncodeException("AES加密错误", e2);
        }
    }

    public static byte[] hex2byte(String str) {
        if (str == null) {
            return null;
        }
        int length = str.length();
        if (length % 2 == 1) {
            return null;
        }
        byte[] bArr = new byte[length / 2];
        for (int i = 0; i != length / 2; i++) {
            bArr[i] = (byte) Integer.parseInt(str.substring(i * 2, (i * 2) + 2), 16);
        }
        return bArr;
    }

    public static String hexDecrypt(String str, String str2) throws AESDecodeException, UnsupportedEncodingException {
        return new String(decrypt(UrlBase64Coder.decode(str), hex2byte(str2)));
    }

    public static String hexEncrypt(String str, String str2) throws AESEncodeException, UnsupportedEncodingException {
        return new String(UrlBase64Coder.encode(encrypt(str, hex2byte(str2))));
    }

    public static void test() throws AESDecodeException, UnsupportedEncodingException {
    }
}
