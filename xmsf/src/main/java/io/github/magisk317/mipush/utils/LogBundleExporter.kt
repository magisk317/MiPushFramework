package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.Intent
import android.app.ActivityManager
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.util.Collections
import java.util.Date
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.app.MemoryLimitDiagnostics
import io.github.magisk317.mipush.diagnostics.DiagnosticArchive
import io.github.magisk317.mipush.diagnostics.DiagnosticFileSanitizer
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.platform.support.BoundedShellResult
import io.github.magisk317.mipush.platform.support.BoundedShellRunner
import io.github.magisk317.xposed.logging.MagiskOtel

object LogBundleExporter {
    private const val EXPORT_FILE_PREFIX = "mipush_logs_"
    private const val STAGING_DIR_PREFIX = ".tmp_mipush_logs_"
    private const val STAGING_STALE_MAX_AGE_MS = 30L * 60L * 1000L
    private const val XMSF_KEEPER_PACKAGE = "com.xiaomi.xmsfkeeper"
    private const val XMSF_KEEPALIVE_DIAGNOSTICS_FILE = "xmsf_keepalive.txt"
    private const val APPLICATION_EXIT_HISTORY_FILE = "application_exit_history.txt"
    private const val ROOT_DIAGNOSTICS_TIMEOUT_MS = 6_000L
    private const val PRIVATE_LOG_DIR_NAME = "log"
    private const val PRIVATE_CRASH_DIR_NAME = "crash"
    private const val PRIVATE_EXPORT_DIR_NAME = "xmsf_logs"
    private const val LEGACY_CACHE_LOG_DIR_NAME = "logs"
    private const val MI_PUSH_LOG_DIR_NAME = "MiPushLog"
    private const val LSPOSED_ROTATING_LOGS_PER_KIND = 2
    private val crashFilePattern = Regex("^Crash_\\d{4}-\\d{2}-\\d{2}\\.txt$")
    private val lsposedRotatingLogPattern = Regex("""^(verbose|modules)_.+\.log$""")
    private val LSPOSED_LOG_DIRS = listOf(
        "/data/adb/lspd/log",
    )
    private val opLock = Any()

    data class ExportResult(
        val file: File?,
        val details: String,
    )

    data class ClearResult(
        val success: Boolean,
        val details: String,
    )

    interface RootCommandAccess {
        fun refreshRootAccessIfGranted(): Boolean

        fun runRootCommand(
            command: String,
            timeoutMs: Long = BoundedShellRunner.DEFAULT_TIMEOUT_MS,
        ): BoundedShellResult
    }

    private object DefaultRootCommandAccess : RootCommandAccess {
        override fun refreshRootAccessIfGranted(): Boolean = AppRootAccessFacade.refreshRootAccessIfGranted()

        override fun runRootCommand(command: String, timeoutMs: Long): BoundedShellResult {
            return AppRootAccessFacade.runRootCommand(command, timeoutMs = timeoutMs)
        }
    }

    var rootCommandAccess: RootCommandAccess = DefaultRootCommandAccess

    fun resetRootCommandAccessForTest() {
        rootCommandAccess = DefaultRootCommandAccess
    }

    fun buildLogBundle(context: Context): ExportResult = synchronized(opLock) {
        val exportStarted = SystemClock.elapsedRealtime()
        val now = Date()
        val timestamp = LogUtils.dateInfo(now)
        val exportDir = getPrivateExportDir(context)
        // Free disk I/O capacity before a full export: leftover staging can be tens of MB.
        purgeAllStagingDirs(exportDir)
        LogUtils.pruneAllLogArtifacts(context, now, force = true)
        val deletedLegacyLogs = LogUtils.deleteLegacyTextLogFiles(context)
        val result = DiagnosticArchive.buildBundle(
            context = context,
            timestamp = timestamp,
            exportDir = exportDir,
            exportFilePrefix = EXPORT_FILE_PREFIX,
            stagingDirPrefix = STAGING_DIR_PREFIX,
            deletePath = ::deleteRecursivelyWithSuFallback,
            collect = { stagingDir, details ->
                if (deletedLegacyLogs > 0) {
                    details += "legacy runtime text logs cleared: $deletedLegacyLogs"
                }
                collectWithMaxParallelism(context, stagingDir, details)
            },
            onInfo = { logI(it) },
            onWarning = { logW(it) },
            onError = { message, error ->
                if (error == null) logE(message) else logE(message, error)
            },
        )
        logI(
            "buildLogBundle finished success=${result.file != null} " +
                "size=${result.file?.length() ?: -1} " +
                "totalTookMs=${SystemClock.elapsedRealtime() - exportStarted}",
        )
        val durationMs = (SystemClock.elapsedRealtime() - exportStarted).coerceAtLeast(0L)
        MagiskOtel.event(
            name = "app.monitor",
            attributes = mapOf(
                "result" to if (result.file != null) "ok" else "error",
                "duration_ms" to durationMs.toString(),
                "process" to "main",
                "stage" to "log_bundle_export",
                "reason" to if (result.file != null) "exported" else "export_failed",
                "found_count" to if (result.details.isBlank()) "0" else result.details.count { it == ';' }.plus(1).toString(),
            ),
            statusOk = result.file != null,
        )
        ExportResult(result.file, result.details)
    }

    /**
     * Run independent collectors in parallel up to available processors.
     * Root-backed steps stay sequential after local I/O to avoid saturating su.
     * Content is never truncated: full runtime/crash/sdk/lsposed/logcat are kept.
     */
    private fun collectWithMaxParallelism(
        context: Context,
        stagingDir: File,
        details: MutableList<String>,
    ) {
        val safeDetails = Collections.synchronizedList(details)
        val workers = Runtime.getRuntime().availableProcessors().coerceAtLeast(2).coerceAtMost(6)
        val pool = Executors.newFixedThreadPool(workers)
        val error = AtomicReference<Throwable>(null)
        fun submit(label: String, block: () -> Unit) = pool.submit(
            Callable {
                val started = SystemClock.elapsedRealtime()
                runCatching(block).onFailure { error.compareAndSet(null, it) }
                val took = SystemClock.elapsedRealtime() - started
                safeDetails += "$label=${took}ms"
                logI("LogBundleExporter $label tookMs=$took")
                null
            },
        )
        try {
            val localJobs = listOf(
                submit("copyAppLogs") { copyAppLogs(context, stagingDir, safeDetails) },
                submit("copyCrashLogs") { copyCrashLogs(context, stagingDir, safeDetails) },
                submit("copyMiPushSdkLogs") { copyMiPushSdkLogs(context, stagingDir, safeDetails) },
                submit("captureApplicationExitHistory") {
                    captureApplicationExitHistory(context, stagingDir, safeDetails)
                },
                submit("captureLogcat") { captureLogcat(stagingDir, safeDetails) },
            )
            localJobs.forEach { it.get() }
            error.get()?.let { throw it }

            // Root-heavy path: sequential.
            run {
                val started = SystemClock.elapsedRealtime()
                if (!copyLsposedLogs(stagingDir, safeDetails)) {
                    safeDetails += "lsposed log missing or unreadable"
                }
                safeDetails += "copyLsposedLogs=${SystemClock.elapsedRealtime() - started}ms"
                logI("LogBundleExporter copyLsposedLogs tookMs=${SystemClock.elapsedRealtime() - started}")
            }
            run {
                val started = SystemClock.elapsedRealtime()
                captureXmsfKeepaliveDiagnostics(stagingDir, safeDetails)
                safeDetails += "keepaliveDiagnostics=${SystemClock.elapsedRealtime() - started}ms"
                logI("LogBundleExporter keepaliveDiagnostics tookMs=${SystemClock.elapsedRealtime() - started}")
            }

            val sanitizeStarted = SystemClock.elapsedRealtime()
            // Runtime jsonl is already sanitized at append time; skip re-scan of multi-MB files.
            // Other sources (logcat/lsposed/sdk/crash) still get full streaming sanitize in parallel.
            DiagnosticFileSanitizer.sanitizeDirectory(
                root = stagingDir,
                onWarning = { logW(it) },
                includeFile = { file ->
                    !(file.name.startsWith("runtime") && file.name.endsWith(".jsonl"))
                },
                parallel = true,
            )
            safeDetails += "sanitize=${SystemClock.elapsedRealtime() - sanitizeStarted}ms"
            logI(
                "LogBundleExporter sanitize tookMs=${SystemClock.elapsedRealtime() - sanitizeStarted}",
            )
            safeDetails += "exportParallelWorkers=$workers"
        } finally {
            pool.shutdown()
            runCatching { pool.awaitTermination(2, TimeUnit.MINUTES) }
        }
    }

    private fun purgeAllStagingDirs(exportDir: File) {
        if (!exportDir.exists() || !exportDir.isDirectory) return
        exportDir.listFiles().orEmpty().forEach { child ->
            if (!child.name.startsWith(STAGING_DIR_PREFIX)) return@forEach
            runCatching { deleteRecursivelyWithSuFallback(child) }
                .onFailure { logW("Failed to purge staging ${child.name}: ${it.message}") }
        }
    }

    fun buildShareIntent(context: Context, file: File): Intent =
        DiagnosticArchive.buildShareIntent(
            context = context,
            file = file,
            authority = Constants.AUTHORITY_FILE_PROVIDER,
            onInfo = { logI(it) },
            onWarning = { logW(it) },
        )

    fun clearLogFolders(context: Context): ClearResult = synchronized(opLock) {
        val result = DiagnosticArchive.clearDirectories(
            targets = listOf(
                "log" to getLogDir(context),
                "crash" to getCrashDir(context),
                "legacy_cache_log" to getLegacyCacheLogDir(context),
                "private_export" to getPrivateExportDir(context),
            ),
            deletePath = ::deleteRecursivelyWithSuFallback,
            onWarning = { logW(it) },
        )
        MagiskOtel.event(
            name = "app.monitor",
            attributes = mapOf(
                "result" to if (result.success) "ok" else "error",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "log_bundle_clear",
                "reason" to if (result.success) "cleared" else "clear_failed",
                "found_count" to if (result.details.isBlank()) "0" else result.details.count { it == ';' }.plus(1).toString(),
            ),
            statusOk = result.success,
        )
        ClearResult(result.success, result.details)
    }

    fun getLogDir(context: Context): File = ensurePrivateSubDir(context, PRIVATE_LOG_DIR_NAME)

    fun getCrashDir(context: Context): File = ensurePrivateSubDir(context, PRIVATE_CRASH_DIR_NAME)

    internal fun getLegacyCacheLogDir(context: Context): File = File(context.cacheDir, LEGACY_CACHE_LOG_DIR_NAME)

    internal fun getPrivateExportDir(context: Context): File = ensurePrivateSubDir(context, PRIVATE_EXPORT_DIR_NAME)

    internal fun getMiPushSdkLogDir(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, MI_PUSH_LOG_DIR_NAME) }

    private val exportZipDateRegex = Regex("""^mipush_logs_(\d{4}-\d{2}-\d{2})_\d{2}-\d{2}-\d{2}\.zip$""")
    private val exportStagingDateRegex = Regex("""^\.tmp_mipush_logs_(\d{4}-\d{2}-\d{2})_\d{2}-\d{2}-\d{2}$""")
    private val crashDateRegex = Regex("""^Crash_(\d{4}-\d{2}-\d{2})\.txt$""")
    private val managerCacheZipPattern = Regex("""^runtime-log-.*\.zip$""")

    /**
     * Delete non-runtime log products older than [cutoffMs] (start-of-day cutoff from retention).
     * Covers crash, private export zips/staging, legacy cache logs, MiPush SDK logs, and
     * leftover manager-style cache zips if present under this package.
     */
    internal fun pruneExpiredArtifacts(context: Context, cutoffMs: Long) {
        pruneDatedFiles(
            dir = getCrashDir(context),
            dateRegex = crashDateRegex,
            nameFilter = { crashFilePattern.matches(it) },
            cutoffMs = cutoffMs,
        )
        pruneExportDir(getPrivateExportDir(context), cutoffMs)
        pruneTreeByMtime(getLegacyCacheLogDir(context), cutoffMs)
        getMiPushSdkLogDir(context)?.let { pruneTreeByMtime(it, cutoffMs) }
        // Share/export temp zips written into cacheDir (manager host copies use similar names).
        pruneFilesInDir(
            dir = context.cacheDir,
            cutoffMs = cutoffMs,
            include = { it.isFile && (managerCacheZipPattern.matches(it.name) || it.name.startsWith(EXPORT_FILE_PREFIX)) },
        )
        // Manager-local diagnostic dirs if this code ever runs under the manager package id.
        listOf("log", "crash", "private_export").forEach { name ->
            val dir = File(context.filesDir, name)
            if (dir.absolutePath == getLogDir(context).absolutePath ||
                dir.absolutePath == getCrashDir(context).absolutePath ||
                dir.absolutePath == getPrivateExportDir(context).absolutePath
            ) {
                return@forEach
            }
            if (name == "log") {
                // best-effort mtime prune for foreign process local log copies
                pruneTreeByMtime(dir, cutoffMs)
            } else if (name == "crash") {
                pruneDatedFiles(dir, crashDateRegex, { crashFilePattern.matches(it) }, cutoffMs)
            } else {
                pruneExportDir(dir, cutoffMs)
            }
        }
    }

    private fun pruneExportDir(exportDir: File, cutoffMs: Long) {
        if (!exportDir.exists() || !exportDir.isDirectory) return
        val nowMs = System.currentTimeMillis()
        // Staging is temporary; drop leftovers quickly so a failed export cannot pile up
        // .tmp_* trees for the full retention window.
        val stagingStaleBeforeMs = nowMs - STAGING_STALE_MAX_AGE_MS
        exportDir.listFiles().orEmpty().forEach { child ->
            val isStaging = child.name.startsWith(STAGING_DIR_PREFIX)
            val ageMs = artifactAgeMs(child)
            val pastRetention = ageMs > 0L && ageMs < cutoffMs
            val stagingStale = isStaging && (
                child.lastModified() <= 0L || child.lastModified() < stagingStaleBeforeMs
            )
            if (!pastRetention && !stagingStale) return@forEach
            runCatching {
                if (child.isDirectory) {
                    deleteRecursivelyWithSuFallback(child)
                } else {
                    child.delete()
                }
            }
        }
    }

    private fun artifactAgeMs(file: File): Long {
        val name = file.name
        exportZipDateRegex.find(name)?.groupValues?.getOrNull(1)?.let { day ->
            LogUtils.parseDayStartMs(day)?.let { return it }
        }
        exportStagingDateRegex.find(name)?.groupValues?.getOrNull(1)?.let { day ->
            LogUtils.parseDayStartMs(day)?.let { return it }
        }
        crashDateRegex.find(name)?.groupValues?.getOrNull(1)?.let { day ->
            LogUtils.parseDayStartMs(day)?.let { return it }
        }
        return file.lastModified()
    }

    private fun pruneDatedFiles(
        dir: File,
        dateRegex: Regex,
        nameFilter: (String) -> Boolean,
        cutoffMs: Long,
    ) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles().orEmpty().forEach { file ->
            if (!file.isFile || !nameFilter(file.name)) {
                // Still drop unknown leftover files by mtime.
                if (file.isFile) {
                    val mtime = file.lastModified()
                    if (mtime > 0L && mtime < cutoffMs) {
                        runCatching { file.delete() }
                    }
                } else if (file.isDirectory) {
                    val mtime = file.lastModified()
                    if (mtime > 0L && mtime < cutoffMs) {
                        runCatching { deleteRecursivelyWithSuFallback(file) }
                    }
                }
                return@forEach
            }
            val day = dateRegex.find(file.name)?.groupValues?.getOrNull(1)
            val fileTime = day?.let { LogUtils.parseDayStartMs(it) } ?: file.lastModified()
            if (fileTime > 0L && fileTime < cutoffMs) {
                runCatching { file.delete() }
            }
        }
    }

    private fun pruneTreeByMtime(root: File, cutoffMs: Long) {
        if (!root.exists()) return
        if (root.isFile) {
            val mtime = root.lastModified()
            if (mtime > 0L && mtime < cutoffMs) {
                runCatching { root.delete() }
            }
            return
        }
        if (!root.isDirectory) return
        root.walkBottomUp().forEach { file ->
            if (file == root) return@forEach
            val mtime = file.lastModified()
            if (mtime > 0L && mtime < cutoffMs) {
                runCatching {
                    if (file.isDirectory) {
                        if (file.listFiles().isNullOrEmpty()) file.delete()
                    } else {
                        file.delete()
                    }
                }
            }
        }
        // Drop empty root leftovers only if everything is gone; keep the directory itself.
    }

    private fun pruneFilesInDir(dir: File?, cutoffMs: Long, include: (File) -> Boolean) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        dir.listFiles().orEmpty().forEach { file ->
            if (!include(file)) return@forEach
            val mtime = file.lastModified()
            if (mtime > 0L && mtime < cutoffMs) {
                runCatching { file.delete() }
            }
        }
    }

    private fun ensurePrivateSubDir(context: Context, name: String): File {
        val dir = File(context.filesDir, name)
        DiagnosticArchive.ensureDirectory(dir, recreateWhenFile = true, onWarning = { logW(it) })
        return dir
    }

    private fun copyAppLogs(context: Context, stagingDir: File, details: MutableList<String>) {
        val src = getLogDir(context)
        var copiedAny = false
        if (src.exists() && src.isDirectory && src.listFiles()?.isNotEmpty() == true) {
            val stagedAppLogDir = File(stagingDir, "app/log")
            copyDirectory(src, stagedAppLogDir) { file ->
                file.name.endsWith(".jsonl") && !LogUtils.isRedundantAppRouteRuntimeLog(file)
            }
            copiedAny = stagedAppLogDir.walkTopDown().any { it.isFile }
            if (copiedAny) {
                details += "log: ${src.absolutePath}"
                details += summarizeRuntimeLogFiles(stagedAppLogDir)
            }
        }
        if (!copiedAny) {
            details += "app log missing"
            details += "runtime log files: 0"
        }
    }

    private fun copyCrashLogs(context: Context, stagingDir: File, details: MutableList<String>) {
        val crashDir = getCrashDir(context)
        if (crashDir.exists() && crashDir.isDirectory && crashDir.listFiles()?.isNotEmpty() == true) {
            copyDirectory(crashDir, File(stagingDir, "app/crash"))
            details += "crash log: ${crashDir.absolutePath}"
        } else {
            details += "crash log missing"
        }
    }

    private fun copyMiPushSdkLogs(context: Context, stagingDir: File, details: MutableList<String>) {
        val sdkDir = getMiPushSdkLogDir(context)
        if (sdkDir != null && sdkDir.exists() && sdkDir.isDirectory && sdkDir.listFiles()?.isNotEmpty() == true) {
            copyDirectory(sdkDir, File(stagingDir, "app/mipush_sdk"))
            details += "mipush sdk log: ${sdkDir.absolutePath}"
        }
    }

    private fun copyLsposedLogs(stagingDir: File, details: MutableList<String>): Boolean {
        val lsposedTargetRoot = File(stagingDir, "lsposed")
        var copied = false
        LSPOSED_LOG_DIRS.forEach { path ->
            val src = File(path)
            if (src.exists() && src.canRead()) {
                val target = File(lsposedTargetRoot, src.name)
                copyDirectory(src, target) { file ->
                    !file.name.contains("old", ignoreCase = true)
                }
                val trimmed = trimLsposedRotatingLogs(target)
                if (trimmed > 0) {
                    details += "lsposed rotating logs trimmed: $trimmed"
                }
                if (target.walkTopDown().any { it.isFile }) {
                    details += "lsposed direct: $path"
                    copied = true
                }
            }
        }
        if (copied) return true

        if (!rootCommandAccess.refreshRootAccessIfGranted()) {
            details += "lsposed su skipped: root not granted"
            return false
        }

        val targetPath = lsposedTargetRoot.absolutePath
        val targetUid = runCatching { android.os.Process.myUid() }.getOrDefault(-1)
        val shellCmd = buildString {
            append("mkdir -p ${shQuote(targetPath)}; ")
            LSPOSED_LOG_DIRS.forEach { path ->
                val name = File(path).name
                val targetDir = "$targetPath/$name"
                append("if [ -d ${shQuote(path)} ]; then ")
                append("mkdir -p ${shQuote(targetDir)}; ")
                append("cd ${shQuote(path)} && ")
                append("find . -type f ! -iname '*old*' | while read -r rel; do ")
                append("mkdir -p ${shQuote(targetDir)}/\"$(dirname \"${'$'}rel\")\"; ")
                append("cp \"${'$'}rel\" ${shQuote(targetDir)}/\"${'$'}rel\"; ")
                append("done; ")
                append("chmod -R a+rX ${shQuote(targetDir)}; ")
                if (targetUid > 0) {
                    append("chown -R $targetUid:$targetUid ${shQuote(targetDir)}; ")
                }
                append("fi; ")
            }
        }
        val suResult = runSuCommand(shellCmd)
        val trimmed = trimLsposedRotatingLogs(lsposedTargetRoot)
        if (trimmed > 0) {
            details += "lsposed rotating logs trimmed: $trimmed"
        }
        if (suResult.exitCode == 0 && lsposedTargetRoot.walkTopDown().any { it.isFile }) {
            details += "lsposed copied via su"
            return true
        }
        details += "lsposed su failed: ${suResult.stderr.ifBlank { suResult.stdout }.ifBlank { "unknown" }}"
        return false
    }

    private fun captureLogcat(stagingDir: File, details: MutableList<String>) {
        val logcatDir = File(stagingDir, "logcat")
        if (!ensureDirectory(logcatDir, recreateWhenFile = true)) return
        val output = File(logcatDir, "logcat_all.txt")
        val direct = dumpCommandOutput(listOf("logcat", "-d", "-v", "threadtime", "-b", "all"), output)
        if (direct) {
            details += "logcat: direct"
            return
        }
        if (!rootCommandAccess.refreshRootAccessIfGranted()) return
        val su = dumpRootCommandOutput("logcat -d -v threadtime -b all", output)
        if (su) {
            details += "logcat: su"
        }
    }

    private fun captureApplicationExitHistory(
        context: Context,
        stagingDir: File,
        details: MutableList<String>,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            details += "application exit history skipped: api<30"
            return
        }
        val output = File(stagingDir, "system/$APPLICATION_EXIT_HISTORY_FILE")
        val parent = output.parentFile
        if (parent == null || !ensureDirectory(parent, recreateWhenFile = true)) {
            details += "application exit history failed: output dir unavailable"
            return
        }
        val manager = context.getSystemService(ActivityManager::class.java)
        if (manager == null) {
            details += "application exit history skipped: activity manager unavailable"
            return
        }
        val exits = runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 12, 0)
        }.getOrElse { error ->
            details += "application exit history failed: ${error.javaClass.simpleName}"
            return
        }
        output.writeText(buildString {
            appendLine("# ApplicationExitInfo history for ${context.packageName}")
            appendLine("# Memory-related rows are marked for Android 17 limiter diagnosis.")
            appendLine("count=${exits.size}")
            exits.forEachIndexed { index, info ->
                val memoryRelated = MemoryLimitDiagnostics.isMemoryRelatedExitReason(info.reason)
                appendLine()
                appendLine("[$index]")
                appendLine("memoryRelated=$memoryRelated")
                appendLine("reason=${info.reason}")
                appendLine("status=${info.status}")
                appendLine("timestamp=${info.timestamp}")
                appendLine("pssKb=${info.pss}")
                appendLine("rssKb=${info.rss}")
                appendLine("description=${info.description.orEmpty()}")
            }
        })
        details += "application exit history: system/$APPLICATION_EXIT_HISTORY_FILE entries=${exits.size}"
    }

    private fun captureXmsfKeepaliveDiagnostics(stagingDir: File, details: MutableList<String>) {
        if (!rootCommandAccess.refreshRootAccessIfGranted()) {
            details += "xmsf keepalive diagnostics skipped: root not granted"
            return
        }

        val output = File(stagingDir, "system/$XMSF_KEEPALIVE_DIAGNOSTICS_FILE")
        val parent = output.parentFile
        if (parent == null || !ensureDirectory(parent, recreateWhenFile = true)) {
            details += "xmsf keepalive diagnostics failed: output dir unavailable"
            return
        }

        val sections = keepaliveDiagnosticCommands().map { section ->
            section to rootCommandAccess.runRootCommand(section.command, timeoutMs = ROOT_DIAGNOSTICS_TIMEOUT_MS)
        }
        val text = buildString {
            appendLine("# XMSF keepalive diagnostics")
            appendLine("# Captures Xiaomi system keeper package, process, and XMPushService binding state.")
            appendLine()
            sections.forEach { (section, result) ->
                appendLine("## ${section.title}")
                appendLine("$ ${section.command}")
                appendLine("exitCode=${result.exitCode} timedOut=${result.timedOut} skipped=${result.skipped}")
                val stdout = result.stdoutText.ifBlank { "<empty>" }
                appendLine("[stdout]")
                appendLine(stdout)
                val stderr = result.stderrText.ifBlank { "<empty>" }
                appendLine("[stderr]")
                appendLine(stderr)
                appendLine()
            }
        }
        output.writeText(text)
        details += "xmsf keepalive diagnostics: system/$XMSF_KEEPALIVE_DIAGNOSTICS_FILE"
    }

    private data class KeepaliveDiagnosticCommand(
        val title: String,
        val command: String,
    )

    private fun keepaliveDiagnosticCommands(): List<KeepaliveDiagnosticCommand> {
        val packageFields =
            """Package \[|codePath=|resourcePath=|legacyNativeLibraryDir=|versionCode=|versionName=|""" +
                "pkgFlags=|privateFlags=|userId=|firstInstallTime=|lastUpdateTime=|enabled="
        val serviceFields =
            "ServiceRecord|packageName=|processName=|app=|recentCallingPackage=|recentCallingUid=|" +
                "infoAllowStartForeground|Bindings:|Client AppBindRecord|ConnectionRecord|" +
                """caller=|callerPackage|com\.xiaomi\.xmsfkeeper"""
        val logcatFields =
            """XMSFKeeper|xmsfkeeper|caller=com\.xiaomi\.xmsfkeeper|XMPushService"""
        return listOf(
            KeepaliveDiagnosticCommand(
                title = "xmsf package path",
                command = packagePathDiagnosticCommand(Constants.SERVICE_APP_NAME),
            ),
            KeepaliveDiagnosticCommand(
                title = "xmsf package flags",
                command = packageFlagsDiagnosticCommand(Constants.SERVICE_APP_NAME, packageFields),
            ),
            KeepaliveDiagnosticCommand(
                title = "xmsfkeeper package path",
                command = packagePathDiagnosticCommand(XMSF_KEEPER_PACKAGE),
            ),
            KeepaliveDiagnosticCommand(
                title = "xmsfkeeper package flags",
                command = packageFlagsDiagnosticCommand(XMSF_KEEPER_PACKAGE, packageFields),
            ),
            KeepaliveDiagnosticCommand(
                title = "xmsfkeeper process",
                command = "pidof $XMSF_KEEPER_PACKAGE || true; " +
                    "(ps -A || ps) | grep -F ${shQuote(XMSF_KEEPER_PACKAGE)} | grep -v grep || " +
                    "echo ${shQuote("process missing: $XMSF_KEEPER_PACKAGE")}",
            ),
            KeepaliveDiagnosticCommand(
                title = "XMPushService binding",
                command = "dumpsys activity services ${Constants.SERVICE_APP_NAME}/${Constants.XM_PUSH_SERVICE_CLASS} | " +
                    "grep -E ${shQuote(serviceFields)} || " +
                    "echo ${shQuote("binding fields missing: ${Constants.SERVICE_APP_NAME}/${Constants.XM_PUSH_SERVICE_CLASS}")}",
            ),
            KeepaliveDiagnosticCommand(
                title = "recent xmsfkeeper logcat",
                command = "logcat -d -v threadtime -b all | grep -E ${shQuote(logcatFields)} | tail -n 200 || " +
                    "echo ${shQuote("recent xmsfkeeper logcat entries missing")}",
            ),
        )
    }

    private fun packagePathDiagnosticCommand(packageName: String): String =
        "pm path $packageName || echo ${shQuote("package missing: $packageName")}"

    private fun packageFlagsDiagnosticCommand(packageName: String, grepPattern: String): String =
        "dumpsys package $packageName | grep -E ${shQuote(grepPattern)} || " +
            "echo ${shQuote("package fields missing: $packageName")}"

    private fun dumpCommandOutput(command: List<String>, output: File): Boolean {
        return runCatching {
            val parent = output.parentFile ?: return false
            if (!ensureDirectory(parent, recreateWhenFile = true)) return false
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(output))
                .start()
            val completed = process.waitFor(6, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return@runCatching false
            }
            output.exists() && output.length() > 0
        }.getOrDefault(false)
    }

    private fun dumpRootCommandOutput(command: String, output: File): Boolean {
        return runCatching {
            val parent = output.parentFile ?: return false
            if (!ensureDirectory(parent, recreateWhenFile = true)) return false
            val result = rootCommandAccess.runRootCommand(command, timeoutMs = 6_000L)
            if (!result.isSuccess) return false
            output.writeText(result.stdoutText)
            output.exists() && output.length() > 0
        }.getOrDefault(false)
    }

    private fun copyDirectory(source: File, target: File, includeFile: (File) -> Boolean = { true }) {
        DiagnosticArchive.copyDirectory(source, target, includeFile, onWarning = { logW(it) })
    }

    private fun summarizeRuntimeLogFiles(stagedAppLogDir: File): String {
        val files = stagedAppLogDir.walkTopDown()
            .filter { file ->
                file.isFile && file.name.startsWith("runtime.") && file.name.endsWith(".jsonl")
            }
            .toList()
        val totalBytes = files.sumOf { it.length() }
        return "runtime log files: ${files.size}, bytes=$totalBytes"
    }

    internal fun trimLsposedRotatingLogs(root: File): Int {
        if (!root.exists() || !root.isDirectory) return 0
        var deleted = 0
        root.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                val kind = lsposedRotatingLogPattern.matchEntire(file.name)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: return@mapNotNull null
                "${file.parentFile?.absolutePath.orEmpty()}:$kind" to file
            }
            .groupBy({ it.first }, { it.second })
            .values
            .forEach { files ->
                files.sortedWith(compareByDescending<File> { it.name }.thenByDescending { it.lastModified() })
                    .drop(LSPOSED_ROTATING_LOGS_PER_KIND)
                    .forEach { file ->
                        if (deleteRecursivelyWithSuFallback(file)) {
                            deleted += 1
                        }
                    }
            }
        return deleted
    }

    private fun ensureDirectory(dir: File, recreateWhenFile: Boolean): Boolean {
        return DiagnosticArchive.ensureDirectory(dir, recreateWhenFile, onWarning = { logW(it) })
    }

    private fun deleteRecursivelyWithSuFallback(target: File): Boolean {
        if (!target.exists()) return true
        if (target.deleteRecursively()) return true
        if (!rootCommandAccess.refreshRootAccessIfGranted()) {
            logW("Skip su rm fallback because root is not granted: ${target.absolutePath}")
            return !target.exists()
        }
        val suResult = runSuCommand("rm -rf ${shQuote(target.absolutePath)}")
        val deleted = !target.exists()
        if (!deleted) {
            logW(
                "su rm fallback failed: path=${target.absolutePath} exit=${suResult.exitCode} stderr=${suResult.stderr} stdout=${suResult.stdout}",
            )
        }
        return deleted
    }

    private data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun runSuCommand(command: String): ShellResult {
        val result = rootCommandAccess.runRootCommand(command)
        return ShellResult(result.exitCode, result.stdoutText, result.stderrText)
    }

    private fun shQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun pruneCurrentDayLocalLogs(context: Context, now: Date) {
        runCatching {
            LogUtils.pruneAllLogArtifacts(context, now, force = true)
        }.onFailure {
            logW("Failed to prune local logs before export: ${it.message ?: it.javaClass.simpleName}")
        }
    }
}
