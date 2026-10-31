package io.github.magisk317.mipush.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.xiaomi.xmsf.R
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.logging.DailyJsonlLogStore
import io.github.magisk317.xposed.logging.JsonLineEncoder
import io.github.magisk317.xposed.logging.JsonLineField
import io.github.magisk317.xposed.logging.LogLevel
import io.github.magisk317.xposed.logging.LogSink
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import io.github.magisk317.xposed.logging.XLog as SharedXLog
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Calendar
import java.util.Date
import java.util.Locale

object LogUtils {
    private const val MAX_READ_LINES = 2000
    private const val DEFAULT_ROUTE = "app"
    private val dailyDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val logTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val dailyRuntimeLogPattern = Regex("""^runtime(?:\.[A-Za-z0-9_.-]+)?\.\d{4}-\d{2}-\d{2}(?:-\d+)?\.jsonl$""")
    private val dailyLogDateRegex = Regex("""^runtime(?:\.[^.]+)*\.(\d{4}-\d{2}-\d{2})(?:-\d+)?\.jsonl$""")
    private val redundantAppRouteRuntimeLogPattern = Regex("""^runtime\.app\.\d{4}-\d{2}-\d{2}(?:-\d+)?\.jsonl$""")
    private val legacyTextLogPattern = Regex("""^(logs_\d{4}-\d{2}-\d{2}|runtime(?:\.[A-Za-z0-9_.-]+)?)\.(txt|log)$""")
    private val legacyModuleTextLogPattern = Regex("""^[A-Za-z0-9_.-]+_\d{4}-\d{2}-\d{2}\.txt$""")
    private const val FULL_PRUNE_INTERVAL_MS = 30L * 60L * 1000L

    private fun LogLevel.toAndroidPriority(): Int = priority

    @Volatile
    private var lastFullPruneAtMs: Long = 0L

    // Unified single-letter level mapping aligned with android.util.Log priorities.
    // Module-side XLog.d/.i/.w/.e writes D/I/W/E through the same content provider,
    // so normalizing here keeps runtime.*.jsonl consistent across both producers.
    private fun LogLevel.toShortLetter(): String = shortName

    private val writeLock = Any()

    private val fileStore = DailyJsonlLogStore()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var retentionDays: Int = MagiskLogPolicy.DEFAULT_LOG_RETENTION_DAYS

    @Volatile
    private var minLogLevel: LogLevel = LogLevel.VERBOSE

    data class RuntimeLogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String,
        val throwable: String = "",
        val route: String = "app",
        val packageName: String = "",
        val processName: String = "",
        val pid: Int = 0,
        val threadName: String = "",
    )

    data class RuntimeLogFileInfo(
        val name: String,
        val sizeBytes: Long,
        val lineCount: Int,
        val firstTimestamp: Long?,
        val lastTimestamp: Long?,
    )

    data class RuntimeLogFileSummary(
        val fileCount: Int,
        val totalBytes: Long,
        val entryCount: Int,
        val firstTimestamp: Long?,
        val lastTimestamp: Long?,
        val files: List<RuntimeLogFileInfo>,
    )

    data class RuntimeLogFileContent(
        val name: String,
        val sizeBytes: Long,
        val displayedLineCount: Int,
        val truncated: Boolean,
        val text: String,
    )

    /**
     * @param verboseLogging whether this build is a capture channel (debug / CI). A release build
     *   installs no file sink at all: nothing is written to `files/log`, so there is no runtime
     *   log to retain, prune or export. Logcat still receives the level-filtered stream.
     */
    @JvmStatic
    fun init(context: Context, verboseLogging: Boolean = MagiskLogPolicy.current().verboseLogging) {
        val resolved = context.applicationContext ?: context
        appContext = resolved
        minLogLevel = if (verboseLogging) LogLevel.VERBOSE else LogLevel.WARN
        runCatching {
            deleteLegacyTextLogFiles(resolved)
            pruneAllLogArtifacts(resolved, Date(), force = true)
            if (!verboseLogging) {
                // Release: keep the logger configured for logcat only. The kit discards events
                // when no sink is installed, so no file is ever created for them.
                SharedXLog.configure(
                    tag = "xmsf",
                    logLevel = minLogLevel.toAndroidPriority(),
                    logToXposed = false,
                    sink = null,
                )
                return@runCatching
            }
            val sink = fileLogSink(resolved)
            SharedXLog.configure(
                tag = "xmsf",
                logLevel = minLogLevel.toAndroidPriority(),
                logToXposed = false,
                sink = sink,
            )
        }.onFailure {
            // 文件日志初始化失败时不回退到会泄露日志的默认 writer。
            android.util.Log.e("xmsf", "XLog file log init failed, logs will be discarded", it)
        }
    }

    @JvmStatic
    fun setMinLogLevel(level: LogLevel) {
        minLogLevel = level
        SharedXLog.setLogLevel(level.toAndroidPriority())
    }

    fun setRetentionDays(days: Int) {
        updateRetentionDays(days, appContext)
    }

    internal fun setRetentionDays(context: Context, days: Int) {
        val resolved = context.applicationContext ?: context
        appContext = resolved
        updateRetentionDays(days, resolved)
    }

    internal fun resetForTest() {
        synchronized(writeLock) {
            appContext = null
            retentionDays = MagiskLogPolicy.DEFAULT_LOG_RETENTION_DAYS
            lastFullPruneAtMs = 0L
        }
        SharedXLog.resetForTest()
    }

    private fun fileLogSink(context: Context): LogSink = LogSink { event ->
        appendRuntimeLog(
            context = context,
            level = event.level.shortName,
            tag = event.tag,
            message = event.message,
            throwable = event.throwableText.orEmpty(),
            route = event.route ?: DEFAULT_ROUTE,
            packageName = context.packageName,
            processName = currentProcessName(),
            alreadySanitized = true,
        )
    }

    fun appendModuleLog(
        context: Context,
        source: String,
        level: String,
        tag: String,
        packageName: String,
        processName: String,
        message: String,
        throwable: String,
        alreadySanitized: Boolean = false,
    ) {
        appendRuntimeLog(
            context = context.applicationContext ?: context,
            level = level.ifBlank { "I" },
            tag = tag.ifBlank { "unknown" },
            message = message,
            throwable = throwable,
            route = sanitizeSegment(source.ifBlank { "module" }),
            packageName = packageName,
            processName = processName,
            alreadySanitized = alreadySanitized,
        )
    }

    private fun appendRuntimeLog(
        context: Context,
        level: String,
        tag: String,
        message: String,
        throwable: String,
        route: String,
        packageName: String,
        processName: String,
        alreadySanitized: Boolean = false,
    ) {
        val now = Date()
        val thread = Thread.currentThread()
        val entry = RuntimeLogEntry(
            timestamp = now.time,
            level = level,
            tag = tag,
            message = if (alreadySanitized) message else DefaultLogSanitizer.sanitizeIfEnabled(message),
            throwable = if (alreadySanitized) throwable else DefaultLogSanitizer.sanitizeIfEnabled(throwable),
            route = route,
            packageName = packageName,
            processName = processName,
            pid = android.os.Process.myPid(),
            threadName = thread.name.orEmpty(),
        )
        val line = encodeJsonLine(entry) + "\n"
        val logDir = LogBundleExporter.getLogDir(context)
        synchronized(writeLock) {
            runCatching {
                if (!logDir.exists()) logDir.mkdirs()
                pruneAllLogArtifacts(context, now, force = false)
                val routeName = sanitizeSegment(route)
                fileStore.appendLine(logDir, listOf(routeStem(routeName)), now.time, line)
            }.onFailure { }
        }
    }

    @JvmStatic
    fun getLogFolder(context: Context): String {
        return LogBundleExporter.getLogDir(context).absolutePath
    }

    @JvmStatic
    fun getCrashFolder(context: Context): String {
        return LogBundleExporter.getCrashDir(context).absolutePath
    }

    @JvmStatic
    fun clearLog(context: Context) {
        val result = LogBundleExporter.clearLogFolders(context)
        if (!result.success) {
            Toast.makeText(
                context,
                context.getString(R.string.log_share_error, result.details),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        Toast.makeText(context, context.getString(R.string.log_clear_done), Toast.LENGTH_SHORT).show()
    }

    fun summarizeFiles(context: Context): RuntimeLogFileSummary {
        val files = getRuntimeLogFiles(context)
        val infos = files.mapNotNull { file ->
            runCatching { summarizeFile(file) }.getOrNull()
        }
        return RuntimeLogFileSummary(
            fileCount = infos.size,
            totalBytes = infos.sumOf { it.sizeBytes },
            entryCount = infos.sumOf { it.lineCount },
            firstTimestamp = infos.mapNotNull { it.firstTimestamp }.minOrNull(),
            lastTimestamp = infos.mapNotNull { it.lastTimestamp }.maxOrNull(),
            files = infos,
        )
    }

    fun readLogFile(context: Context, name: String, maxLines: Int = MAX_READ_LINES): RuntimeLogFileContent? {
        require(maxLines >= 0) { "maxLines must be non-negative" }
        val safeName = File(name).name
        val file = getRuntimeLogFiles(context).firstOrNull { it.name == safeName } ?: return null
        val tail = ArrayDeque<String>()
        var lineCount = 0
        file.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                lineCount += 1
                if (maxLines == 0) return@forEach
                if (tail.size == maxLines) tail.removeFirst()
                tail.addLast(line)
            }
        }
        val displayed = tail.toList()
        return RuntimeLogFileContent(
            name = file.name,
            sizeBytes = file.length(),
            displayedLineCount = displayed.size,
            truncated = lineCount > displayed.size,
            text = displayed.joinToString("\n"),
        )
    }

    fun deleteRuntimeLogFile(context: Context, name: String): Boolean {
        val safeName = File(name).name
        val file = getRuntimeLogFiles(context).firstOrNull { it.name == safeName } ?: return false
        return file.delete()
    }

    fun deleteLegacyTextLogFiles(context: Context): Int {
        val dirs = listOf(
            LogBundleExporter.getLogDir(context),
            File(LogBundleExporter.getLogDir(context), "modules"),
            File(context.cacheDir, "logs"),
        )
        var deleted = 0
        dirs.forEach { dir ->
            dir.listFiles().orEmpty().forEach { file ->
                if (file.isFile &&
                    (legacyTextLogPattern.matches(file.name) || legacyModuleTextLogPattern.matches(file.name)) &&
                    file.delete()
                ) {
                    deleted += 1
                }
            }
        }
        return deleted
    }

    internal fun isRedundantAppRouteRuntimeLog(file: File): Boolean {
        return file.isFile && redundantAppRouteRuntimeLogPattern.matches(file.name)
    }

    /**
     * Prune every log-related artifact owned by this package under the retention window:
     * runtime jsonl, crash files, export zips/staging, legacy cache logs, MiPush SDK logs.
     */
    internal fun pruneAllLogArtifacts(context: Context, now: Date = Date(), force: Boolean = true) {
        val resolved = context.applicationContext ?: context
        val nowMs = now.time
        // Non-forced maintenance is called from every append. Keep that hot path
        // to a timestamp check; directory scans happen at most once per interval.
        if (!force && lastFullPruneAtMs > 0L && nowMs - lastFullPruneAtMs < FULL_PRUNE_INTERVAL_MS) {
            return
        }
        lastFullPruneAtMs = nowMs
        pruneExpiredRuntimeLogs(LogBundleExporter.getLogDir(resolved), now)
        val modulesDir = File(LogBundleExporter.getLogDir(resolved), "modules")
        if (modulesDir.isDirectory) {
            pruneExpiredRuntimeLogs(modulesDir, now)
        }
        LogBundleExporter.pruneExpiredArtifacts(resolved, retentionCutoffMillis(now))
    }

    internal fun retentionCutoffMillis(now: Date = Date()): Long {
        return Calendar.getInstance(Locale.US).apply {
            time = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(MagiskLogPolicy.retentionDays(retentionDays) - 1))
        }.timeInMillis
    }

    private fun getRuntimeLogFiles(context: Context): List<File> {
        val logDir = LogBundleExporter.getLogDir(context)
        return logDir.listFiles()
            .orEmpty()
            .filter { it.isFile && dailyRuntimeLogPattern.matches(it.name) && !isRedundantAppRouteRuntimeLog(it) }
            .sortedWith(compareBy(DailyJsonlLogStore.DAILY_FILE_ORDER) { it.name })
    }

    private fun summarizeFile(file: File): RuntimeLogFileInfo {
        var lineCount = 0
        var firstTimestamp: Long? = null
        var lastTimestamp: Long? = null
        file.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            lineCount += 1
            val timestamp = parseLogTimeMs(line)
            if (timestamp != null) {
                if (firstTimestamp == null) firstTimestamp = timestamp
                lastTimestamp = timestamp
            }
        }
        return RuntimeLogFileInfo(
            name = file.name,
            sizeBytes = file.length(),
            lineCount = lineCount,
            firstTimestamp = firstTimestamp,
            lastTimestamp = lastTimestamp,
        )
    }

    private fun pruneExpiredRuntimeLogs(logDir: File, now: Date) {
        if (!logDir.exists() || !logDir.isDirectory) return
        val cutoff = retentionCutoffMillis(now)
        logDir.listFiles()
            .orEmpty()
            .forEach { file ->
                if (!file.isFile) return@forEach
                if (isRedundantAppRouteRuntimeLog(file)) {
                    runCatching { file.delete() }.onFailure { }
                    return@forEach
                }
                if (!dailyRuntimeLogPattern.matches(file.name)) {
                    // Unknown leftover files under the runtime log dir still respect retention by mtime.
                    val mtime = file.lastModified()
                    if (mtime > 0L && mtime < cutoff) {
                        runCatching { file.delete() }.onFailure { }
                    }
                    return@forEach
                }
                val fileTime = dailyLogDateStartMs(file.name) ?: file.lastModified()
                if (fileTime > 0L && fileTime < cutoff) {
                    runCatching { file.delete() }.onFailure { }
                }
            }
    }

    private fun dailyLogDate(name: String): String? = dailyLogDateRegex.find(name)?.groupValues?.getOrNull(1)

    private fun dailyLogDateStartMs(name: String): Long? {
        val date = dailyLogDate(name) ?: return null
        return parseDayStartMs(date)
    }

    internal fun parseDayStartMs(date: String): Long? {
        return runCatching {
            LocalDate.parse(date, dailyDateFormatter)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun updateRetentionDays(days: Int, context: Context?) {
        synchronized(writeLock) {
            retentionDays = MagiskLogPolicy.retentionDays(days)
            context?.let { pruneAllLogArtifacts(it, Date(), force = true) }
        }
    }

    private fun parseLogTimeMs(line: String): Long? {
        val timeMarker = "\"time\":\""
        val timeStart = line.indexOf(timeMarker)
        if (timeStart < 0) return null
        val valueStart = timeStart + timeMarker.length
        val valueEnd = line.indexOf('"', valueStart)
        if (valueEnd < 0) return null
        return runCatching {
            java.time.LocalDateTime.parse(line.substring(valueStart, valueEnd), logTimestampFormatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun encodeJsonLine(entry: RuntimeLogEntry): String = JsonLineEncoder.encode(
        JsonLineField.string(
            "time",
            Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).format(logTimestampFormatter),
        ),
        JsonLineField.string("level", entry.level),
        JsonLineField.string("tag", entry.tag),
        // The kit encoder is redaction-free by design; free-text fields are
        // sanitized by the caller (kit DefaultLogSanitizer is the shared policy).
        JsonLineField.string("message", DefaultLogSanitizer.sanitizeIfEnabled(entry.message)),
        JsonLineField.string(
            "throwable",
            DefaultLogSanitizer.sanitizeIfEnabled(entry.throwable),
            include = entry.throwable.isNotBlank(),
        ),
        JsonLineField.string("route", entry.route),
        JsonLineField.string("packageName", entry.packageName, include = entry.packageName.isNotBlank()),
        JsonLineField.string("processName", entry.processName, include = entry.processName.isNotBlank()),
        JsonLineField.number("pid", entry.pid),
        JsonLineField.string("threadName", entry.threadName, include = entry.threadName.isNotBlank()),
    )

    private fun routeStem(route: String): String =
        if (route == DEFAULT_ROUTE) "runtime" else "runtime.$route"

    private fun sanitizeSegment(value: String): String {
        return value
            .ifBlank { "module" }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(64)
    }

    private fun currentProcessName(): String {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.app.Application.getProcessName()
            } else {
                ""
            }
        }.getOrDefault("")
    }
}
