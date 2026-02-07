package com.xiaomi.xmsf

import android.widget.Toast
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.elvishew.xlog.flattener.ClassicFlattener
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.elvishew.xlog.printer.file.naming.DateFileNameGenerator
import com.xiaomi.xmsf.utils.LogUtils
import top.trumeet.common.utils.Utils

object CrashHandler {
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    init {
        initDefaultHandler()
    }

    @JvmStatic
    fun installCrashLogger() {
        val tag = CrashHandler::class.java.simpleName
        val logger: Logger = XLog.tag(tag).build()
        val crashLogger: Logger = XLog.tag(tag).printers(createCrashPrinter()).build()

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
            logger.e("Mi Push Crash", e)
            crashLogger.e("Mi Push Crash", e)
        }
    }

    private fun createCrashPrinter(): FilePrinter {
        val days7InMillis = 7 * 24 * 60 * 60 * 1000
        return FilePrinter.Builder(LogUtils.getLogFolder(Utils.getApplication()!!))
            .fileNameGenerator(object : DateFileNameGenerator() {
                override fun generateFileName(logLevel: Int, timestamp: Long): String {
                    return "Crash_" + super.generateFileName(logLevel, timestamp)
                }
            })
            .backupStrategy(NeverBackupStrategy())
            .cleanStrategy(FileLastModifiedCleanStrategy(days7InMillis.toLong()))
            .flattener(ClassicFlattener())
            .build()
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
