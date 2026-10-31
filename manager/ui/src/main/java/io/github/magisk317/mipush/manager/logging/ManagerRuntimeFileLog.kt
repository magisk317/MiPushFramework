package io.github.magisk317.mipush.manager.logging

import android.annotation.SuppressLint
import android.content.Context
import io.github.magisk317.xposed.logging.DailyJsonlLogStore
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.logging.JsonLineEncoder
import io.github.magisk317.xposed.logging.JsonLineField
import io.github.magisk317.xposed.logging.LogSink
import io.github.magisk317.xposed.logging.LogLevel
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    private const val MANAGER_STEM = "runtime.manager"
    private const val PRUNE_INTERVAL_MS = 30L * 60L * 1000L
    private val logTimestampFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val writeLock = Any()

    private val fileStore = DailyJsonlLogStore()
    // init() stores only applicationContext for this process-wide file logger.
    @SuppressLint("StaticFieldLeak")
    private val appContext = AtomicReference<Context?>(null)

    @Volatile
    private var retentionDays: Int = MagiskLogPolicy.DEFAULT_LOG_RETENTION_DAYS

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
            XLog.configure(
                tag = "MiPushManager",
                logLevel = LogLevel.VERBOSE.priority,
                sink = managerLogSink(resolved),
            )
        }.onFailure {
            android.util.Log.e("MiPushManager", "Manager file log init failed", it)
        }
    }

    fun setRetentionDays(days: Int) {
        val resolvedDays = MagiskLogPolicy.retentionDays(days)
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
                .filter { it.isFile && isManagerRouteFile(it.name) }
                .sortedWith(compareBy(DailyJsonlLogStore.DAILY_FILE_ORDER) { it.name })
        }
    }

    fun clear(context: Context) {
        synchronized(writeLock) {
            val dir = getLogDir(context)
            dir.listFiles()?.forEach { file ->
                val cleared = runCatching { file.delete() }.getOrDefault(false)
                if (!cleared && file.exists()) {
                    android.util.Log.w("MiPushManager", "log file survived clear: ${file.name}")
                }
            }
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
        val line = JsonLineEncoder.encode(
            JsonLineField.string(
                "time",
                Instant.ofEpochMilli(now.time).atZone(ZoneId.systemDefault()).format(logTimestampFormatter),
            ),
            JsonLineField.string("level", level),
            JsonLineField.string("tag", tag),
            JsonLineField.string(
                "message",
                if (alreadySanitized) message else DefaultLogSanitizer.sanitizeIfEnabled(message),
            ),
            JsonLineField.string(
                "throwable",
                if (alreadySanitized) throwable else DefaultLogSanitizer.sanitizeIfEnabled(throwable),
                include = throwable.isNotBlank(),
            ),
            JsonLineField.string("route", ROUTE),
            JsonLineField.string("packageName", context.packageName),
            JsonLineField.string("processName", currentProcessName()),
            JsonLineField.number("pid", android.os.Process.myPid()),
            JsonLineField.string("threadName", thread.name.orEmpty()),
        ) + "\n"
        synchronized(writeLock) {
            runCatching {
                val logDir = getLogDir(context)
                if (shouldPrune(now.time)) {
                    pruneExpired(context, now)
                    lastPruneAtMs = now.time
                }
                fileStore.appendLine(logDir, listOf(MANAGER_STEM), now.time, line)
            }.onFailure {
                // This is the file sink itself: a failed write cannot be reported through the same
                // file without recursing, so it stays a documented no-op.
            }
        }
    }

    private fun shouldPrune(nowMs: Long): Boolean {
        return lastPruneAtMs <= 0L || nowMs - lastPruneAtMs >= PRUNE_INTERVAL_MS
    }

    private fun pruneExpired(context: Context, now: Date) {
        // Retention pruning is best-effort: a file that will not go (permissions or a
        // concurrent writer) is picked up again by the next sweep.
        fileStore.pruneExpired(
            logDir = getLogDir(context),
            nowMs = now.time,
            retentionDays = MagiskLogPolicy.retentionDays(retentionDays),
            isManagedFile = { true },
        )
    }

    private fun isManagerRouteFile(name: String): Boolean =
        DailyJsonlLogStore.parseDailyFileName(name)?.base == MANAGER_STEM

    private fun currentProcessName(): String = runCatching {
        android.app.Application.getProcessName()
    }.getOrDefault("")
}
