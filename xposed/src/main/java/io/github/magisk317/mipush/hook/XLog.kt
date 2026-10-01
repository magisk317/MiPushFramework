package io.github.magisk317.mipush.hook

import android.util.Log
import android.os.SystemClock
import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.common.logging.LogRoute
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.logging.FixedWindowLogLimiter
import io.github.magisk317.xposed.logging.LogEvent
import io.github.magisk317.xposed.logging.LogSink
import io.github.magisk317.xposed.logging.XLog as SharedXLog
import io.github.magisk317.xposed.logging.XposedLogClient
import io.github.magisk317.xposed.logging.XposedLogEvent
import java.lang.reflect.Method

/** MiPush adapter that keeps existing tag call sites while using the shared XLog backend. */
object XLog {
    private const val SOURCE = "mipush"
    private const val HOOK_PUSH_NC_TAG = "HookPushNC"
    private val debugLimiter = FixedWindowLogLimiter(
        maxEvents = 12,
        windowMs = 30_000L,
        maxTrackedKeys = 64,
    )
    private val moduleSink = LogSink { event ->
        XposedLogClient.send(
            XposedLogEvent(
                source = SOURCE,
                level = event.level.shortName,
                tag = event.tag,
                message = event.message,
                throwable = event.throwableText.orEmpty(),
                route = event.route,
                force = event.force,
                sensitive = event.sensitive,
                sanitized = true,
            ),
        )
        SystemUiLocalLogSink.append(event)
    }

    fun configure() {
        resetForLifecycle()
        XposedLogClient.configure(
            authority = "com.xiaomi.xmsf.module.log",
            source = SOURCE,
        )
        SharedXLog.configure(
            tag = SOURCE,
            logLevel = if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO,
            logToXposed = false,
            sink = moduleSink,
        )
    }

    fun t(tag: String, message: String?) {
        if (!BuildConfig.DEBUG) return
        emit(Log.VERBOSE, null, tag, message, force = false)
    }

    fun d(tag: String, message: String?) {
        if (tag == HOOK_PUSH_NC_TAG && !allowHookPushNcDebug()) return
        emit(Log.DEBUG, null, tag, message, force = false)
    }

    fun i(tag: String, message: String?) {
        emit(Log.INFO, null, tag, message, force = false)
    }

    fun w(tag: String, message: String?) {
        emit(Log.WARN, null, tag, message, force = true)
    }

    fun e(tag: String, message: String?, throwable: Throwable?) {
        emit(Log.ERROR, null, tag, message, force = true, throwable = throwable)
    }

    fun d(route: LogRoute, tag: String, message: String?) {
        if (tag == HOOK_PUSH_NC_TAG && !allowHookPushNcDebug()) return
        emit(Log.DEBUG, route, tag, message, force = false)
    }

    /** Rate-limited DEBUG for hook methods that can run once per framework call. */
    fun dLimited(route: LogRoute, tag: String, message: String?) {
        if (SharedXLog.getLogLevel() > Log.DEBUG || !allowLimitedDebug()) return
        emit(Log.DEBUG, route, tag, message, force = false)
    }

    fun i(route: LogRoute, tag: String, message: String?) {
        emit(Log.INFO, route, tag, message, force = false)
    }

    fun w(route: LogRoute, tag: String, message: String?) {
        emit(Log.WARN, route, tag, message, force = true)
    }

    fun e(route: LogRoute, tag: String, message: String?, throwable: Throwable?) {
        emit(Log.ERROR, route, tag, message, force = true, throwable = throwable)
    }

    fun MethodHookParam.logMethod(tag: String, stackTrace: Boolean = false) {
        d(tag, "╔═══════════════════════════════════════════════════════")
        d(tag, method.toString())
        d(tag, "${method.name} called with ${safeArgs(args)}")
        if (stackTrace) {
            d(tag, Log.getStackTraceString(Throwable()))
        }
        if (hasThrowable()) {
            e(tag, "${method.name} thrown", throwable)
        } else if (method is Method && (method as Method).returnType != Void.TYPE) {
            d(tag, "${method.name} return ${safeArg(result)}")
        }
        d(tag, "╚═══════════════════════════════════════════════════════")
    }

    private fun emit(
        priority: Int,
        route: LogRoute?,
        tag: String,
        message: String?,
        force: Boolean,
        throwable: Throwable? = null,
    ) {
        val taggedMessage = "[$tag] ${message.orEmpty()}"
        val throwableArgs = throwable?.let { arrayOf<Any?>(it) } ?: emptyArray()
        SharedXLog.log(
            priority = priority,
            route = route?.id ?: LogRoute.HOOK.id,
            force = force,
            sensitive = true,
            message = taggedMessage,
            *throwableArgs,
        )
    }

    internal fun resetForLifecycle() {
        debugLimiter.reset()
    }

    private fun allowLimitedDebug(): Boolean =
        debugLimiter.tryAcquire("limited", SystemClock.elapsedRealtime()).allowed

    private fun allowHookPushNcDebug(): Boolean =
        debugLimiter.tryAcquire("hook_push_nc", SystemClock.elapsedRealtime()).allowed

    private fun safeArgs(args: Array<Any?>?): String {
        if (args == null) return "null"
        return args.joinToString(prefix = "[", postfix = "]") { DefaultLogSanitizer.redactArg(it) }
    }

    private fun safeArg(value: Any?): String = DefaultLogSanitizer.redactArg(value)
}
