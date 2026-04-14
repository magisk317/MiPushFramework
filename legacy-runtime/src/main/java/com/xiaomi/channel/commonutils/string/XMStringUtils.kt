package com.xiaomi.channel.commonutils.string

import android.text.TextUtils
import java.io.UnsupportedEncodingException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.regex.Pattern

object XMStringUtils {
    private const val MAX_ASCII: Char = 127.toChar()
    private const val MIN_ASCII: Char = 0.toChar()
    private const val ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    @JvmStatic
    fun bytesToString(bArr: ByteArray?): String? {
        if (bArr == null || bArr.isEmpty()) return null
        return try {
            String(bArr, charset("UTF-8"))
        } catch (e: UnsupportedEncodingException) {
            String(bArr)
        }
    }

    @JvmStatic
    fun checkAllAscii(str: String?): Boolean {
        if (str == null) return true
        for (i in str.indices) {
            val c = str[i]
            if (c < MIN_ASCII || c > MAX_ASCII) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun contains(str: String, str2: String): Boolean {
        var i = 0
        var i2 = 0
        while (i < str2.length && i2 < str.length) {
            if (str2[i] == str[i2]) {
                i++
                i2++
            } else {
                i2++
            }
        }
        return i == str2.length
    }

    @JvmStatic
    fun generateRandomString(length: Int): String {
        val random = Random()
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(ALPHANUMERIC[random.nextInt(ALPHANUMERIC.length)])
        }
        return sb.toString()
    }

    @JvmStatic
    fun getBytes(str: String): ByteArray {
        return try {
            str.toByteArray(charset("UTF-8"))
        } catch (e: UnsupportedEncodingException) {
            str.toByteArray()
        }
    }

    @JvmStatic
    fun getHexString(bArr: ByteArray): String {
        var str = ""
        for (b in bArr) {
            str += Integer.toString((b.toInt() and 255) + 256, 16).substring(1)
        }
        return str
    }

    @JvmStatic
    fun getMd5(bArr: ByteArray): String {
        return try {
            val messageDigest = MessageDigest.getInstance("MD5")
            messageDigest.update(bArr)
            String.format("%1\$032X", BigInteger(1, messageDigest.digest())).lowercase()
        } catch (e: Exception) {
            ""
        }
    }

    @JvmStatic
    fun getMd5Digest(str: String): String {
        if (TextUtils.isEmpty(str)) return ""
        return try {
            val messageDigest = MessageDigest.getInstance("MD5")
            messageDigest.update(getBytes(str))
            String.format("%1\$032X", BigInteger(1, messageDigest.digest()))
        } catch (e: NoSuchAlgorithmException) {
            str
        }
    }

    @JvmStatic
    fun getSHA1Digest(str: String?): String? {
        if (str == null) return null
        return try {
            val messageDigest = MessageDigest.getInstance("SHA1")
            messageDigest.update(getBytes(str))
            String.format("%1\$032X", BigInteger(1, messageDigest.digest()))
        } catch (e: NoSuchAlgorithmException) {
            str
        }
    }

    @JvmStatic
    fun getStringNotNull(str: String?): String {
        return if (TextUtils.isEmpty(str)) "" else str!!
    }

    @JvmStatic
    fun getStringUTF8Length(str: String?): Int {
        if (TextUtils.isEmpty(str)) return 0
        return try {
            str!!.toByteArray(charset("UTF-8")).size
        } catch (e: UnsupportedEncodingException) {
            0
        }
    }

    @JvmStatic
    fun isNumberAndLetter(str: String?): Boolean {
        if (TextUtils.isEmpty(str)) return false
        return Pattern.compile("^[A-Za-z0-9]+$").matcher(str!!).matches()
    }

    @JvmStatic
    fun isTheSameChars(str: String?): Boolean {
        if (TextUtils.isEmpty(str)) return false
        val firstChar = str!![0]
        for (i in 1 until str.length) {
            if (str[i] != firstChar) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun join(collection: Collection<*>?, delimiter: Char): String? {
        if (collection == null) return null
        return join(collection.iterator(), delimiter)
    }

    @JvmStatic
    fun join(collection: Collection<*>?, delimiter: String): String? {
        if (collection == null) return null
        return join(collection.iterator(), delimiter)
    }

    @JvmStatic
    fun join(it: Iterator<*>?, c: Char): String? {
        if (it == null) return null
        if (!it.hasNext()) return ""
        val next = it.next()
        if (!it.hasNext()) return next.toString()
        val sb = StringBuffer(256)
        if (next != null) sb.append(next)
        while (it.hasNext()) {
            sb.append(c)
            val next2 = it.next()
            if (next2 != null) sb.append(next2)
        }
        return sb.toString()
    }

    @JvmStatic
    fun join(it: Iterator<*>?, str: String): String? {
        if (it == null) return null
        if (!it.hasNext()) return ""
        val next = it.next()
        if (!it.hasNext()) return next.toString()
        val sb = StringBuffer(256)
        if (next != null) sb.append(next)
        while (it.hasNext()) {
            if (str != null) sb.append(str)
            val next2 = it.next()
            if (next2 != null) sb.append(next2)
        }
        return sb.toString()
    }

    @JvmStatic
    fun join(objArr: Array<Any?>): String? {
        return join(objArr, null as String?)
    }

    @JvmStatic
    fun join(objArr: Array<Any?>, c: Char): String? {
        if (objArr == null) return null
        return join(objArr, c, 0, objArr.size)
    }

    @JvmStatic
    fun join(objArr: Array<Any?>, c: Char, start: Int, end: Int): String? {
        if (objArr == null) return null
        val count = end - start
        if (count <= 0) return ""
        val sb = StringBuffer(count * ((if (objArr[start] == null) 16 else objArr[start].toString().length) + 1))
        for (i4 in start until end) {
            if (i4 > start) sb.append(c)
            if (objArr[i4] != null) sb.append(objArr[i4])
        }
        return sb.toString()
    }

    @JvmStatic
    fun join(objArr: Array<Any?>, str: String?): String? {
        if (objArr == null) return null
        return join(objArr, str, 0, objArr.size)
    }

    @JvmStatic
    fun join(objArr: Array<Any?>, str: String?, start: Int, end: Int): String? {
        if (objArr == null) return null
        val delimiter = str ?: ""
        val count = end - start
        if (count <= 0) return ""
        val sb = StringBuffer(count * ((if (objArr[start] == null) 16 else objArr[start].toString().length) + delimiter.length))
        for (i4 in start until end) {
            if (i4 > start) sb.append(delimiter)
            if (objArr[i4] != null) sb.append(objArr[i4])
        }
        return sb.toString()
    }

    @JvmStatic
    fun obfuscateString(str: String?, i: Int): String {
        if (TextUtils.isEmpty(str)) return ""
        val length = str!!.length
        var i2 = i
        if (i <= 0 || length < i) {
            i2 = length / 3
            if (i2 <= 1) {
                i2 = 1
            } else if (i2 > 3) {
                i2 = 3
            }
        }
        val sb = StringBuilder()
        for (i3 in 0 until length) {
            if ((i3 + 1) % i2 == 0) {
                sb.append('*')
            } else {
                sb.append(str[i3])
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun stringToInt(str: String?, default: Int): Int {
        return try {
            str?.toInt() ?: default
        } catch (e: NumberFormatException) {
            default
        }
    }

    @JvmStatic
    fun toIntArray(list: List<Int>): IntArray {
        return list.map { it.toInt() }.toIntArray()
    }

    @JvmStatic
    fun toLongArray(list: List<Long>): LongArray {
        return list.map { it.toLong() }.toLongArray()
    }

    @JvmStatic
    fun toStrArray(list: List<String>): Array<String> {
        return list.toTypedArray()
    }

    @JvmStatic
    fun toUpperCase(str: String?): String {
        return if (TextUtils.isEmpty(str)) "" else str!!.uppercase()
    }
}
