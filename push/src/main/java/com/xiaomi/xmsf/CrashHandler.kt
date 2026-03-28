package com.xiaomi.xmsf

import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.aakira.napier.Napier
import com.xiaomi.xmsf.utils.LogUtils
import top.trumeet.common.utils.Utils

object CrashHandler {
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
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val fileName = "Crash_${dateFormat.format(Date())}.txt"
            val file = File(logDir, fileName)
            val time = timeFormat.format(Date())
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
