package io.github.magisk317.mipush.hook

import android.content.ContentValues
import android.net.Uri
import android.os.Process
import android.util.Log
import io.github.magisk317.mipush.xposed.BuildConfig
import io.github.magisk317.mipush.xposed.MethodHookParam
import io.github.magisk317.mipush.xposed.XposedRuntime
import io.github.magisk317.mipush.xposed.currentApplication
import java.lang.reflect.Method

import java.util.concurrent.Executors

object XLog {
    private const val SOURCE = "MiPush"
    private val logExecutor = Executors.newSingleThreadExecutor()
    private val FRAMEWORK_LOG_URI: Uri = Uri.parse("content://com.xiaomi.xmsf.module.log/entry")
    private val suppressedDebugTags = setOf(
        "SystemNotificationManager",
        "HookPushNC",
    )
    private const val TRACE_ENABLED = false

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
        d(tag, "${method.name} called with ${args.contentDeepToString()}")
        if (stackTrace) {
            d(tag, Log.getStackTraceString(Throwable()))
        }
        if (hasThrowable()) {
            e(tag, "${method.name} thrown", throwable)
        } else if (method is Method && (method as Method).returnType != Void.TYPE) {
            d(tag, "${method.name} return $result")
        }
        d(tag, "╚═══════════════════════════════════════════════════════")
    }

    private fun emit(level: String, tag: String, message: String?, throwable: Throwable?) {
        val priority = priorityFor(level)
        XposedRuntime.log(priority, tag, "[MiPush][$level][$tag] $message", throwable)
        logExecutor.execute {
            runCatching {
                relayToFramework(level, tag, message, throwable)
            }
        }
    }

    private fun relayToFramework(level: String, tag: String, message: String?, throwable: Throwable?) {
        if (level == "T") return
        val application = currentApplication() ?: return
        runCatching {
            val values = ContentValues().apply {
                put("source", SOURCE)
                put("level", level)
                put("tag", tag)
                put("message", message ?: "")
                put("throwable", throwable?.stackTraceToString() ?: "")
                put("package_name", application.packageName ?: "")
                put("process_name", runCatching { android.app.Application.getProcessName() }.getOrNull() ?: "")
                put("pid", Process.myPid())
                put("uid", Process.myUid())
            }
            application.contentResolver.insert(FRAMEWORK_LOG_URI, values)
        }
    }

    private fun priorityFor(level: String): Int = when (level) {
        "E" -> Log.ERROR
        "W" -> Log.WARN
        "I" -> Log.INFO
        "D" -> Log.DEBUG
        else -> Log.VERBOSE
    }
}
