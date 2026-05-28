package io.github.magisk317.mipush.common.utils

import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier

inline fun <reified T : Any> T.logV(message: String, throwable: Throwable? = null) {
    Napier.v(message, throwable, tag = T::class.java.simpleName)
}

inline fun <reified T : Any> T.logD(message: String, throwable: Throwable? = null) {
    Napier.d(message, throwable, tag = T::class.java.simpleName)
}

inline fun <reified T : Any> T.logI(message: String, throwable: Throwable? = null) {
    Napier.i(message, throwable, tag = T::class.java.simpleName)
}

inline fun <reified T : Any> T.logW(message: String, throwable: Throwable? = null) {
    Napier.w(message, throwable, tag = T::class.java.simpleName)
}

inline fun <reified T : Any> T.logE(message: String, throwable: Throwable? = null) {
    Napier.e(message, throwable, tag = T::class.java.simpleName)
}

inline fun <reified T : Any> T.logV(throwable: Throwable? = null, noinline message: () -> String) {
    if (Napier.isEnable(LogLevel.VERBOSE, T::class.java.simpleName)) {
        Napier.v(throwable, tag = T::class.java.simpleName, message = message)
    }
}

inline fun <reified T : Any> T.logD(throwable: Throwable? = null, noinline message: () -> String) {
    if (Napier.isEnable(LogLevel.DEBUG, T::class.java.simpleName)) {
        Napier.d(throwable, tag = T::class.java.simpleName, message = message)
    }
}

inline fun <reified T : Any> T.logI(throwable: Throwable? = null, noinline message: () -> String) {
    if (Napier.isEnable(LogLevel.INFO, T::class.java.simpleName)) {
        Napier.i(throwable, tag = T::class.java.simpleName, message = message)
    }
}

inline fun <reified T : Any> T.logW(throwable: Throwable? = null, noinline message: () -> String) {
    if (Napier.isEnable(LogLevel.WARNING, T::class.java.simpleName)) {
        Napier.w(throwable, tag = T::class.java.simpleName, message = message)
    }
}

inline fun <reified T : Any> T.logE(throwable: Throwable? = null, noinline message: () -> String) {
    if (Napier.isEnable(LogLevel.ERROR, T::class.java.simpleName)) {
        Napier.e(throwable, tag = T::class.java.simpleName, message = message)
    }
}
