package org.apache.thrift.protocol;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/TBase64Utils.class */
class TBase64Utils {
    private static final byte[] DECODE_TABLE = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, -1, 63, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, -1, -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
    private static final String ENCODE_TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    TBase64Utils() {
    }

    static final void decode(byte[] bArr, int i, int i2, byte[] bArr2, int i3) {
        byte[] bArr3 = DECODE_TABLE;
        bArr2[i3] = (byte) ((bArr3[bArr[i] & 255] << 2) | (bArr3[bArr[i + 1] & 255] >> 4));
        if (i2 > 2) {
            bArr2[i3 + 1] = (byte) ((bArr3[bArr[i + 2] & 255] >> 2) | ((bArr3[bArr[i + 1] & 255] << 4) & 240));
            if (i2 > 3) {
                bArr2[i3 + 2] = (byte) (bArr3[bArr[i + 3] & 255] | ((bArr3[bArr[i + 2] & 255] << 6) & 192));
            }
        }
    }

    static final void encode(byte[] bArr, int i, int i2, byte[] bArr2, int i3) {
        bArr2[i3] = (byte) ENCODE_TABLE.charAt((bArr[i] >> 2) & 63);
        if (i2 == 3) {
            bArr2[i3 + 1] = (byte) ENCODE_TABLE.charAt(((bArr[i] << 4) + (bArr[i + 1] >> 4)) & 63);
            bArr2[i3 + 2] = (byte) ENCODE_TABLE.charAt(((bArr[i + 1] << 2) + (bArr[i + 2] >> 6)) & 63);
            bArr2[i3 + 3] = (byte) ENCODE_TABLE.charAt(bArr[i + 2] & 63);
        } else if (i2 != 2) {
            bArr2[i3 + 1] = (byte) ENCODE_TABLE.charAt((bArr[i] << 4) & 63);
        } else {
            bArr2[i3 + 1] = (byte) ENCODE_TABLE.charAt(((bArr[i] << 4) + (bArr[i + 1] >> 4)) & 63);
            bArr2[i3 + 2] = (byte) ENCODE_TABLE.charAt((bArr[i + 1] << 2) & 63);
        }
    }
}
