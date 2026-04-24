package com.xiaomi.push.log
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.logger.LoggerInterface

class MIPushDebugLog(
    private var sUserLogInterface: LoggerInterface?,
    private var sPushLogFileInterface: LoggerInterface?
) : LoggerInterface {
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
}
