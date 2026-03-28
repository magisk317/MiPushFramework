package org.apache.thrift;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/EncodingUtils.class */
public class EncodingUtils {
    public static final int decodeBigEndian(byte[] bArr) {
        return decodeBigEndian(bArr, 0);
    }

    public static final int decodeBigEndian(byte[] bArr, int i) {
        return ((bArr[i] & 255) << 24) | ((bArr[i + 1] & 255) << 16) | ((bArr[i + 2] & 255) << 8) | (bArr[i + 3] & 255);
    }

    public static final void encodeBigEndian(int i, byte[] bArr) {
        encodeBigEndian(i, bArr, 0);
    }

    public static final void encodeBigEndian(int i, byte[] bArr, int i2) {
        bArr[i2] = (byte) ((i >> 24) & 255);
        bArr[i2 + 1] = (byte) ((i >> 16) & 255);
        bArr[i2 + 2] = (byte) ((i >> 8) & 255);
        bArr[i2 + 3] = (byte) (i & 255);
    }
}
