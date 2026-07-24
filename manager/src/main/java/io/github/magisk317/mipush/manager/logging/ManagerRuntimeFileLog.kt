package io.github.magisk317.mipush.manager.logging

import android.content.Context
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Manager-host FileAntilog aligned with xmsf [io.github.magisk317.mipush.utils.LogUtils]:
 * daily jsonl under files/log, route segment "manager".
 *
 * Export merges these files into the runtime log bundle as app/log/runtime.manager.*.jsonl.
 */
object ManagerRuntimeFileLog {
    private const val ROUTE = "manager"
    private const val MIN_RETENTION_DAYS = 1
    private const val DEFAULT_RETENTION_DAYS = 2
    private val dailyDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val logTimestampFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val writeLock = Any()
    private val appContext = AtomicReference<Context?>(null)

    @Volatile
    private var retentionDays: Int = DEFAULT_RETENTION_DAYS

    fun init(context: Context) {
        val resolved = context.applicationContext ?: context
        appContext.set(resolved)
        Napier.takeLogarithm()
        runCatching {
            pruneExpired(resolved, Date())
            Napier.base(FileAntilog(resolved))
        }.onFailure {
            android.util.Log.e("MiPushManager", "Manager FileAntilog init failed", it)
        }
    }

    fun setRetentionDays(days: Int) {
        retentionDays = days.coerceAtLeast(MIN_RETENTION_DAYS)
        appContext.get()?.let { pruneExpired(it, Date()) }
    }

    fun getLogDir(context: Context): File {
        val dir = File((context.applicationContext ?: context).filesDir, "log")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listExportableLogFiles(context: Context): List<File> {
        return getLogDir(context).listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith("runtime.") && it.name.endsWith(".jsonl") }
            .sortedBy { it.name }
    }

    fun clear(context: Context) {
        val dir = getLogDir(context)
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private class FileAntilog(private val context: Context) : Antilog() {
        override fun performLog(
            priority: LogLevel,
            tag: String?,
            throwable: Throwable?,
            message: String?,
        ) {
            append(
                context = context,
                level = priority.toShortLetter(),
                tag = tag.orEmpty(),
                message = message.orEmpty(),
                throwable = throwable?.stackTraceToString().orEmpty(),
            )
        }

        private fun LogLevel.toShortLetter(): String = when (this) {
            LogLevel.VERBOSE -> "V"
            LogLevel.DEBUG -> "D"
            LogLevel.INFO -> "I"
            LogLevel.WARNING -> "W"
            LogLevel.ERROR -> "E"
            LogLevel.ASSERT -> "A"
        }
    }

    private fun append(
        context: Context,
        level: String,
        tag: String,
        message: String,
        throwable: String,
    ) {
        val now = Date()
        val thread = Thread.currentThread()
        val line = buildString {
            append('{')
            append("\"time\":").appendJsonString(
                Instant.ofEpochMilli(now.time).atZone(ZoneId.systemDefault()).format(logTimestampFormatter),
            )
            append(",\"level\":").appendJsonString(level)
            append(",\"tag\":").appendJsonString(tag)
            append(",\"message\":").appendJsonString(DefaultLogSanitizer.sanitizeIfEnabled(message))
            if (throwable.isNotBlank()) {
                append(",\"throwable\":").appendJsonString(
                    DefaultLogSanitizer.sanitizeIfEnabled(throwable),
                )
            }
            append(",\"route\":").appendJsonString(ROUTE)
            append(",\"packageName\":").appendJsonString(context.packageName)
            append(",\"processName\":").appendJsonString(currentProcessName())
            append(",\"pid\":").append(android.os.Process.myPid())
            append(",\"threadName\":").appendJsonString(thread.name.orEmpty())
            append('}')
            append('\n')
        }
        synchronized(writeLock) {
            runCatching {
                val logDir = getLogDir(context)
                pruneExpired(context, now)
                val day = Instant.ofEpochMilli(now.time).atZone(ZoneId.systemDefault())
                    .format(dailyDateFormatter)
                File(logDir, "runtime.$ROUTE.$day.jsonl").appendText(line)
            }
        }
    }

    private fun pruneExpired(context: Context, now: Date) {
        val cutoff = Calendar.getInstance(Locale.US).apply {
            time = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(retentionDays.coerceAtLeast(MIN_RETENTION_DAYS) - 1))
        }.timeInMillis
        getLogDir(context).listFiles().orEmpty().forEach { file ->
            if (!file.isFile) return@forEach
            val day = Regex("""runtime(?:\.[^.]+)*\.(\d{4}-\d{2}-\d{2})\.jsonl$""")
                .find(file.name)?.groupValues?.getOrNull(1)
            val fileTime = day?.let {
                runCatching {
                    LocalDate.parse(it, dailyDateFormatter)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull()
            } ?: file.lastModified()
            if (fileTime > 0L && fileTime < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    private fun currentProcessName(): String = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            android.app.Application.getProcessName()
        } else {
            ""
        }
    }.getOrDefault("")

    private fun StringBuilder.appendJsonString(value: String): StringBuilder {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
        return this
    }
}
