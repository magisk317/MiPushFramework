package com.xiaomi.push.log

import com.xiaomi.channel.commonutils.logger.LevelAwareLoggerInterface
import com.xiaomi.channel.commonutils.logger.LoggerInterface

class MIPushDebugLog(
    private var sUserLogInterface: LoggerInterface?,
    private var sPushLogFileInterface: LoggerInterface?
) : LevelAwareLoggerInterface {
    override fun log(str: String) {
        sUserLogInterface?.log(str)
        sPushLogFileInterface?.log(str)
    }

    override fun log(str: String, th: Throwable) {
        sUserLogInterface?.log(str, th)
        sPushLogFileInterface?.log(str, th)
    }

    override fun setTag(str: String) {
        // No-op
    }

    override fun log(level: Int, str: String) {
        logWithLevel(sUserLogInterface, level, str)
        logWithLevel(sPushLogFileInterface, level, str)
    }

    override fun log(level: Int, str: String, th: Throwable) {
        logWithLevel(sUserLogInterface, level, str, th)
        logWithLevel(sPushLogFileInterface, level, str, th)
    }

    private fun logWithLevel(logger: LoggerInterface?, level: Int, str: String) {
        if (logger is LevelAwareLoggerInterface) {
            logger.log(level, str)
        } else {
            logger?.log(str)
        }
    }

    private fun logWithLevel(logger: LoggerInterface?, level: Int, str: String, th: Throwable) {
        if (logger is LevelAwareLoggerInterface) {
            logger.log(level, str, th)
        } else {
            logger?.log(str, th)
        }
    }
}
