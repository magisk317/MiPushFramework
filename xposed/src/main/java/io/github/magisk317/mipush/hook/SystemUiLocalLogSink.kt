package io.github.magisk317.mipush.hook

import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.logging.LogEvent
import java.io.File

/**
 * SystemUI cannot rely on the XMSF ContentProvider remaining alive. Persist a small
 * process-local jsonl so a later root export can recover app-icon diagnostics even
 * when XMSF was killed between render and capture.
 */
internal object SystemUiLocalLogSink {
    private const val PROCESS = "com.android.systemui"
    private const val DIR_NAME = "mipush-hook"
    private const val FILE_NAME = "runtime.hook.systemui.jsonl"
    private const val PREV_FILE_NAME = "runtime.hook.systemui.prev.jsonl"
    private const val MAX_BYTES = 512L * 1024L
    private const val FAILURE_REPORT_INTERVAL_MS = 60_000L
    private val writeLock = Any()

    fun append(event: LogEvent) {
        val processName = currentProcessName()
        if (!processName.startsWith(PROCESS)) {
            return
        }
        val dir = logDir() ?: return
        val file = File(dir, FILE_NAME)
        val line = buildString {
            append('{')
            appendJson("level", event.level.shortName)
            append(',')
            appendJson("tag", event.tag)
            append(',')
            appendJson("message", event.message)
            append(',')
            appendJson("route", event.route.orEmpty())
            append(',')
            appendJson("processName", processName)
            append("}\n")
        }
        synchronized(writeLock) {
            runCatching {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                if (file.exists() && file.length() > MAX_BYTES) {
                    val rotated = File(dir, PREV_FILE_NAME)
                    rotated.delete()
                    file.renameTo(rotated)
                }
                file.appendText(line)
            }.onFailure {
                // Report persistence failures through the relay (bounded): if the local file
                // and the ContentProvider relay are both down, no SystemUI evidence exists.
                failedAppends += 1
                val now = System.currentTimeMillis()
                if (failedAppends <= 3 || now - lastFailureReportMs >= FAILURE_REPORT_INTERVAL_MS) {
                    lastFailureReportMs = now
                    // Report through logcat directly: routing back through XLog would
                    // re-enter this sink while the local file is unwritable.
                    android.util.Log.w("SystemUiLocalLogSink",
                        "local append failed count=$failedAppends error=${it.javaClass.simpleName}: ${it.message}")
                }
            }
        }
    }

    private var failedAppends = 0L
    private var lastFailureReportMs = 0L

    internal fun logDir(): File? {
        val filesDir = currentApplication()?.filesDir ?: return null
        return File(filesDir, DIR_NAME)
    }

    private fun currentProcessName(): String = runCatching {
        File("/proc/self/cmdline").readBytes()
            .toString(Charsets.UTF_8)
            .substringBefore('\u0000')
    }.getOrDefault("")

    private fun StringBuilder.appendJson(key: String, value: String) {
        append('"').append(key).append('"').append(':').append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}
