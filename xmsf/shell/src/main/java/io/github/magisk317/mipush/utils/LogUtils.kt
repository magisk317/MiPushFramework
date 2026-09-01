package io.github.magisk317.mipush.utils

import android.content.Context
import android.widget.Toast
import com.xiaomi.xmsf.R
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.mipush.common.logging.DailyRouteLogQuota
import io.github.magisk317.mipush.diagnostics.StructuredLogCore
import io.github.magisk317.xposed.logging.LogSink
import io.github.magisk317.xposed.logging.LoggingKit
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
    private const val DEFAULT_RETENTION_DAYS = 2
    private const val MIN_RETENTION_DAYS = 1
    private const val MAX_READ_LINES = 2000
    private const val DEFAULT_ROUTE = "app"
    private val dailyDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val logTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val dailyRuntimeLogPattern = Regex("""^runtime(?:\.[A-Za-z0-9_.-]+)?\.\d{4}-\d{2}-\d{2}\.jsonl$""")
    private val dailyLogDateRegex = Regex("""^runtime(?:\.[^.]+)*\.(\d{4}-\d{2}-\d{2})\.jsonl$""")
    private val redundantAppRouteRuntimeLogPattern = Regex("""^runtime\.app\.\d{4}-\d{2}-\d{2}\.jsonl$""")
    private val legacyTextLogPattern = Regex("""^(logs_\d{4}-\d{2}-\d{2}|runtime(?:\.[A-Za-z0-9_.-]+)?)\.(txt|log)$""")
    private val legacyModuleTextLogPattern = Regex("""^[A-Za-z0-9_.-]+_\d{4}-\d{2}-\d{2}\.txt$""")
    private const val FULL_PRUNE_INTERVAL_MS = 30L * 60L * 1000L

    @Volatile
    private var lastFullPruneAtMs: Long = 0L

    // Unified single-letter level mapping aligned with android.util.Log priorities.
    // Module-side XLog.d/.i/.w/.e writes D/I/W/E through the same content provider,
    // so normalizing here keeps runtime.*.jsonl consistent across both producers.
    private fun Severity.toShortLetter(): String = when (this) {
        Severity.Verbose -> "V"
        Severity.Debug -> "D"
        Severity.Info -> "I"
        Severity.Warn -> "W"
        Severity.Error -> "E"
        Severity.Assert -> "A"
    }

    private val writeLock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var retentionDays: Int = DEFAULT_RETENTION_DAYS

    @Volatile
    private var minLogLevel: Severity = Severity.Verbose

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

    @JvmStatic
    fun init(context: Context) {
        val resolved = context.applicationContext ?: context
        appContext = resolved
        runCatching {
            deleteLegacyTextLogFiles(resolved)
            pruneAllLogArtifacts(resolved, Date(), force = true)
            LoggingKit.init(
                defaultTag = "MiPush",
                minSeverity = minLogLevel,
                sink = fileLogSink(resolved),
            )
        }.onFailure {
            // 文件日志初始化失败时不回退到会泄露日志的默认 writer。
            android.util.Log.e("MiPushFramework", "Kermit file log init failed, logs will be discarded", it)
        }
    }

    @JvmStatic
    fun setMinLogLevel(level: Severity) {
        minLogLevel = level
        Logger.setMinSeverity(level)
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
            retentionDays = DEFAULT_RETENTION_DAYS
            lastFullPruneAtMs = 0L
        }
        Logger.setLogWriters(emptyList())
        Logger.setMinSeverity(Severity.Verbose)
    }

    private fun fileLogSink(context: Context): LogSink = LogSink { event ->
        appendRuntimeLog(
            context = context,
            level = event.level.shortName,
            tag = event.tag,
            message = event.message,
            throwable = event.throwableText.orEmpty(),
            route = "app",
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
                val day = currentDateString(now)
                val incomingBytes = line.toByteArray(Charsets.UTF_8).size.toLong()
                if (DailyRouteLogQuota.ensureCapacity(logDir, routeName, day, incomingBytes)) {
                    writeLineToFile(
                        File(logDir, DailyRouteLogQuota.runtimeFileName(routeName, day)),
                        line,
                    )
                }
            }
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

    @JvmStatic
    fun logArchiveName(date: Date): String {
        return "logs_" + dateInfo(date)
    }

    @JvmStatic
    fun dateInfo(date: Date): String {
        return Instant.ofEpochMilli(date.time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.US))
    }

    internal fun currentDateString(now: Date = Date()): String =
        Instant.ofEpochMilli(now.time).atZone(ZoneId.systemDefault()).format(dailyDateFormatter)

    internal fun pruneAppLogsForToday(logDir: File, now: Date = Date()) {
        pruneExpiredRuntimeLogs(logDir, now)
    }

    internal fun pruneModuleLogsForToday(moduleLogDir: File, now: Date = Date()) {
        pruneExpiredRuntimeLogs(moduleLogDir, now)
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
            add(Calendar.DAY_OF_YEAR, -(retentionDays.coerceAtLeast(MIN_RETENTION_DAYS) - 1))
        }.timeInMillis
    }

    internal fun pruneDailyFiles(logDir: File, currentDate: String, pattern: Regex) {
        // Keep behavior for callers that still want "not today" cleanup, but prefer
        // [pruneAllLogArtifacts] / retention-based prune for production paths.
        runCatching {
            logDir.listFiles()?.forEach { child ->
                if (child.isFile && pattern.matches(child.name) && !child.name.contains(currentDate)) {
                    child.delete()
                }
            }
        }
    }

    private fun getRuntimeLogFiles(context: Context): List<File> {
        val logDir = LogBundleExporter.getLogDir(context)
        return logDir.listFiles()
            .orEmpty()
            .filter { it.isFile && dailyRuntimeLogPattern.matches(it.name) && !isRedundantAppRouteRuntimeLog(it) }
            .sortedWith(compareBy<File> { dailyLogDate(it.name).orEmpty() }.thenBy { it.name })
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
                    runCatching { file.delete() }
                    return@forEach
                }
                if (!dailyRuntimeLogPattern.matches(file.name)) {
                    // Unknown leftover files under the runtime log dir still respect retention by mtime.
                    val mtime = file.lastModified()
                    if (mtime > 0L && mtime < cutoff) {
                        runCatching { file.delete() }
                    }
                    return@forEach
                }
                val fileTime = dailyLogDateStartMs(file.name) ?: file.lastModified()
                if (fileTime > 0L && fileTime < cutoff) {
                    runCatching { file.delete() }
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
            retentionDays = days.coerceAtLeast(MIN_RETENTION_DAYS)
            context?.let { pruneAllLogArtifacts(it, Date(), force = true) }
        }
    }

    private fun writeLineToFile(file: File, line: String) {
        val parent = file.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
        file.appendText(line)
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

    private fun encodeJsonLine(entry: RuntimeLogEntry): String = StructuredLogCore.encode(
        StructuredLogCore.stringField(
            "time",
            Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).format(logTimestampFormatter),
        ),
        StructuredLogCore.stringField("level", entry.level),
        StructuredLogCore.stringField("tag", entry.tag),
        StructuredLogCore.stringField("message", entry.message),
        StructuredLogCore.stringField("throwable", entry.throwable, include = entry.throwable.isNotBlank()),
        StructuredLogCore.stringField("route", entry.route),
        StructuredLogCore.stringField("packageName", entry.packageName, include = entry.packageName.isNotBlank()),
        StructuredLogCore.stringField("processName", entry.processName, include = entry.processName.isNotBlank()),
        StructuredLogCore.numberField("pid", entry.pid),
        StructuredLogCore.stringField("threadName", entry.threadName, include = entry.threadName.isNotBlank()),
    )

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
