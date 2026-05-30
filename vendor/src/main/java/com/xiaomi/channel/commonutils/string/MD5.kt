package com.xiaomi.channel.commonutils.string

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object MD5 {
    private const val HEX_PREFIX = "0"

    @JvmStatic
    fun MD5_16(str: String?): String {
        if (str == null) return ""
        return MD5_32(str)?.subSequence(8, 24)?.toString() ?: ""
    }

    @JvmStatic
    fun MD5_32(str: String): String? {
        return try {
            val messageDigest = MessageDigest.getInstance("MD5")
            messageDigest.update(str.toByteArray(), 0, str.length)
            val stringBuffer = StringBuffer()
            for (b in messageDigest.digest()) {
                stringBuffer.append(byte2Hex(b))
            }
            stringBuffer.toString()
        } catch (e: NoSuchAlgorithmException) {
            null
        }
    }

    private fun byte2Hex(b: Byte): String {
        val i = (b.toInt() and 127) + if (b < 0) 128 else 0
        return buildString {
            if (i < 16) append(HEX_PREFIX)
            append(Integer.toHexString(i).lowercase())
        }
    }
}
