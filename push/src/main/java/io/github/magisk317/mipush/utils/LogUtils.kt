package io.github.magisk317.mipush.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.xiaomi.xmsf.R
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogUtils {
    private val dailyDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dailyLogPattern = Regex("^logs_\\d{4}-\\d{2}-\\d{2}\\.txt$")
    private val dailyModuleLogPattern = Regex("^.+_\\d{4}-\\d{2}-\\d{2}\\.txt$")

    data class ShareIntentResult(
        val intent: Intent?,
        val error: String? = null,
    )

    @JvmStatic
    fun init(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            val logDir = LogBundleExporter.getLogDir(appContext)
            Napier.base(FileAntilog(logDir))
        }.onFailure {
            Napier.base(DebugAntilog())
        }
    }

    private class FileAntilog(private val logDir: File) : Antilog() {
        private val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        override fun performLog(priority: LogLevel, tag: String?, throwable: Throwable?, message: String?) {
            try {
                if (!logDir.exists()) logDir.mkdirs()
                val currentDate = currentDateString()
                pruneDailyFiles(logDir, currentDate, dailyLogPattern)
                val fileName = "logs_${currentDate}.txt"
                val file = File(logDir, fileName)
                val time = logDateFormat.format(Date())
                val errorMsg = throwable?.stackTraceToString() ?: ""
                val line = "$time [${priority.name}] ${tag ?: ""}: ${message ?: ""} $errorMsg\n"
                file.appendText(line)
            } catch (_: Exception) {}
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

    @JvmStatic
    fun prepareShareIntent(context: Context): ShareIntentResult {
        return runCatching {
            val export = LogBundleExporter.buildLogBundle(context)
            val file = export.file ?: return ShareIntentResult(null, export.details)
            ShareIntentResult(
                intent = LogBundleExporter.buildShareIntent(context, file),
                error = export.details,
            )
        }.getOrElse {
            ShareIntentResult(null, it.message ?: it.javaClass.simpleName)
        }
    }

    @JvmStatic
    fun logArchiveName(date: Date): String {
        return "logs_" + dateInfo(date)
    }

    @JvmStatic
    fun dateInfo(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(date)
    }

    internal fun currentDateString(now: Date = Date()): String = dailyDateFormat.format(now)

    internal fun pruneAppLogsForToday(logDir: File, now: Date = Date()) {
        pruneDailyFiles(logDir, currentDateString(now), dailyLogPattern)
    }

    internal fun pruneModuleLogsForToday(moduleLogDir: File, now: Date = Date()) {
        pruneDailyFiles(moduleLogDir, currentDateString(now), dailyModuleLogPattern)
    }

    internal fun pruneDailyFiles(logDir: File, currentDate: String, pattern: Regex) {
        runCatching {
            logDir.listFiles()?.forEach { child ->
                if (child.isFile && pattern.matches(child.name) && !child.name.contains(currentDate)) {
                    child.delete()
                }
            }
        }
    }
}
