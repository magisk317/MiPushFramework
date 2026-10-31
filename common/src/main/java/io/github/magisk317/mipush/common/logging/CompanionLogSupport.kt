package io.github.magisk317.mipush.common.logging

import io.github.magisk317.xposed.logging.XLog

/**
 * Pins the log tag for components that log from a `companion object` scope.
 *
 * `logI`/`logW`/`logE`/`logD` build the tag from `javaClass.simpleName` of the reified receiver.
 * Inside a companion that receiver is the companion's own type, whose simple name is `Companion`
 * before minification and an opaque R8 short name afterwards — so those lines carry no component
 * tag in the shipped runtime log, and `@StableLogTag` on the outer class cannot help: the outer
 * name is not what reaches the tag.
 *
 * Implementing this on the companion shadows the extensions with the component's own name, so
 * every existing call site keeps working unchanged.
 */
interface CompanionLogSupport {
    /** The component name to emit, normally the enclosing class's simple name. */
    val logTag: String

    fun logD(message: String, throwable: Throwable? = null) {
        XLog.tagged(logTag).d(message, throwable)
    }

    fun logI(message: String, throwable: Throwable? = null) {
        XLog.tagged(logTag).i(message, throwable)
    }

    fun logW(message: String, throwable: Throwable? = null) {
        XLog.tagged(logTag).w(message, throwable)
    }

    fun logE(message: String, throwable: Throwable? = null) {
        XLog.tagged(logTag).e(message, throwable)
    }
}
