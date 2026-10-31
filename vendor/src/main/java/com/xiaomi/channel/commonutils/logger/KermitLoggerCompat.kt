package com.xiaomi.channel.commonutils.logger

import io.github.magisk317.xposed.logging.XLog

/** Kermit-backed compatibility facade for the legacy vendor logger call shape. */
object KermitLoggerCompat {
    fun v(message: String, throwable: Throwable? = null, tag: String? = null) = log(tag, throwable) { XLog.tagged(it).v(throwable) { message } }
    fun d(message: String, throwable: Throwable? = null, tag: String? = null) = log(tag, throwable) { XLog.tagged(it).d(throwable) { message } }
    fun i(message: String, throwable: Throwable? = null, tag: String? = null) = log(tag, throwable) { XLog.tagged(it).i(throwable) { message } }
    fun w(message: String, throwable: Throwable? = null, tag: String? = null) = log(tag, throwable) { XLog.tagged(it).w(throwable) { message } }
    fun e(message: String, throwable: Throwable? = null, tag: String? = null) = log(tag, throwable) { XLog.tagged(it).e(throwable) { message } }

    private inline fun log(
        tag: String?,
        throwable: Throwable?,
        block: (String) -> Unit,
    ) = block(tag ?: "xmsf")
}
