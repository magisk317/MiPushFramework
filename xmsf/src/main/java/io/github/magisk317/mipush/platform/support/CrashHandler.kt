package io.github.magisk317.mipush.platform.support

import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.mipush.common.utils.Utils

object CrashHandler {
    private val crashFilePattern = Regex("^Crash_\\d{4}-\\d{2}-\\d{2}\\.txt$")
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    init {
        initDefaultHandler()
    }

    @JvmStatic
    fun installCrashLogger() {
        val tag = "CrashHandler"
        val logDir = File(LogUtils.getCrashFolder(Utils.getApplication()!!))

        install { _, e ->
            val crashInfo = StringBuilder()
            crashInfo.append("Mi Push Crash:\n")
            crashInfo.append(e)
            val stackTrace = e.stackTrace
            for (i in 0 until minOf(3, stackTrace.size)) {
                crashInfo.append("\n    ")
                crashInfo.append(stackTrace[i])
            }
            Utils.makeText(crashInfo, Toast.LENGTH_LONG)
            Napier.e("Mi Push Crash", e, tag = tag)
            writeCrashToFile(logDir, e)
        }
    }

    private fun writeCrashToFile(logDir: File, throwable: Throwable) {
        try {
            if (!logDir.exists()) logDir.mkdirs()
            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val now = Date()
            val currentDate = LogUtils.currentDateString(now)
            LogUtils.pruneDailyFiles(logDir, currentDate, crashFilePattern)
            val fileName = "Crash_${currentDate}.txt"
            val file = File(logDir, fileName)
            val time = timeFormat.format(now)
            val line = "$time [ERROR] CrashHandler: Mi Push Crash ${throwable.stackTraceToString()}\n"
            file.appendText(line)
        } catch (_: Exception) {}
    }



    @JvmStatic
    fun install(handler: Thread.UncaughtExceptionHandler) {
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            handler.uncaughtException(t, e)
            defaultHandler?.uncaughtException(t, e)
        }
    }

    @JvmStatic
    fun uninstall() {
        Thread.setDefaultUncaughtExceptionHandler(defaultHandler)
    }

    @JvmStatic
    fun initDefaultHandler() {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    }
}
