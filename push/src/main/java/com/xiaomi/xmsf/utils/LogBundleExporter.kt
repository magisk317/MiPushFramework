package com.xiaomi.xmsf.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import top.trumeet.common.Constants

internal object LogBundleExporter {
    private const val ZIP_MIME_TYPE = "application/zip"
    private const val EXPORT_FILE_PREFIX = "mipush_logs_"
    private const val STAGING_DIR_PREFIX = ".tmp_mipush_logs_"
    private const val PRIVATE_LOG_DIR_NAME = "log"
    private const val PRIVATE_CRASH_DIR_NAME = "crash"
    private const val PRIVATE_EXPORT_DIR_NAME = "xmsf_logs"
    private const val LEGACY_CACHE_LOG_DIR_NAME = "logs"
    private const val MI_PUSH_LOG_DIR_NAME = "MiPushLog"
    private val crashFilePattern = Regex("^Crash_\\d{4}-\\d{2}-\\d{2}\\.txt$")
    private val LSPOSED_LOG_DIRS = listOf(
        "/data/adb/lspd/log",
    )
    private val opLock = Any()
    private val logger = object {
        fun i(message: String) = Napier.i(message, tag = "LogBundleExporter")
        fun w(message: String) = Napier.w(message, tag = "LogBundleExporter")
        fun e(message: String, throwable: Throwable? = null) = Napier.e(message, throwable, tag = "LogBundleExporter")
    }

    data class ExportResult(
        val file: File?,
        val details: String,
    )

    data class ClearResult(
        val success: Boolean,
        val details: String,
    )

    fun buildLogBundle(context: Context): ExportResult {
        synchronized(opLock) {
            val now = Date()
            val timestamp = LogUtils.dateInfo(now)
            pruneCurrentDayLocalLogs(context, now)
            val exportDir = getPrivateExportDir(context)
            if (!ensureDirectory(exportDir, recreateWhenFile = true)) {
                val details = "export root unavailable: ${exportDir.absolutePath}"
                logger.e(details)
                return ExportResult(null, details)
            }
            val stagingDir = File(exportDir, "${STAGING_DIR_PREFIX}$timestamp").apply {
                if (exists()) {
                    deleteRecursivelyWithSuFallback(this)
                }
            }
            if (!ensureDirectory(stagingDir, recreateWhenFile = true)) {
                val details = "staging dir unavailable: ${stagingDir.absolutePath}"
                logger.e(details)
                return ExportResult(null, details)
            }
            val details = mutableListOf<String>()
            try {
                copyAppLogs(context, stagingDir, details)
                copyCrashLogs(context, stagingDir, details)
                copyMiPushSdkLogs(context, stagingDir, details)
                val lsposedCopied = copyLsposedLogs(stagingDir, details)
                if (!lsposedCopied) {
                    details += "lsposed log missing or unreadable"
                }
                captureLogcat(stagingDir, details)

                val payloadCount = stagingDir.walkTopDown()
                    .count { it.isFile }
                if (payloadCount == 0) {
                    val noDataDetails = details.joinToString("; ").ifBlank { "no log sources available" }
                    logger.w("buildLogBundle skipped: $noDataDetails")
                    return ExportResult(null, noDataDetails)
                }

                File(stagingDir, "summary.txt").writeText(
                    buildString {
                        appendLine("Export Time: $timestamp")
                        appendLine("Package: ${context.packageName}")
                        details.forEach { appendLine("- $it") }
                    },
                )

                val zipFile = File(exportDir, "${EXPORT_FILE_PREFIX}$timestamp.zip")
                zipDirectory(stagingDir, zipFile)
                setFileWorldReadable(zipFile, 2)
                val detailSummary = details.joinToString("; ")
                logger.i("buildLogBundle success: file=${zipFile.absolutePath} size=${zipFile.length()} details=$detailSummary")
                return ExportResult(zipFile, detailSummary)
            } catch (t: Throwable) {
                logger.e("buildLogBundle failed", t)
                return ExportResult(null, t.message ?: t.javaClass.simpleName)
            } finally {
                runCatching {
                    if (!deleteRecursivelyWithSuFallback(stagingDir)) {
                        logger.w("Failed to cleanup staging dir: ${stagingDir.absolutePath}")
                    }
                }
            }
        }
    }

    fun buildShareIntent(context: Context, file: File): Intent {
        require(file.exists() && file.isFile && file.canRead()) {
            "share file unavailable: ${file.absolutePath}"
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            Constants.AUTHORITY_FILE_PROVIDER,
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = ZIP_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        val resolvedTargets = context.packageManager.queryIntentActivities(intent, 0)
        resolvedTargets.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@forEach
            runCatching {
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure {
                logger.w(
                    "grantUriPermission failed: pkg=$packageName uri=$uri err=${it.message ?: it.javaClass.simpleName}",
                )
            }
        }
        logger.i("buildShareIntent: file=${file.absolutePath} size=${file.length()} uri=$uri targets=${resolvedTargets.size}")
        return intent
    }

    fun clearLogFolders(context: Context): ClearResult {
        synchronized(opLock) {
            val details = mutableListOf<String>()
            var success = true
            val targets = listOf(
                "log" to getLogDir(context),
                "crash" to getCrashDir(context),
                "legacy_cache_log" to getLegacyCacheLogDir(context),
                "private_export" to getPrivateExportDir(context),
            )
            targets.forEach { (name, dir) ->
                val ok = clearDirectoryContents(dir)
                if (ok) {
                    details += "$name cleared"
                } else {
                    success = false
                    details += "$name clear failed"
                }
            }
            return ClearResult(success = success, details = details.joinToString("; "))
        }
    }

    fun getLogDir(context: Context): File = ensurePrivateSubDir(context, PRIVATE_LOG_DIR_NAME)

    fun getCrashDir(context: Context): File = ensurePrivateSubDir(context, PRIVATE_CRASH_DIR_NAME)

    private fun getLegacyCacheLogDir(context: Context): File = File(context.cacheDir, LEGACY_CACHE_LOG_DIR_NAME)

    private fun getPrivateExportDir(context: Context): File = ensurePrivateSubDir(context, PRIVATE_EXPORT_DIR_NAME)

    private fun getMiPushSdkLogDir(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, MI_PUSH_LOG_DIR_NAME) }

    private fun ensurePrivateSubDir(context: Context, name: String): File {
        val dir = File(context.filesDir, name)
        ensureDirectory(dir, recreateWhenFile = true)
        return dir
    }

    private fun copyAppLogs(context: Context, stagingDir: File, details: MutableList<String>) {
        val sources = listOf(
            "log" to getLogDir(context),
            "legacy_cache_log" to getLegacyCacheLogDir(context),
        )
        var copiedAny = false
        sources.forEach { (label, src) ->
            if (src.exists() && src.isDirectory && src.listFiles()?.isNotEmpty() == true) {
                copyDirectory(src, File(stagingDir, "app/$label"))
                details += "$label: ${src.absolutePath}"
                copiedAny = true
            }
        }
        val moduleLogDir = File(getLogDir(context), "modules")
        if (moduleLogDir.exists() && moduleLogDir.isDirectory && moduleLogDir.listFiles()?.isNotEmpty() == true) {
            details += "module log: ${moduleLogDir.absolutePath}"
        }
        if (!copiedAny) {
            details += "app log missing"
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
                if (target.walkTopDown().any { it.isFile }) {
                    details += "lsposed direct: $path"
                    copied = true
                }
            }
        }
        if (copied) return true

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
        val su = dumpCommandOutput(listOf("su", "-c", "logcat -d -v threadtime -b all"), output)
        if (su) {
            details += "logcat: su"
        }
    }

    private fun dumpCommandOutput(command: List<String>, output: File): Boolean {
        return runCatching {
            val parent = output.parentFile ?: return false
            if (!ensureDirectory(parent, recreateWhenFile = true)) return false
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            output.outputStream().use { out ->
                process.inputStream.copyTo(out)
            }
            process.waitFor(6, TimeUnit.SECONDS)
            if (process.isAlive) {
                process.destroy()
            }
            output.exists() && output.length() > 0
        }.getOrDefault(false)
    }

    private fun copyDirectory(source: File, target: File, includeFile: (File) -> Boolean = { true }) {
        source.walkTopDown().forEach { file ->
            val relative = file.relativeTo(source).path
            val dest = if (relative.isEmpty()) target else File(target, relative)
            if (file.isDirectory) {
                ensureDirectory(dest, recreateWhenFile = true)
                return@forEach
            }
            if (!includeFile(file)) {
                return@forEach
            }
            val parent = dest.parentFile ?: return@forEach
            if (!ensureDirectory(parent, recreateWhenFile = true)) {
                logger.w("Skip copy due to invalid parent dir: ${dest.absolutePath}")
                return@forEach
            }
            runCatching {
                file.copyTo(dest, overwrite = true)
            }.onFailure {
                logger.w(
                    "Skip copy file failed: src=${file.absolutePath} dst=${dest.absolutePath} err=${it.message ?: it.javaClass.simpleName}",
                )
            }
        }
    }

    private fun zipDirectory(sourceDir: File, outputZip: File) {
        val parent = outputZip.parentFile
        if (parent != null && !ensureDirectory(parent, recreateWhenFile = true)) {
            throw IOException("zip output dir unavailable: ${parent.absolutePath}")
        }
        FileOutputStream(outputZip).use { fos ->
            ZipOutputStream(fos).use { zos ->
                sourceDir.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        runCatching {
                            val entryName = file.relativeTo(sourceDir).invariantSeparatorsPath
                            zos.putNextEntry(ZipEntry(entryName))
                            file.inputStream().use { input -> input.copyTo(zos) }
                            zos.closeEntry()
                        }.onFailure {
                            logger.w(
                                "Skip zipping unreadable file: ${file.absolutePath} err=${it.message ?: it.javaClass.simpleName}",
                            )
                        }
                    }
            }
        }
    }

    private fun clearDirectoryContents(dir: File): Boolean {
        return runCatching {
            if (!ensureDirectory(dir, recreateWhenFile = true)) {
                return@runCatching false
            }
            var deletedAll = true
            dir.listFiles().orEmpty().forEach { child ->
                if (!deleteRecursivelyWithSuFallback(child)) {
                    deletedAll = false
                    logger.w("Failed to delete log child: ${child.absolutePath}")
                }
            }
            deletedAll && ensureDirectory(dir, recreateWhenFile = true)
        }.getOrDefault(false)
    }

    private fun ensureDirectory(dir: File, recreateWhenFile: Boolean): Boolean {
        if (dir.exists()) {
            if (dir.isDirectory) return true
            if (!recreateWhenFile) return false
            if (!dir.delete()) {
                logger.w("Failed to delete non-directory path: ${dir.absolutePath}")
                return false
            }
        }
        if (!dir.mkdirs() && !dir.exists()) {
            logger.w("Failed to mkdirs for path: ${dir.absolutePath}")
            return false
        }
        return dir.isDirectory
    }

    private fun deleteRecursivelyWithSuFallback(target: File): Boolean {
        if (!target.exists()) return true
        if (target.deleteRecursively()) return true
        val suResult = runSuCommand("rm -rf ${shQuote(target.absolutePath)}")
        val deleted = !target.exists()
        if (!deleted) {
            logger.w(
                "su rm fallback failed: path=${target.absolutePath} exit=${suResult.exitCode} stderr=${suResult.stderr} stdout=${suResult.stdout}",
            )
        }
        return deleted
    }

    private fun setFileWorldReadable(file: File, parentDepth: Int) {
        var currentFile: File? = file
        if (!file.exists()) return
        repeat(parentDepth + 1) {
            currentFile?.setReadable(true, false)
            currentFile?.setExecutable(true, false)
            currentFile = currentFile?.parentFile
        }
    }

    private data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun runSuCommand(command: String): ShellResult = try {
        val process = ProcessBuilder("su", "-c", command).start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        ShellResult(exitCode, stdout, stderr)
    } catch (e: IOException) {
        ShellResult(-1, "", e.message ?: e.javaClass.simpleName)
    } catch (e: SecurityException) {
        ShellResult(-1, "", e.message ?: e.javaClass.simpleName)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        ShellResult(-1, "", e.message ?: e.javaClass.simpleName)
    }

    private fun shQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun pruneCurrentDayLocalLogs(context: Context, now: Date) {
        runCatching {
            LogUtils.pruneAppLogsForToday(getLogDir(context), now)
            LogUtils.pruneAppLogsForToday(getLegacyCacheLogDir(context), now)
            LogUtils.pruneModuleLogsForToday(File(getLogDir(context), "modules"), now)
            LogUtils.pruneDailyFiles(getCrashDir(context), LogUtils.currentDateString(now), crashFilePattern)
        }.onFailure {
            logger.w("Failed to prune local logs before export: ${it.message ?: it.javaClass.simpleName}")
        }
    }
}
