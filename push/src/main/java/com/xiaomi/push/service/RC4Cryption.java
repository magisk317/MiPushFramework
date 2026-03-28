package com.xiaomi.push.service;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.string.Base64Coder;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/RC4Cryption.class */
public class RC4Cryption {
    private static int keylength = 8;
    private byte[] S;
    private int next_j;
    private int the_i;
    private int the_j;

    public RC4Cryption() {
        this.next_j = -666;
        this.S = new byte[256];
        this.the_j = 0;
        this.the_i = 0;
    }

    public RC4Cryption(byte[] bArr) {
        this.next_j = -666;
        this.S = bArr;
        this.the_j = 0;
        this.the_i = 0;
    }

    public static byte[] buildkey(byte[] bArr, byte[] bArr2) {
        byte[] bArr3 = new byte[keylength];
        int length = bArr.length;
        for (int i = 0; i < length; i++) {
            bArr3[i] = bArr[i];
        }
        for (int i2 = length; i2 < keylength; i2++) {
            bArr3[i2] = bArr2[i2 - length];
        }
        return bArr3;
    }

    public static String byte2string(byte b) {
        return ("" + "0123456789abcdef".charAt((b >> 4) & 15)) + "0123456789abcdef".charAt(b & 15);
    }

    public static String byte2string(byte[] bArr) {
        String str = "";
        for (byte b : bArr) {
            str = str + byte2string(b);
        }
        return str;
    }

    public static byte[] decrypt(byte[] bArr, String str) {
        return encrypt(bArr, Base64Coder.decode(str));
    }

    public static String encrypt(byte[] bArr, String str) {
        return String.valueOf(Base64Coder.encode(encrypt(bArr, str.getBytes())));
    }

    public static byte[] encrypt(byte[] bArr, byte[] bArr2) {
        byte[] bArr3 = new byte[bArr2.length];
        RC4Cryption rC4Cryption = new RC4Cryption();
        rC4Cryption.ksa(bArr);
        rC4Cryption.init();
        for (int i = 0; i < bArr2.length; i++) {
            bArr3[i] = (byte) (bArr2[i] ^ rC4Cryption.nextVal());
        }
        return bArr3;
    }

    public static byte[] encrypt(byte[] bArr, byte[] bArr2, boolean z, int i, int i2) {
        if (i < 0 || i > bArr2.length || i + i2 > bArr2.length) {
            throw new IllegalArgumentException("start = " + i + " len = " + i2);
        }
        int i3 = i;
        byte[] bArr3 = bArr2;
        if (!z) {
            bArr3 = new byte[i2];
            i3 = 0;
        }
        RC4Cryption rC4Cryption = new RC4Cryption();
        rC4Cryption.ksa(bArr);
        rC4Cryption.init();
        for (int i4 = 0; i4 < i2; i4++) {
            bArr3[i3 + i4] = (byte) (bArr2[i + i4] ^ rC4Cryption.nextVal());
        }
        return bArr3;
    }

    public static byte[] generateKeyForRC4(String str, String str2) {
        byte[] bArrDecode = Base64Coder.decode(str);
        byte[] bytes = str2.getBytes();
        byte[] bArr = new byte[bArrDecode.length + 1 + bytes.length];
        for (int i = 0; i < bArrDecode.length; i++) {
            bArr[i] = bArrDecode[i];
        }
        bArr[bArrDecode.length] = (byte) 95;
        for (int i2 = 0; i2 < bytes.length; i2++) {
            bArr[bArrDecode.length + 1 + i2] = bytes[i2];
        }
        return bArr;
    }

    private static int hexval(char c) {
        if ('0' <= c && c <= '9') {
            return c - '0';
        }
        if ('a' <= c && c <= 'f') {
            return (c - 'a') + 10;
        }
        if ('A' > c || c > 'F') {
            return 0;
        }
        return (c - 'A') + 10;
    }

    private void init() {
        this.the_j = 0;
        this.the_i = 0;
    }

    private void ksa(int i, byte[] bArr, boolean z) {
        int length = bArr.length;
        for (int i2 = 0; i2 < 256; i2++) {
            this.S[i2] = (byte) i2;
        }
        int i3 = 0;
        this.the_j = 0;
        while (true) {
            this.the_i = i3;
            int i4 = this.the_i;
            if (i4 >= i) {
                break;
            }
            int iPosify = ((this.the_j + posify(this.S[i4])) + posify(bArr[this.the_i % length])) % 256;
            this.the_j = iPosify;
            sswap(this.S, this.the_i, iPosify);
            i3 = this.the_i + 1;
        }
        if (i != 256) {
            this.next_j = ((this.the_j + posify(this.S[i])) + posify(bArr[i % length])) % 256;
        }
        if (z) {
            StringBuilder sb = new StringBuilder();
            sb.append("S_");
            sb.append(i - 1);
            sb.append(":");
            for (int i5 = 0; i5 <= i; i5++) {
                sb.append(" ");
                sb.append(posify(this.S[i5]));
            }
            sb.append("   j_");
            sb.append(i - 1);
            sb.append("=");
            sb.append(this.the_j);
            sb.append("   j_");
            sb.append(i);
            sb.append("=");
            sb.append(this.next_j);
            sb.append("   S_");
            sb.append(i - 1);
            sb.append("[j_");
            sb.append(i - 1);
            sb.append("]=");
            sb.append(posify(this.S[this.the_j]));
            sb.append("   S_");
            sb.append(i - 1);
            sb.append("[j_");
            sb.append(i);
            sb.append("]=");
            sb.append(posify(this.S[this.next_j]));
            if (this.S[1] != 0) {
                sb.append("   S[1]!=0");
            }
            MyLog.w(sb.toString());
        }
    }

    private void ksa(byte[] bArr) {
        ksa(256, bArr, false);
    }

    public static int posify(byte b) {
        return b >= 0 ? b : b + 256;
    }

    private static void sswap(byte[] bArr, int i, int i2) {
        byte b = bArr[i];
        bArr[i] = bArr[i2];
        bArr[i2] = b;
    }

    public static byte[] string2byte(String str) {
        int length = str.length() / 2;
        byte[] bArr = new byte[length];
        for (int i = 0; i < length; i++) {
            bArr[i] = (byte) ((hexval(str.charAt(i * 2)) * 16) + hexval(str.charAt((i * 2) + 1)));
        }
        return bArr;
    }

    int S(int i) {
        return posify(this.S[(byte) i]);
    }

    byte inverse(byte b) {
        for (int i = 0; i < 256; i++) {
            if (b == this.S[i]) {
                return (byte) i;
            }
        }
        return (byte) 0;
    }

    byte nextVal() {
        int i = (this.the_i + 1) % 256;
        this.the_i = i;
        int iPosify = (this.the_j + posify(this.S[i])) % 256;
        this.the_j = iPosify;
        sswap(this.S, this.the_i, iPosify);
        byte[] bArr = this.S;
        return bArr[(posify(bArr[this.the_i]) + posify(this.S[this.the_j])) % 256];
    }

    int next_j() {
        return this.next_j;
    }

    int the_i() {
        return this.the_i;
    }

    int the_j() {
        return this.the_j;
    }
}
