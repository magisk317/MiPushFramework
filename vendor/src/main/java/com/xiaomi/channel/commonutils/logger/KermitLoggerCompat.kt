package com.xiaomi.channel.commonutils.logger

import co.touchlab.kermit.Logger

/** Kermit-backed compatibility facade for the legacy vendor logger call shape. */
object KermitLoggerCompat {
    fun v(message: String, throwable: Throwable? = null, tag: String? = null) = log(tag, throwable) { Logger.withTag(it).v(throwable) { message } }
    fun d(message: String, throwable: Throwable? = null, tag: String? = null) = log(tag, throwable) { Logger.withTag(it).d(throwable) { message } }
    fun i(message: String, throwable: Throwable? = null, tag: String? = null) = log(tag, throwable) { Logger.withTag(it).i(throwable) { message } }
    fun w(message: String, throwable: Throwable? = null, tag: String? = null) = log(tag, throwable) { Logger.withTag(it).w(throwable) { message } }
    fun e(message: String, throwable: Throwable? = null, tag: String? = null) = log(tag, throwable) { Logger.withTag(it).e(throwable) { message } }

    private inline fun log(
        tag: String?,
        throwable: Throwable?,
        block: (String) -> Unit,
    ) = block(tag ?: "MiPush")
}
