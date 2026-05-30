package com.xiaomi.channel.commonutils.misc

import android.content.Context

object DebugUtils {
    private val HEXCHARS = "0123456789ABCDEF".toCharArray()

    @JvmStatic
    fun bytes2Hex(bArr: ByteArray, offset: Int, length: Int): String {
        val sb = StringBuilder(length * 2)
        for (i in 0 until length) {
            val value = bArr[offset + i].toInt() and 255
            sb.append(HEXCHARS[value shr 4])
            sb.append(HEXCHARS[value and 15])
        }
        return sb.toString()
    }

    @JvmStatic
    fun isTesting(context: Context): Boolean = DebugSwitch.sDebugServerHost
}
