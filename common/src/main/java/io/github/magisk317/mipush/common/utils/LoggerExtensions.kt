package io.github.magisk317.mipush.common.utils

import co.touchlab.kermit.Logger

inline fun <reified T : Any> T.logV(message: String, throwable: Throwable? = null) {
    val logger = Logger.withTag(T::class.java.simpleName)
    if (throwable == null) logger.v { message } else logger.v(throwable) { message }
}

inline fun <reified T : Any> T.logD(message: String, throwable: Throwable? = null) {
    val logger = Logger.withTag(T::class.java.simpleName)
    if (throwable == null) logger.d { message } else logger.d(throwable) { message }
}

inline fun <reified T : Any> T.logI(message: String, throwable: Throwable? = null) {
    val logger = Logger.withTag(T::class.java.simpleName)
    if (throwable == null) logger.i { message } else logger.i(throwable) { message }
}

inline fun <reified T : Any> T.logW(message: String, throwable: Throwable? = null) {
    val logger = Logger.withTag(T::class.java.simpleName)
    if (throwable == null) logger.w { message } else logger.w(throwable) { message }
}

inline fun <reified T : Any> T.logE(message: String, throwable: Throwable? = null) {
    val logger = Logger.withTag(T::class.java.simpleName)
    if (throwable == null) logger.e { message } else logger.e(throwable) { message }
}

inline fun <reified T : Any> T.logV(throwable: Throwable? = null, noinline message: () -> String) {
    val logger = Logger.withTag(T::class.java.simpleName)
    if (throwable == null) logger.v { message() } else logger.v(throwable) { message() }
}

inline fun <reified T : Any> T.logD(throwable: Throwable? = null, noinline message: () -> String) {
    val logger = Logger.withTag(T::class.java.simpleName)
    if (throwable == null) logger.d { message() } else logger.d(throwable) { message() }
}

inline fun <reified T : Any> T.logI(throwable: Throwable? = null, noinline message: () -> String) {
    val logger = Logger.withTag(T::class.java.simpleName)
    if (throwable == null) logger.i { message() } else logger.i(throwable) { message() }
}

inline fun <reified T : Any> T.logW(throwable: Throwable? = null, noinline message: () -> String) {
    val logger = Logger.withTag(T::class.java.simpleName)
    if (throwable == null) logger.w { message() } else logger.w(throwable) { message() }
}

inline fun <reified T : Any> T.logE(throwable: Throwable? = null, noinline message: () -> String) {
    val logger = Logger.withTag(T::class.java.simpleName)
    if (throwable == null) logger.e { message() } else logger.e(throwable) { message() }
}
