package com.xiaomi.channel.commonutils.string;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/string/Base64Coder.class */
public class Base64Coder {
    private static byte[] map2;
    private static final String systemLineSeparator = System.getProperty("line.separator");
    private static char[] map1 = new char[64];

    static {
        int i = 0;
        char c = 'A';
        while (c <= 'Z') {
            map1[i] = c;
            c = (char) (c + 1);
            i++;
        }
        char c2 = 'a';
        while (c2 <= 'z') {
            map1[i] = c2;
            c2 = (char) (c2 + 1);
            i++;
        }
        char c3 = '0';
        while (c3 <= '9') {
            map1[i] = c3;
            c3 = (char) (c3 + 1);
            i++;
        }
        char[] cArr = map1;
        cArr[i] = (char) 43;
        cArr[i + 1] = (char) 47;
        map2 = new byte[128];
        int i2 = 0;
        while (true) {
            byte[] bArr = map2;
            if (i2 >= bArr.length) {
                break;
            }
            bArr[i2] = (byte) (-1);
            i2++;
        }
        for (int i3 = 0; i3 < 64; i3++) {
            map2[map1[i3]] = (byte) i3;
        }
    }

    private Base64Coder() {
    }

    public static byte[] decode(String str) {
        return decode(str.toCharArray());
    }

    public static byte[] decode(char[] cArr) {
        return decode(cArr, 0, cArr.length);
    }

    public static byte[] decode(char[] cArr, int i, int i2) {
        char c;
        char c2;
        if (i2 % 4 != 0) {
            throw new IllegalArgumentException("Length of Base64 encoded input string is not a multiple of 4.");
        }
        while (i2 > 0 && cArr[(i + i2) - 1] == '=') {
            i2--;
        }
        int i3 = (i2 * 3) / 4;
        byte[] bArr = new byte[i3];
        int i4 = i + i2;
        int i5 = 0;
        int i6 = i;
        while (i6 < i4) {
            int i7 = i6 + 1;
            char c3 = cArr[i6];
            int i8 = i7 + 1;
            char c4 = cArr[i7];
            if (i8 < i4) {
                i6 = i8 + 1;
                c = cArr[i8];
            } else {
                i6 = i8;
                c = 'A';
            }
            if (i6 < i4) {
                char c5 = cArr[i6];
                i6++;
                c2 = c5;
            } else {
                c2 = 'A';
            }
            if (c3 > 127 || c4 > 127 || c > 127 || c2 > 127) {
                throw new IllegalArgumentException("Illegal character in Base64 encoded data.");
            }
            byte[] bArr2 = map2;
            byte b = bArr2[c3];
            byte b2 = bArr2[c4];
            byte b3 = bArr2[c];
            byte b4 = bArr2[c2];
            if (b < 0 || b2 < 0 || b3 < 0 || b4 < 0) {
                throw new IllegalArgumentException("Illegal character in Base64 encoded data.");
            }
            int i9 = i5 + 1;
            bArr[i5] = (byte) ((b << 2) | (b2 >>> 4));
            int i10 = i9;
            if (i9 < i3) {
                bArr[i9] = (byte) (((b2 & 15) << 4) | (b3 >>> 2));
                i10 = i9 + 1;
            }
            if (i10 < i3) {
                bArr[i10] = (byte) (((b3 & 3) << 6) | b4);
                i10++;
            }
            i5 = i10;
        }
        return bArr;
    }

    public static byte[] decodeLines(String str) {
        char[] cArr = new char[str.length()];
        int i = 0;
        int i2 = 0;
        while (i2 < str.length()) {
            char cCharAt = str.charAt(i2);
            int i3 = i;
            if (cCharAt != ' ') {
                i3 = i;
                if (cCharAt != '\r') {
                    i3 = i;
                    if (cCharAt != '\n') {
                        i3 = i;
                        if (cCharAt != '\t') {
                            cArr[i] = cCharAt;
                            i3 = i + 1;
                        }
                    }
                }
            }
            i2++;
            i = i3;
        }
        return decode(cArr, 0, i);
    }

    public static String decodeString(String str) {
        return new String(decode(str));
    }

    public static char[] encode(byte[] bArr) {
        return encode(bArr, 0, bArr.length);
    }

    public static char[] encode(byte[] bArr, int i) {
        return encode(bArr, 0, i);
    }

    public static char[] encode(byte[] bArr, int i, int i2) {
        int i3;
        int i4;
        int i5;
        int i6 = ((i2 * 4) + 2) / 3;
        char[] cArr = new char[((i2 + 2) / 3) * 4];
        int i7 = i + i2;
        int i8 = 0;
        for (int i9 = i; i9 < i7; i9 = i3) {
            int i10 = i9 + 1;
            int i11 = bArr[i9] & 255;
            if (i10 < i7) {
                i3 = i10 + 1;
                i4 = bArr[i10] & 255;
            } else {
                i3 = i10;
                i4 = 0;
            }
            if (i3 < i7) {
                int i12 = bArr[i3] & 255;
                i3++;
                i5 = i12;
            } else {
                i5 = 0;
            }
            int i13 = i8 + 1;
            char[] cArr2 = map1;
            cArr[i8] = cArr2[i11 >>> 2];
            int i14 = i13 + 1;
            cArr[i13] = cArr2[((i11 & 3) << 4) | (i4 >>> 4)];
            cArr[i14] = i14 < i6 ? cArr2[((i4 & 15) << 2) | (i5 >>> 6)] : '=';
            int i15 = i14 + 1;
            char c = '=';
            if (i15 < i6) {
                c = cArr2[i5 & 63];
            }
            cArr[i15] = c;
            i8 = i15 + 1;
        }
        return cArr;
    }

    public static String encodeLines(byte[] bArr) {
        return encodeLines(bArr, 0, bArr.length, 76, systemLineSeparator);
    }

    public static String encodeLines(byte[] bArr, int i, int i2, int i3, String str) {
        int i4 = (i3 * 3) / 4;
        if (i4 <= 0) {
            throw new IllegalArgumentException();
        }
        StringBuilder sb = new StringBuilder((((i2 + 2) / 3) * 4) + (str.length() * (((i2 + i4) - 1) / i4)));
        int i5 = 0;
        while (true) {
            int i6 = i5;
            if (i6 >= i2) {
                return sb.toString();
            }
            int iMin = Math.min(i2 - i6, i4);
            sb.append(encode(bArr, i + i6, iMin));
            sb.append(str);
            i5 = i6 + iMin;
        }
    }

    public static String encodeString(String str) {
        return new String(encode(str.getBytes()));
    }
}
