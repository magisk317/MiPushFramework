package com.xiaomi.smack.util;

import android.text.TextUtils;
import com.xiaomi.channel.commonutils.string.Base64Coder;
import java.util.Random;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/util/StringUtils.class */
public class StringUtils {
    private static final char[] QUOTE_ENCODE = "&quot;".toCharArray();
    private static final char[] APOS_ENCODE = "&apos;".toCharArray();
    private static final char[] AMP_ENCODE = "&amp;".toCharArray();
    private static final char[] LT_ENCODE = "&lt;".toCharArray();
    private static final char[] GT_ENCODE = "&gt;".toCharArray();
    private static Random randGen = new Random();
    private static char[] numbersAndLetters = "0123456789abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private StringUtils() {
    }

    public static byte[] decodeBase64(String str) {
        return Base64Coder.decode(str);
    }

    public static String encodeBase64(String str) {
        return Base64Coder.encodeString(str);
    }

    public static String encodeBase64(byte[] bArr) {
        return String.valueOf(Base64Coder.encode(bArr));
    }

    public static String escapeForXML(String str) {
        if (str == null) {
            return null;
        }
        int i = 0;
        int i2 = 0;
        char[] charArray = str.toCharArray();
        int length = charArray.length;
        StringBuilder sb = new StringBuilder((int) (length * 1.3d));
        while (i < length) {
            char c = charArray[i];
            int i3 = i2;
            if (c <= '>') {
                if (c == '<') {
                    if (i > i2) {
                        sb.append(charArray, i2, i - i2);
                    }
                    i3 = i + 1;
                    sb.append(LT_ENCODE);
                } else if (c == '>') {
                    if (i > i2) {
                        sb.append(charArray, i2, i - i2);
                    }
                    i3 = i + 1;
                    sb.append(GT_ENCODE);
                } else if (c == '&') {
                    if (i > i2) {
                        sb.append(charArray, i2, i - i2);
                    }
                    if (length > i + 5 && charArray[i + 1] == '#' && Character.isDigit(charArray[i + 2]) && Character.isDigit(charArray[i + 3]) && Character.isDigit(charArray[i + 4]) && charArray[i + 5] == ';') {
                        i3 = i2;
                    } else {
                        i3 = i + 1;
                        sb.append(AMP_ENCODE);
                    }
                } else if (c == '\"') {
                    if (i > i2) {
                        sb.append(charArray, i2, i - i2);
                    }
                    i3 = i + 1;
                    sb.append(QUOTE_ENCODE);
                } else if (c == '\'') {
                    if (i > i2) {
                        sb.append(charArray, i2, i - i2);
                    }
                    i3 = i + 1;
                    sb.append(APOS_ENCODE);
                }
            }
            i++;
            i2 = i3;
        }
        if (i2 == 0) {
            return str;
        }
        if (i > i2) {
            sb.append(charArray, i2, i - i2);
        }
        return sb.toString();
    }

    public static boolean isValidXmlChar(char c) {
        return (c >= ' ' && c <= 55295) || (c >= 57344 && c <= 65533) || ((c >= 0 && c <= 65535) || c == '\t' || c == '\n' || c == '\r');
    }

    public static String parseName(String str) {
        if (str == null) {
            return null;
        }
        int iLastIndexOf = str.lastIndexOf("@");
        return iLastIndexOf <= 0 ? "" : str.substring(0, iLastIndexOf);
    }

    public static String randomString(int i) {
        if (i < 1) {
            return null;
        }
        char[] cArr = new char[i];
        for (int i2 = 0; i2 < cArr.length; i2++) {
            cArr[i2] = numbersAndLetters[randGen.nextInt(71)];
        }
        return new String(cArr);
    }

    public static final String replace(String str, String str2, String str3) {
        if (str == null) {
            return null;
        }
        int iIndexOf = str.indexOf(str2, 0);
        if (iIndexOf < 0) {
            return str;
        }
        char[] charArray = str.toCharArray();
        char[] charArray2 = str3.toCharArray();
        int length = str2.length();
        StringBuilder sb = new StringBuilder(charArray.length);
        sb.append(charArray, 0, iIndexOf);
        sb.append(charArray2);
        int i = iIndexOf;
        while (true) {
            int i2 = i + length;
            int iIndexOf2 = str.indexOf(str2, i2);
            if (iIndexOf2 <= 0) {
                sb.append(charArray, i2, charArray.length - i2);
                return sb.toString();
            }
            sb.append(charArray, i2, iIndexOf2 - i2);
            sb.append(charArray2);
            i = iIndexOf2;
        }
    }

    public static String stripInvalidXMLChars(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char cCharAt = str.charAt(i);
            if (isValidXmlChar(cCharAt)) {
                sb.append(cCharAt);
            }
        }
        return sb.toString();
    }

    public static final String unescapeFromXML(String str) {
        return replace(replace(replace(replace(replace(str, "&lt;", "<"), "&gt;", ">"), "&quot;", "\""), "&apos;", "'"), "&amp;", "&");
    }
}
