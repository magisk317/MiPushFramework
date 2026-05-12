package com.xiaomi.channel.commonutils.logger

interface LoggerInterface {
    fun log(str: String)

    fun log(str: String, th: Throwable)

    fun setTag(str: String)
}

interface LevelAwareLoggerInterface : LoggerInterface {
    fun log(level: Int, str: String)

    fun log(level: Int, str: String, th: Throwable)
}
