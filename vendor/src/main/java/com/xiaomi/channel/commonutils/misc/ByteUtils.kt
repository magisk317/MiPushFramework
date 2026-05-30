package com.xiaomi.channel.commonutils.misc

object ByteUtils {
    @JvmStatic
    fun parseInt(i: Int): ByteArray {
        return byteArrayOf(
            (i shr 24).toByte(),
            (i shr 16).toByte(),
            (i shr 8).toByte(),
            i.toByte()
        )
    }

    @JvmStatic
    fun toInt(bArr: ByteArray): Int {
        if (bArr.size == 4) {
            return 0 or ((bArr[0].toInt() and 255) shl 24) or
                    ((bArr[1].toInt() and 255) shl 16) or
                    ((bArr[2].toInt() and 255) shl 8) or
                    (bArr[3].toInt() and 255)
        }
        throw IllegalArgumentException("the length of bytes must be 4")
    }
}
