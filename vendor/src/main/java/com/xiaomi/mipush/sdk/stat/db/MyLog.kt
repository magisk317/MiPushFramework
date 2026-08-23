package com.xiaomi.mipush.sdk.stat.db

import com.xiaomi.channel.commonutils.logger.KermitLoggerCompat
object MyLog {
    private const val TAG = "PUSH_STAT"

    fun i(msg: String) {
        KermitLoggerCompat.i(message = msg, tag = TAG)
    }

    fun v(msg: String) {
        KermitLoggerCompat.d(message = msg, tag = TAG)
    }

    fun e(msg: String) {
        KermitLoggerCompat.e(message = msg, tag = TAG)
    }

    fun e(e: Throwable) {
        KermitLoggerCompat.e(message = e.message ?: "Unknown error", throwable = e, tag = TAG)
    }

    fun e(msg: String, e: Throwable) {
        KermitLoggerCompat.e(message = msg, throwable = e, tag = TAG)
    }

    fun w(msg: String) {
        KermitLoggerCompat.w(message = msg, tag = TAG)
    }
}
