package com.xiaomi.channel.commonutils.string

object UrlBase64Coder {
    private val map1 = CharArray(64)
    private val map2: ByteArray
    private val systemLineSeparator = System.getProperty("line.separator")

    init {
        var i = 0
        var c = 'A'
        while (c <= 'Z') {
            map1[i++] = c
            c++
        }
        c = 'a'
        while (c <= 'z') {
            map1[i++] = c
            c++
        }
        c = '0'
        while (c <= '9') {
            map1[i++] = c
            c++
        }
        map1[i] = '-'
        map1[i + 1] = '_'
        map2 = ByteArray(128)
        var i2 = 0
        while (i2 < map2.size) {
            map2[i2] = -1
            i2++
        }
        for (i3 in 0 until 64) {
            map2[map1[i3].code] = i3.toByte()
        }
    }

    @JvmStatic
    fun decode(str: String): ByteArray {
        return decode(str.toCharArray())
    }

    @JvmStatic
    fun decode(cArr: CharArray): ByteArray {
        return decode(cArr, 0, cArr.size)
    }

    @JvmStatic
    fun decode(cArr: CharArray, i: Int, i2: Int): ByteArray {
        if (i2 % 4 != 0) {
            throw IllegalArgumentException("Length of Base64 encoded input string is not a multiple of 4.")
        }
        var len = i2
        while (len > 0 && cArr[i + len - 1] == '.') {
            len--
        }
        val i3 = (len * 3) / 4
        val bArr = ByteArray(i3)
        val i4 = i + len
        var i5 = 0
        var i6 = i
        while (i6 < i4) {
            val i7 = i6 + 1
            val c3 = cArr[i6]
            val i8 = i7 + 1
            val c4 = cArr[i7]
            val c: Char
            if (i8 < i4) {
                i6 = i8 + 1
                c = cArr[i8]
            } else {
                i6 = i8
                c = 'A'
            }
            val c2 = if (i6 < i4) {
                i6++
                cArr[i6 - 1]
            } else {
                'A'
            }
            if (c3.code > 127 || c4.code > 127 || c.code > 127 || c2.code > 127) {
                throw IllegalArgumentException("Illegal character in Base64 encoded data.")
            }
            val b = map2[c3.code]
            val b2 = map2[c4.code]
            val b3 = map2[c.code]
            val b4 = map2[c2.code]
            if (b < 0 || b2 < 0 || b3 < 0 || b4 < 0) {
                throw IllegalArgumentException("Illegal character in Base64 encoded data.")
            }
            val i9 = i5 + 1
            bArr[i5] = ((b.toInt() shl 2) or (b2.toInt() ushr 4)).toByte()
            var i10 = i9
            if (i9 < i3) {
                bArr[i9] = (((b2.toInt() and 15) shl 4) or (b3.toInt() ushr 2)).toByte()
                i10 = i9 + 1
            }
            if (i10 < i3) {
                bArr[i10] = (((b3.toInt() and 3) shl 6) or b4.toInt()).toByte()
                i10++
            }
            i5 = i10
        }
        return bArr
    }

    @JvmStatic
    fun decodeLines(str: String): ByteArray {
        val cArr = CharArray(str.length)
        var i = 0
        var i2 = 0
        while (i2 < str.length) {
            val cCharAt = str[i2]
            var i3 = i
            if (cCharAt != ' ' && cCharAt != '\r' && cCharAt != '\n' && cCharAt != '\t') {
                cArr[i] = cCharAt
                i3 = i + 1
            }
            i2++
            i = i3
        }
        return decode(cArr, 0, i)
    }

    @JvmStatic
    fun decodeString(str: String): String {
        return String(decode(str))
    }

    @JvmStatic
    fun encode(bArr: ByteArray): CharArray {
        return encode(bArr, 0, bArr.size)
    }

    @JvmStatic
    fun encode(bArr: ByteArray, i: Int): CharArray {
        return encode(bArr, 0, i)
    }

    @JvmStatic
    fun encode(bArr: ByteArray, i: Int, i2: Int): CharArray {
        val i6 = ((i2 * 4) + 2) / 3
        val cArr = CharArray(((i2 + 2) / 3) * 4)
        val i7 = i + i2
        var i8 = 0
        var i9 = i
        while (i9 < i7) {
            val i10 = i9 + 1
            val i11 = bArr[i9].toInt() and 255
            val i3: Int
            val i4: Int
            if (i10 < i7) {
                i3 = i10 + 1
                i4 = bArr[i10].toInt() and 255
            } else {
                i3 = i10
                i4 = 0
            }
            val i5: Int
            if (i3 < i7) {
                i5 = bArr[i3].toInt() and 255
                i9 = i3 + 1
            } else {
                i5 = 0
                i9 = i3
            }
            val i12 = i8 + 1
            cArr[i8] = map1[i11 ushr 2]
            val i13 = i12 + 1
            cArr[i12] = map1[((i11 and 3) shl 4) or (i4 ushr 4)]
            cArr[i13] = if (i13 < i6) map1[((i4 and 15) shl 2) or (i5 ushr 6)] else '.'
            val i14 = i13 + 1
            val c = if (i14 < i6) map1[i5 and 63] else '.'
            cArr[i14] = c
            i8 = i14 + 1
        }
        return cArr
    }

    @JvmStatic
    fun encodeLines(bArr: ByteArray): String {
        return encodeLines(bArr, 0, bArr.size, 76, systemLineSeparator ?: "\n")
    }

    @JvmStatic
    fun encodeLines(bArr: ByteArray, i: Int, i2: Int, i3: Int, str: String): String {
        val i4 = (i3 * 3) / 4
        if (i4 <= 0) {
            throw IllegalArgumentException()
        }
        val sb = StringBuilder((((i2 + 2) / 3) * 4) + (str.length * (((i2 + i4) - 1) / i4)))
        var i5 = 0
        while (true) {
            val i6 = i5
            if (i6 >= i2) return sb.toString()
            val iMin = minOf(i2 - i6, i4)
            sb.append(encode(bArr, i + i6, iMin))
            sb.append(str)
            i5 = i6 + iMin
        }
    }

    @JvmStatic
    fun encodeString(str: String): String {
        return String(encode(str.toByteArray()))
    }
}
