package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.app.ActivityManager
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.Date
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.common.Constants
import org.json.JSONArray
import org.json.JSONObject
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.app.MemoryLimitDiagnostics
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.platform.support.BoundedShellResult
import io.github.magisk317.mipush.platform.support.BoundedShellRunner
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.diagnostics.DiagnosticArchive
import io.github.magisk317.xposed.diagnostics.DiagnosticBundleIdentity
import io.github.magisk317.xposed.diagnostics.DiagnosticBundleNames
import io.github.magisk317.xposed.diagnostics.DiagnosticExportMode
import io.github.magisk317.xposed.diagnostics.DiagnosticShell

object LogBundleExporter {
    /**
     * Manager and xmsf runtime share one token: a user triggers a single export, and both
     * package identities are stamped inside that one archive instead of splitting it in two.
     */
    private const val PRODUCT = "mipush"
    private val EXPORT_FILE_PREFIX = DiagnosticBundleNames.exportFilePrefix(PRODUCT)
    private val STAGING_DIR_PREFIX = DiagnosticBundleNames.stagingDirPrefix(PRODUCT)
    private const val STAGING_STALE_MAX_AGE_MS = 30L * 60L * 1000L
    private const val XMSF_KEEPER_PACKAGE = "com.xiaomi.xmsfkeeper"
    private const val XMSF_KEEPALIVE_DIAGNOSTICS_FILE = "xmsf_keepalive.txt"
    private const val ZYGISK_DIAGNOSTICS_FILE = "zygisk_diagnostics.txt"
    private const val TARGET_PROCESS_DIAGNOSTICS_FILE = "target_process_diagnostics.txt"
    private const val ZYGISK_CONFIG_PATH = "/data/adb/mipush_zygisk/app.conf"
    private const val APPLICATION_EXIT_HISTORY_FILE = "application_exit_history.txt"
    private const val ROOT_DIAGNOSTICS_TIMEOUT_MS = 6_000L
    private const val TARGET_PROCESS_DIAGNOSTICS_TIMEOUT_MS = 12_000L
    private const val DEFAULT_HASH_BUFFER_SIZE = 64 * 1024
    private const val PRIVATE_LOG_DIR_NAME = "log"
    const val BLOCK_REASON_NEED_ROOT = "uikit_log_export_need_root"
    private const val RUNTIME_SUMMARY_FILE_NAME = "runtime_summary.json"
    private const val PRIVATE_CRASH_DIR_NAME = "crash"
    private val PRIVATE_EXPORT_DIR_NAME = DiagnosticBundleNames.privateExportDirName(PRODUCT)
    private val LEGACY_PRIVATE_EXPORT_DIR_NAMES = listOf("xmsf_logs")
    private const val LEGACY_CACHE_LOG_DIR_NAME = "logs"
    private const val MI_PUSH_LOG_DIR_NAME = "MiPushLog"
    private val crashFilePattern = Regex("^Crash_\\d{4}-\\d{2}-\\d{2}\\.txt$")
    private val SYSTEMUI_HOOK_LOG_DIRS = listOf(
        "/data/user/0/com.android.systemui/files/mipush-hook",
        "/data/user_de/0/com.android.systemui/files/mipush-hook",
        "/data/system_ce/0/com.android.systemui/files/mipush-hook",
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

    fun checkPreExport(): String? =
        if (!AppRootAccessFacade.refreshRootAccessIfGranted()) BLOCK_REASON_NEED_ROOT else null

    fun buildLogBundle(
        context: Context,
        mode: DiagnosticExportMode = DiagnosticExportMode.current(),
    ): ExportResult = synchronized(opLock) {
        checkPreExport()?.let { reason -> return@synchronized ExportResult(file = null, details = reason) }
        val exportStarted = SystemClock.elapsedRealtime()
        val now = Date()
        val identity = buildBundleIdentity(context)
        val timestamp = DiagnosticBundleNames.exportTimestamp(now.time)
        val exportDir = getPrivateExportDir(context)
        // Free disk I/O capacity before a full export: leftover staging can be tens of MB.
        purgeAllStagingDirs(exportDir)
        LogUtils.pruneAllLogArtifacts(context, now, force = true)
        val deletedLegacyLogs = LogUtils.deleteLegacyTextLogFiles(context)
        val result = DiagnosticArchive.buildBundle(
            identity = identity,
            timestamp = timestamp,
            exportDir = exportDir,
            exportFilePrefix = EXPORT_FILE_PREFIX,
            stagingDirPrefix = STAGING_DIR_PREFIX,
            deletePath = ::deleteRecursivelyWithSuFallback,
            collect = { stagingDir, details ->
                if (deletedLegacyLogs > 0) {
                    details += "legacy runtime text logs cleared: $deletedLegacyLogs"
                }
                collectWithMaxParallelism(context, identity, stagingDir, details, mode)
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
        identity: DiagnosticBundleIdentity,
        stagingDir: File,
        details: MutableList<String>,
        mode: DiagnosticExportMode,
    ) {
        val identities = listOf(
            readPackageIdentity(context, "manager/module", Constants.MANAGER_APP_NAME),
            readPackageIdentity(context, "xmsf host", Constants.SERVICE_APP_NAME),
        )
        identities.forEach { details += it.toSummaryLine() }
        writeRuntimeLogSummaryFile(context, stagingDir, details, identity, identities)
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

            )
            localJobs.forEach { it.get() }
            error.get()?.let { throw it }

            if (mode.capturesRootHeavyDiagnostics) {
                run {
                    val started = SystemClock.elapsedRealtime()
                    copyLsposedLogs(stagingDir, safeDetails, mode)
                    safeDetails += "copyLsposedLogs=${SystemClock.elapsedRealtime() - started}ms"
                }
                run {
                    val started = SystemClock.elapsedRealtime()
                    copySystemUiHookLogs(stagingDir, safeDetails)
                    safeDetails += "copySystemUiHookLogs=${SystemClock.elapsedRealtime() - started}ms"
                }
                run {
                    val started = SystemClock.elapsedRealtime()
                    captureXmsfKeepaliveDiagnostics(stagingDir, safeDetails)
                    safeDetails += "keepaliveDiagnostics=${SystemClock.elapsedRealtime() - started}ms"
                }
                run {
                    val started = SystemClock.elapsedRealtime()
                    captureZygiskDiagnostics(stagingDir, safeDetails)
                    safeDetails += "zygiskDiagnostics=${SystemClock.elapsedRealtime() - started}ms"
                }
                run {
                    val started = SystemClock.elapsedRealtime()
                    captureTargetProcessDiagnostics(context, stagingDir, safeDetails)
                    safeDetails += "targetProcessDiagnostics=${SystemClock.elapsedRealtime() - started}ms"
                }
            }

            run {
                val started = SystemClock.elapsedRealtime()
                captureLogcat(stagingDir, safeDetails, mode)
                safeDetails += "captureLogcat=${SystemClock.elapsedRealtime() - started}ms"
            }

            safeDetails += "exportParallelWorkers=$workers"
        } finally {
            pool.shutdown()
            runCatching { pool.awaitTermination(2, TimeUnit.MINUTES) }.onFailure { }
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

    fun clearLogFolders(context: Context): ClearResult = synchronized(opLock) {
        val result = DiagnosticArchive.clearDirectories(
            targets = listOf(
                "log" to getLogDir(context),
                "crash" to getCrashDir(context),
                "legacy_cache_log" to getLegacyCacheLogDir(context),
                "private_export" to getPrivateExportDir(context),
            ) + getLegacyPrivateExportDirs(context).map { dir -> "legacy_export:${dir.name}" to dir },
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

    /** Bundles left behind before the export directory was derived from the product token. */
    internal fun getLegacyPrivateExportDirs(context: Context): List<File> =
        LEGACY_PRIVATE_EXPORT_DIR_NAMES.map { name -> File(context.filesDir, name) }
            .filter { it.exists() }

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
        getLegacyPrivateExportDirs(context).forEach { pruneExportDir(it, cutoffMs) }
        pruneTreeByMtime(getLegacyCacheLogDir(context), cutoffMs)
        getMiPushSdkLogDir(context)?.let { pruneTreeByMtime(it, cutoffMs) }
        // Export products previously written into cache by legacy callers.
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
            }.onFailure { }
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
                        runCatching { file.delete() }.onFailure { }
                    }
                } else if (file.isDirectory) {
                    val mtime = file.lastModified()
                    if (mtime > 0L && mtime < cutoffMs) {
                        runCatching { deleteRecursivelyWithSuFallback(file) }.onFailure { }
                    }
                }
                return@forEach
            }
            val day = dateRegex.find(file.name)?.groupValues?.getOrNull(1)
            val fileTime = day?.let { LogUtils.parseDayStartMs(it) } ?: file.lastModified()
            if (fileTime > 0L && fileTime < cutoffMs) {
                runCatching { file.delete() }.onFailure { }
            }
        }
    }

    private fun pruneTreeByMtime(root: File, cutoffMs: Long) {
        if (!root.exists()) return
        if (root.isFile) {
            val mtime = root.lastModified()
            if (mtime > 0L && mtime < cutoffMs) {
                runCatching { root.delete() }.onFailure { }
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
                }.onFailure { }
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
                runCatching { file.delete() }.onFailure { }
            }
        }
    }

    /**
     * The bundle header is written by the kit, so the process that exports stamps its own
     * installed package identity rather than a compile-time constant: the same code runs in
     * the manager and in the xmsf runtime, and those two are versioned independently.
     *
     * The channel is the branch this build is distributed from. The shell's own flavor
     * ("normal" / "vc105") selects the bundled MiPush SDK version, not a distribution path,
     * so it cannot stand in for the channel the other two products report.
     */
    private fun buildBundleIdentity(context: Context): DiagnosticBundleIdentity {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        return DiagnosticBundleIdentity.of(
            product = PRODUCT,
            packageName = context.packageName,
            versionName = packageInfo?.versionName.orEmpty(),
            versionCode = packageInfo?.longVersionCode?.toString().orEmpty(),
            distributionChannel = ConfigDefaults.REMOTE_BRANCH,
            commitSha = BuildConfig.GIT_COMMIT,
        )
    }

    private data class PackageIdentity(
        val label: String,
        val packageName: String,
        val versionName: String,
        val versionCode: String,
        val apkPath: String,
        val apkSha256: String,
    ) {
        fun toSummaryLine(): String = "$label package=$packageName versionName=$versionName " +
            "versionCode=$versionCode apkPath=${apkPath.ifBlank { "unavailable" }} " +
            "apkSha256=$apkSha256"

        fun toJson(): JSONObject = JSONObject()
            .put("label", label)
            .put("packageName", packageName)
            .put("versionName", versionName)
            .put("versionCode", versionCode)
            .put("apkPath", apkPath.ifBlank { "unavailable" })
            .put("apkSha256", apkSha256)
    }

    private fun readPackageIdentity(
        context: Context,
        label: String,
        packageName: String,
    ): PackageIdentity {
        val packageInfo = try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (error: Exception) {
            return PackageIdentity(
                label = label,
                packageName = packageName,
                versionName = "unavailable",
                versionCode = "unavailable",
                apkPath = "",
                apkSha256 = "unavailable:${error.javaClass.simpleName}",
            )
        }
        val apkPath = packageInfo.applicationInfo?.sourceDir.orEmpty()
        return PackageIdentity(
            label = label,
            packageName = packageName,
            versionName = packageInfo.versionName.orEmpty().ifBlank { "unavailable" },
            versionCode = packageInfo.longVersionCode.toString(),
            apkPath = apkPath,
            apkSha256 = sha256FileOrUnavailable(apkPath),
        )
    }

    /**
     * Record which build and which installed packages produced this bundle, mirroring the
     * runtime_summary.json that smscode-core writes for XSC and xinyi-relay, so a report
     * handed over from a hand-built install can be told apart from a distributed release.
     *
     * The identity fields come from the same [DiagnosticBundleIdentity] the kit stamps into
     * the bundle header, so the two artifacts cannot drift apart per project.
     */
    private fun writeRuntimeLogSummaryFile(
        context: Context,
        stagingDir: File,
        details: MutableList<String>,
        identity: DiagnosticBundleIdentity,
        identities: List<PackageIdentity>,
    ) {
        runCatching {
            val summary = LogUtils.summarizeFiles(context)
            val files = JSONArray()
            summary.files.forEach { file ->
                files.put(
                    JSONObject()
                        .put("name", file.name)
                        .put("sizeBytes", file.sizeBytes)
                        .put("lineCount", file.lineCount)
                        .put("firstTimestamp", file.firstTimestamp ?: JSONObject.NULL)
                        .put("lastTimestamp", file.lastTimestamp ?: JSONObject.NULL),
                )
            }
            val packages = JSONArray()
            identities.forEach { packages.put(it.toJson()) }
            val payload = JSONObject()
            identity.jsonFields().forEach { (field, value) -> payload.put(field, value) }
            payload
                .put("isCiBuild", BuildConfig.IS_CI_BUILD)
                .put(
                    "verboseLogging",
                    io.github.magisk317.xposed.logging.MagiskLogPolicy.current().verboseLogging,
                )
                .put("fileCount", summary.fileCount)
                .put("totalBytes", summary.totalBytes)
                .put("entryCount", summary.entryCount)
                .put("firstTimestamp", summary.firstTimestamp ?: JSONObject.NULL)
                .put("lastTimestamp", summary.lastTimestamp ?: JSONObject.NULL)
                .put("files", files)
                .put("packages", packages)
            val target = File(stagingDir, "app/log")
            target.mkdirs()
            File(target, RUNTIME_SUMMARY_FILE_NAME).writeText(payload.toString(2))
            details += "exporting package=${identity.packageName} version=${identity.versionName} " +
                "channel=${identity.distributionChannel} commit=${identity.commitSha} " +
                "apkSha256=${identities.firstOrNull()?.apkSha256 ?: "unavailable"}"
        }.onFailure { error ->
            logW("LogBundleExporter runtime summary unavailable: ${error.javaClass.simpleName}")
        }
    }

    private fun sha256FileOrUnavailable(path: String): String {
        if (path.isBlank()) return "unavailable:path_missing"
        val file = File(path)
        if (!file.isFile) return "unavailable:file_missing"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_HASH_BUFFER_SIZE)
            FileInputStream(file).use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
        } catch (error: Exception) {
            "unavailable:${error.javaClass.simpleName}"
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
        // Best-effort: also pull the manager UI process logs so a single runtime-built bundle is
        // self-contained even when the manager-side merge step is skipped. Cross-UID read is not
        // guaranteed, so failures here are non-fatal.
        copyManagerPackageLogs(context, stagingDir, details)
    }

    private fun copyManagerPackageLogs(context: Context, stagingDir: File, details: MutableList<String>) {
        val managerDir = runCatching {
            val foreign = context.createPackageContext(ManagerProtocol.MANAGER_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
            File(foreign.filesDir, PRIVATE_LOG_DIR_NAME)
        }.getOrNull() ?: return
        if (!managerDir.exists() || !managerDir.isDirectory) return
        val staged = File(stagingDir, "app/log/manager")
        val files = managerDir.listFiles().orEmpty().filter {
            it.isFile && it.name.endsWith(".jsonl") && it.name.startsWith("runtime.manager")
        }
        if (files.isEmpty()) return
        copyDirectory(managerDir, staged) { file ->
            file.name.endsWith(".jsonl") && file.name.startsWith("runtime.manager")
        }
        val copied = staged.walkTopDown().any { it.isFile }
        if (copied) {
            details += "manager log: ${managerDir.absolutePath}"
            details += summarizeRuntimeLogFiles(staged)
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
                val memoryRelated = MemoryLimitDiagnostics.isMemoryRelatedExit(info)
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

    private fun ensureDirectory(dir: File, recreateWhenFile: Boolean): Boolean {
        return DiagnosticArchive.ensureDirectory(dir, recreateWhenFile, onWarning = { logW(it) })
    }

    private fun deleteRecursivelyWithSuFallback(target: File): Boolean =
        DiagnosticShell.deleteRecursivelyWithSuFallback(
            target = target,
            rootAvailable = {
                runCatching { com.topjohnwu.superuser.Shell.getShell() }.onFailure { }
                rootCommandAccess.refreshRootAccessIfGranted()
            },
            onWarning = { logW(it) },
        )

    private fun captureLogcat(
        stagingDir: File,
        details: MutableList<String>,
        mode: DiagnosticExportMode,
    ) {
        DiagnosticShell.captureLogcat(
            stagingDir = stagingDir,
            details = details,
            mode = mode,
            suFallback = { output ->
                rootCommandAccess.refreshRootAccessIfGranted() &&
                    dumpRootCommandOutput("logcat -d -v threadtime -b all", output)
            },
            onWarning = { logW(it) },
        )
    }

    private fun copySystemUiHookLogs(stagingDir: File, details: MutableList<String>) {
        val target = File(stagingDir, "app/log")
        if (!ensureDirectory(target, recreateWhenFile = true)) {
            details += "systemui hook log failed: output dir unavailable"
            return
        }
        var copied = false
        SYSTEMUI_HOOK_LOG_DIRS.forEach { path ->
            val src = File(path)
            if (src.exists() && src.canRead()) {
                src.listFiles()?.filter { it.isFile && it.name.endsWith(".jsonl") }?.forEach { file ->
                    file.copyTo(File(target, file.name), overwrite = true)
                    copied = true
                }
            }
        }
        if (copied) {
            details += "systemui hook log: direct"
            return
        }
        val targetPath = target.absolutePath
        val targetUid = runCatching { android.os.Process.myUid() }.getOrDefault(-1)
        val shellCmd = buildString {
            append("mkdir -p ${shQuote(targetPath)}; ")
            SYSTEMUI_HOOK_LOG_DIRS.forEach { path ->
                append("if [ -d ${shQuote(path)} ]; then ")
                append("cp -f ${shQuote(path)}/*.jsonl ${shQuote("$targetPath/")} 2>/dev/null; ")
                if (targetUid > 0) {
                    append("chown $targetUid:$targetUid ${shQuote("$targetPath/")}*.jsonl 2>/dev/null; ")
                }
                append("chmod a+r ${shQuote("$targetPath/")}*.jsonl 2>/dev/null; fi; ")
            }
        }
        val rootReady = rootCommandAccess.refreshRootAccessIfGranted()
        val copiedViaSu = rootReady && runCatching {
            rootCommandAccess.runRootCommand(shellCmd, timeoutMs = ROOT_DIAGNOSTICS_TIMEOUT_MS).isSuccess
        }.getOrDefault(false) &&
            target.listFiles()?.any { it.name.contains("runtime.hook.systemui") } == true
        if (copiedViaSu) {
            details += "systemui hook log: su"
            return
        }
        details += "systemui hook log missing"
    }

    private fun copyLsposedLogs(
        stagingDir: File,
        details: MutableList<String>,
        mode: DiagnosticExportMode,
    ) {
        // The kit owns the directory list (`log` and `log.old`) and the build gate, so a
        // release bundle cannot claim to carry framework logs while collecting half of them.
        DiagnosticShell.copyLsposedLogs(
            stagingDir = stagingDir,
            details = details,
            mode = mode,
            onWarning = { logW(it) },
        )
    }

    /**
     * Snapshot of the Zygisk spoof layer: which config the module is reading, which device
     * profile it resolved, and what the module itself logged. Without this a report cannot show
     * whether an app was ever in the spoof list, or which profile was active when it failed.
     */
    private fun captureZygiskDiagnostics(stagingDir: File, details: MutableList<String>) {
        val output = File(stagingDir, "system/$ZYGISK_DIAGNOSTICS_FILE")
        val parent = output.parentFile
        if (parent == null || !ensureDirectory(parent, recreateWhenFile = true)) {
            details += "zygisk diagnostics failed: output dir unavailable"
            return
        }
        if (!rootCommandAccess.refreshRootAccessIfGranted()) {
            output.writeText("status=unavailable\nreason=root_not_granted\n")
            details += "zygisk diagnostics skipped: root not granted"
            return
        }

        val appConfig = rootCommandAccess.runRootCommand(
            "cat ${shQuote(ZYGISK_CONFIG_PATH)}",
            timeoutMs = ROOT_DIAGNOSTICS_TIMEOUT_MS,
        )
        val deviceConfig = rootCommandAccess.runRootCommand(
            "grep -E ${shQuote("^(#|ro\\.(product|build|miui|hardware|boot\\.hardware|soc)\\.|persist\\.sys\\.(country|oem|miui))")}" +
                " ${shQuote("/data/adb/mipush_zygisk/device.conf")} 2>/dev/null || true",
            timeoutMs = ROOT_DIAGNOSTICS_TIMEOUT_MS,
        )
        val runtime = rootCommandAccess.runRootCommand(
            """
            for p in /data/adb/modules/mipush_zygisk /data/adb/modules/MiPushZygisk /data/adb/modules/mipushzygisk; do
                if [ -f "${'$'}p/module.prop" ]; then
                    echo "## module.prop:${'$'}p"
                    grep -E '^(id|name|version|versionCode|description|author)=' "${'$'}p/module.prop" || true
                fi
            done
            echo '## mipushcut state'
            cat /data/adb/mipush_zygisk/mipushcut.state 2>/dev/null || true
            echo '## selected properties'
            getprop | grep -E '^(\[mipush\.zygisk\.enabled\]|\[ro\.(product|build|miui|hardware|boot\.hardware|soc)\.|\[persist\.sys\.(country|oem|miui)\.)' || true
            echo '## MiPushZygisk logcat'
            logcat -d -v threadtime -b all -s 'MiPushZygisk:*' | tail -n 2000 || true
            """.trimIndent(),
            timeoutMs = ROOT_DIAGNOSTICS_TIMEOUT_MS,
        )
        val text = buildString {
            appendLine("schemaVersion=1")
            appendLine("status=available")
            appendLine("configPath=$ZYGISK_CONFIG_PATH")
            appendLine("configReadSuccess=${appConfig.isSuccess}")
            appendLine("configExitCode=${appConfig.exitCode}")
            appendLine("configTimedOut=${appConfig.timedOut}")
            appendLine("configEntryCount=${safeZygiskPackages(appConfig.stdoutText).size}")
            appendLine("[app.conf]")
            appendLine(appConfig.stdoutText.ifBlank { "<empty>" })
            appendLine("[device.conf platform-only]")
            appendLine(deviceConfig.stdoutText.ifBlank { "<empty>" })
            appendLine("[module/property/logcat]")
            appendLine(runtime.stdoutText.ifBlank { "<empty>" })
            if (runtime.stderrText.isNotBlank()) {
                appendLine("[stderr]")
                appendLine(runtime.stderrText)
            }
        }
        output.writeText(text)
        details += "zygisk diagnostics: system/$ZYGISK_DIAGNOSTICS_FILE"
    }

    /**
     * Per-target-app exit reasons and crash lines. `getHistoricalProcessExitReasons` only accepts
     * the calling package, so the previous bundle could never show why a spoofed app died or was
     * killed; the root `dumpsys activity exit-info` call covers every registered package plus the
     * apps in the spoof list.
     */
    private fun captureTargetProcessDiagnostics(
        context: Context,
        stagingDir: File,
        details: MutableList<String>,
    ) {
        val output = File(stagingDir, "system/$TARGET_PROCESS_DIAGNOSTICS_FILE")
        val parent = output.parentFile
        if (parent == null || !ensureDirectory(parent, recreateWhenFile = true)) {
            details += "target process diagnostics failed: output dir unavailable"
            return
        }
        if (!rootCommandAccess.refreshRootAccessIfGranted()) {
            output.writeText("status=unavailable\nreason=root_not_granted\n")
            details += "target process diagnostics skipped: root not granted"
            return
        }

        val configuredPackages = rootCommandAccess.runRootCommand(
            "cat ${shQuote(ZYGISK_CONFIG_PATH)}",
            timeoutMs = ROOT_DIAGNOSTICS_TIMEOUT_MS,
        ).stdoutText
        val registeredPackages = runCatching {
            RegisteredApplicationDb.getList(null).map { it.packageName }
        }.getOrDefault(emptyList())
        val targets = (listOf(context.packageName, Constants.SERVICE_APP_NAME) +
            safeZygiskPackages(configuredPackages) + registeredPackages)
            .map { it.substringBefore('|').removePrefix("-").trim() }
            .filter(::isSafePackageName)
            .distinct()
            .take(64)
        if (targets.isEmpty()) {
            output.writeText("status=unavailable\nreason=no_target_packages\n")
            details += "target process diagnostics skipped: no target packages"
            return
        }

        val packagePattern = targets.joinToString("|") { it.replace(".", "\\.") }
        val command = buildString {
            targets.forEach { packageName ->
                append("echo ").append(shQuote("## exit-info:$packageName")).append("; ")
                append("dumpsys activity exit-info ").append(shQuote(packageName))
                    .append(" 2>&1 | tail -n 120; ")
            }
            append("echo ").append(shQuote("## crash-logcat")).append("; ")
            append("logcat -d -v threadtime -b crash | grep -E ")
                .append(shQuote(packagePattern)).append(" | tail -n 1500 || true; ")
            append("echo ").append(shQuote("## activity-logcat")).append("; ")
            append("logcat -d -v threadtime -b all | grep -E ")
                .append(shQuote(packagePattern)).append(" | grep -E ")
                .append(shQuote("FATAL EXCEPTION|AndroidRuntime|ANR|am_crash|Process .* has died"))
                .append(" | tail -n 1500 || true")
        }
        val result = rootCommandAccess.runRootCommand(
            command,
            timeoutMs = TARGET_PROCESS_DIAGNOSTICS_TIMEOUT_MS,
        )
        val text = buildString {
            appendLine("schemaVersion=1")
            appendLine("status=${if (result.isSuccess) "available" else "partial"}")
            appendLine("targetCount=${targets.size}")
            appendLine("targets=${targets.joinToString(",")}")
            appendLine("exitCode=${result.exitCode}")
            appendLine("timedOut=${result.timedOut}")
            appendLine("skipped=${result.skipped}")
            appendLine("[stdout]")
            appendLine(result.stdoutText.ifBlank { "<empty>" })
            appendLine("[stderr]")
            appendLine(result.stderrText.ifBlank { "<empty>" })
        }
        output.writeText(text)
        details += "target process diagnostics: system/$TARGET_PROCESS_DIAGNOSTICS_FILE targets=${targets.size}"
    }

    /** Package names from a Zygisk app.conf, so diagnostics can cover the spoofed apps. */
    private fun safeZygiskPackages(content: String): List<String> = content.lineSequence()
        .map { it.trim() }
        .filter {
            it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("profile=") &&
                !it.startsWith("observe=") && !it.startsWith("spoof=")
        }
        .map { it.removePrefix("-") }
        .map { it.substringBefore('|') }
        .filter(::isSafePackageName)
        .distinct()
        .toList()

    private fun isSafePackageName(value: String): Boolean =
        Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+").matches(value)

    private fun captureXmsfKeepaliveDiagnostics(stagingDir: File, details: MutableList<String>) {
        runCatching { com.topjohnwu.superuser.Shell.getShell() }.onFailure { }
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

    private fun shQuote(value: String): String = DiagnosticShell.shQuote(value)

    private fun pruneCurrentDayLocalLogs(context: Context, now: Date) {
        runCatching {
            LogUtils.pruneAllLogArtifacts(context, now, force = true)
        }.onFailure {
            logW("Failed to prune local logs before export: ${it.message ?: it.javaClass.simpleName}")
        }
    }
}
