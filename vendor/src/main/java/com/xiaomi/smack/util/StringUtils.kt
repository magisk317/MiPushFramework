package com.xiaomi.smack.util

import android.text.TextUtils
import com.xiaomi.channel.commonutils.string.Base64Coder
import java.util.Random

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/smack/util/StringUtils.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
object StringUtils {
    private val QUOTE_ENCODE = "&quot;".toCharArray()
    private val APOS_ENCODE = "&apos;".toCharArray()
    private val AMP_ENCODE = "&amp;".toCharArray()
    private val LT_ENCODE = "&lt;".toCharArray()
    private val GT_ENCODE = "&gt;".toCharArray()
    private val randGen = Random()
    private val numbersAndLetters = "0123456789abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()

    @JvmStatic
    fun decodeBase64(str: String): ByteArray = Base64Coder.decode(str)

    @JvmStatic
    fun encodeBase64(str: String): String = Base64Coder.encodeString(str)

    @JvmStatic
    fun encodeBase64(bArr: ByteArray): String = Base64Coder.encode(bArr).concatToString()

    @JvmStatic
    fun escapeForXML(str: String?): String? {
        if (str == null) {
            return null
        }
        var start = 0
        var i = 0
        val charArray = str.toCharArray()
        val length = charArray.size
        val sb = StringBuilder((length * 1.3).toInt())
        while (i < length) {
            val c = charArray[i]
            var nextStart = start
            if (c <= '>') {
                when (c) {
                    '<' -> {
                        if (i > start) sb.append(charArray, start, i - start)
                        nextStart = i + 1
                        sb.append(LT_ENCODE)
                    }
                    '>' -> {
                        if (i > start) sb.append(charArray, start, i - start)
                        nextStart = i + 1
                        sb.append(GT_ENCODE)
                    }
                    '&' -> {
                        if (i > start) sb.append(charArray, start, i - start)
                        if (
                            length > i + 5 &&
                            charArray[i + 1] == '#' &&
                            Character.isDigit(charArray[i + 2]) &&
                            Character.isDigit(charArray[i + 3]) &&
                            Character.isDigit(charArray[i + 4]) &&
                            charArray[i + 5] == ';'
                        ) {
                            nextStart = start
                        } else {
                            nextStart = i + 1
                            sb.append(AMP_ENCODE)
                        }
                    }
                    '"' -> {
                        if (i > start) sb.append(charArray, start, i - start)
                        nextStart = i + 1
                        sb.append(QUOTE_ENCODE)
                    }
                    '\'' -> {
                        if (i > start) sb.append(charArray, start, i - start)
                        nextStart = i + 1
                        sb.append(APOS_ENCODE)
                    }
                }
            }
            i++
            start = nextStart
        }
        if (start == 0) {
            return str
        }
        if (i > start) {
            sb.append(charArray, start, i - start)
        }
        return sb.toString()
    }

    @JvmStatic
    fun isValidXmlChar(c: Char): Boolean {
        return (c >= ' ' && c <= 55295.toChar()) ||
            (c >= 57344.toChar() && c <= 65533.toChar()) ||
            ((c >= 0.toChar() && c <= 65535.toChar()) || c == '\t' || c == '\n' || c == '\r')
    }

    @JvmStatic
    fun parseName(str: String?): String? {
        if (str == null) {
            return null
        }
        val lastIndex = str.lastIndexOf("@")
        return if (lastIndex <= 0) "" else str.substring(0, lastIndex)
    }

    @JvmStatic
    fun randomString(i: Int): String? {
        if (i < 1) {
            return null
        }
        val chars = CharArray(i)
        for (index in chars.indices) {
            chars[index] = numbersAndLetters[randGen.nextInt(71)]
        }
        return String(chars)
    }

    @JvmStatic
    fun replace(str: String?, str2: String, str3: String): String? {
        if (str == null) {
            return null
        }
        val firstIndex = str.indexOf(str2, 0)
        if (firstIndex < 0) {
            return str
        }
        val charArray = str.toCharArray()
        val replacement = str3.toCharArray()
        val length = str2.length
        val sb = StringBuilder(charArray.size)
        sb.append(charArray, 0, firstIndex)
        sb.append(replacement)
        var i = firstIndex
        while (true) {
            val next = i + length
            val nextIndex = str.indexOf(str2, next)
            if (nextIndex <= 0) {
                sb.append(charArray, next, charArray.size - next)
                return sb.toString()
            }
            sb.append(charArray, next, nextIndex - next)
            sb.append(replacement)
            i = nextIndex
        }
    }

    @JvmStatic
    fun stripInvalidXMLChars(str: String?): String? {
        if (TextUtils.isEmpty(str)) {
            return str
        }
        val sb = StringBuilder(str!!.length)
        for (c in str) {
            if (isValidXmlChar(c)) {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun unescapeFromXML(str: String?): String? {
        return replace(
            replace(
                replace(
                    replace(
                        replace(str, "&lt;", "<"),
                        "&gt;",
                        ">"
                    ),
                    "&quot;",
                    "\""
                ),
                "&apos;",
                "'"
            ),
            "&amp;",
            "&"
        )
    }
}
