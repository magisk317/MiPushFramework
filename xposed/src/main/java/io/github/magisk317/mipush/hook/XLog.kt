package io.github.magisk317.mipush.hook

import android.util.Log
import io.github.magisk317.mipush.xposed.BuildConfig
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.XposedRuntime
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.logging.LogSanitizerConfig
import io.github.magisk317.xposed.logging.XposedLogClient
import java.lang.reflect.Method

object XLog {
    private const val SOURCE = "MiPush"
    private val suppressedDebugTags = setOf(
        "HookPushNC",
    )
    private const val TRACE_ENABLED = false

    fun configure() {
        XposedLogClient.configure(
            authority = "com.xiaomi.xmsf.module.log",
            source = SOURCE,
        )
    }

    fun t(tag: String, message: String?) {
        if (!BuildConfig.DEBUG || !TRACE_ENABLED) return
        emit("T", tag, message, null)
    }

    fun d(tag: String, message: String?) {
        if (tag in suppressedDebugTags) return
        emit("D", tag, message, null)
    }

    fun i(tag: String, message: String?) {
        emit("I", tag, message, null)
    }

    fun w(tag: String, message: String?) {
        emit("W", tag, message, null)
    }

    fun e(tag: String, message: String?, throwable: Throwable?) {
        emit("E", tag, message, throwable)
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

    private fun emit(level: String, tag: String, message: String?, throwable: Throwable?) {
        val safeMessage = DefaultLogSanitizer.sanitizeIfEnabled(message ?: "")
        val priority = priorityFor(level)
        XposedRuntime.log(priority, tag, "[MiPush][$level][$tag] $safeMessage", throwable)
        if (level == "T") return
        XposedLogClient.send(
            io.github.magisk317.xposed.logging.XposedLogEvent(
                source = SOURCE,
                level = level,
                tag = tag,
                message = safeMessage,
                throwable = throwable?.stackTraceToString() ?: "",
            ),
        )
    }

    private fun safeArgs(args: Array<Any?>?): String {
        if (args == null) return "null"
        if (!LogSanitizerConfig.isEnabled()) return args.contentDeepToString()
        return args.joinToString(prefix = "[", postfix = "]") { DefaultLogSanitizer.redactArg(it) }
    }

    private fun safeArg(value: Any?): String {
        if (!LogSanitizerConfig.isEnabled()) return value.toString()
        return DefaultLogSanitizer.redactArg(value)
    }

    private fun priorityFor(level: String): Int = when (level) {
        "E" -> Log.ERROR
        "W" -> Log.WARN
        "I" -> Log.INFO
        "D" -> Log.DEBUG
        else -> Log.VERBOSE
    }
}
