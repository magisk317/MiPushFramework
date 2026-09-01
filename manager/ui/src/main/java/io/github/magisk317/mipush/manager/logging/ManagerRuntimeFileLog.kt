package io.github.magisk317.mipush.manager.logging

import android.annotation.SuppressLint
import android.content.Context
import co.touchlab.kermit.Severity
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.mipush.common.logging.DailyRouteLogQuota
import io.github.magisk317.mipush.diagnostics.StructuredLogCore
import io.github.magisk317.mipush.diagnostics.DailyRouteLogNamingPolicy
import io.github.magisk317.xposed.logging.LogSink
import io.github.magisk317.xposed.logging.LoggingKit
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
    private const val PRUNE_INTERVAL_MS = 30L * 60L * 1000L
    private val dailyDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val logTimestampFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val writeLock = Any()
    // init() stores only applicationContext for this process-wide file logger.
    @SuppressLint("StaticFieldLeak")
    private val appContext = AtomicReference<Context?>(null)

    @Volatile
    private var retentionDays: Int = DEFAULT_RETENTION_DAYS

    @Volatile
    private var lastPruneAtMs: Long = 0L

    fun init(context: Context) {
        val resolved = context.applicationContext ?: context
        appContext.set(resolved)
        runCatching {
            val now = Date()
            synchronized(writeLock) {
                pruneExpired(resolved, now)
                lastPruneAtMs = now.time
            }
            LoggingKit.init(
                defaultTag = "MiPushManager",
                minSeverity = Severity.Verbose,
                sink = managerLogSink(resolved),
            )
        }.onFailure {
            android.util.Log.e("MiPushManager", "Manager Kermit file log init failed", it)
        }
    }

    fun setRetentionDays(days: Int) {
        val resolvedDays = days.coerceAtLeast(MIN_RETENTION_DAYS)
        retentionDays = resolvedDays
        appContext.get()?.let { context ->
            val now = Date()
            synchronized(writeLock) {
                // Retention changes are an explicit maintenance request and must
                // not wait for the normal append throttle.
                pruneExpired(context, now)
                lastPruneAtMs = now.time
            }
        }
    }

    fun getLogDir(context: Context): File {
        val dir = File((context.applicationContext ?: context).filesDir, "log")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listExportableLogFiles(context: Context): List<File> {
        synchronized(writeLock) {
            return getLogDir(context).listFiles()
                .orEmpty()
                .filter { it.isFile && DailyRouteLogNamingPolicy.isRouteFile(it.name, ROUTE) }
                .sortedBy { it.name }
        }
    }

    fun clear(context: Context) {
        synchronized(writeLock) {
            val dir = getLogDir(context)
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            lastPruneAtMs = System.currentTimeMillis()
        }
    }

    private fun managerLogSink(context: Context): LogSink = LogSink { event ->
        append(
            context = context,
            level = event.level.shortName,
            tag = event.tag,
            message = event.message,
            throwable = event.throwableText.orEmpty(),
            alreadySanitized = true,
        )
    }

    private fun append(
        context: Context,
        level: String,
        tag: String,
        message: String,
        throwable: String,
        alreadySanitized: Boolean = false,
    ) {
        val now = Date()
        val thread = Thread.currentThread()
        val line = StructuredLogCore.encode(
            StructuredLogCore.stringField(
                "time",
                Instant.ofEpochMilli(now.time).atZone(ZoneId.systemDefault()).format(logTimestampFormatter),
            ),
            StructuredLogCore.stringField("level", level),
            StructuredLogCore.stringField("tag", tag),
            StructuredLogCore.stringField(
                "message",
                if (alreadySanitized) message else DefaultLogSanitizer.sanitizeIfEnabled(message),
            ),
            StructuredLogCore.stringField(
                "throwable",
                if (alreadySanitized) throwable else DefaultLogSanitizer.sanitizeIfEnabled(throwable),
                include = throwable.isNotBlank(),
            ),
            StructuredLogCore.stringField("route", ROUTE),
            StructuredLogCore.stringField("packageName", context.packageName),
            StructuredLogCore.stringField("processName", currentProcessName()),
            StructuredLogCore.numberField("pid", android.os.Process.myPid()),
            StructuredLogCore.stringField("threadName", thread.name.orEmpty()),
        ) + "\n"
        synchronized(writeLock) {
            runCatching {
                val logDir = getLogDir(context)
                if (shouldPrune(now.time)) {
                    pruneExpired(context, now)
                    lastPruneAtMs = now.time
                }
                val day = Instant.ofEpochMilli(now.time).atZone(ZoneId.systemDefault())
                    .format(dailyDateFormatter)
                if (DailyRouteLogQuota.ensureCapacity(
                        logDir = logDir,
                        route = ROUTE,
                        currentDay = day,
                        incomingBytes = line.toByteArray(Charsets.UTF_8).size.toLong(),
                    )
                ) {
                    File(logDir, "runtime.$ROUTE.$day.jsonl").appendText(line)
                }
            }
        }
    }

    private fun shouldPrune(nowMs: Long): Boolean {
        return lastPruneAtMs <= 0L || nowMs - lastPruneAtMs >= PRUNE_INTERVAL_MS
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
        android.app.Application.getProcessName()
    }.getOrDefault("")
}
