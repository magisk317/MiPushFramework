package com.xiaomi.channel.commonutils.android

import com.xiaomi.channel.commonutils.logger.MyLog

object SystemProperties {
    @JvmStatic
    fun get(str: String): String {
        return get(str, "")
    }

    @JvmStatic
    fun get(str: String, str2: String): String {
        return try {
            SystemUtils.loadClass(null, "android.os.SystemProperties")
                .getMethod("get", String::class.java, String::class.java)
                .invoke(null, str, str2) as String
        } catch (e: Exception) {
            MyLog.w("SystemProperties.get: $e")
            str2
        }
    }
}
