package io.github.magisk317.mipush.bridge

import com.xiaomi.channel.commonutils.logger.MyLog

object LegacyLoggerBridge {
    @JvmStatic
    fun setDebugLoggingEnabled(enabled: Boolean) {
        MyLog.setDebugLoggingEnabled(enabled)
    }

    @JvmStatic
    fun setMinimumLogLevel(level: Int) {
        MyLog.setLogLevel(level)
    }

    @JvmStatic
    fun minimumLogLevel(): Int {
        return MyLog.getLogLevel()
    }
}
