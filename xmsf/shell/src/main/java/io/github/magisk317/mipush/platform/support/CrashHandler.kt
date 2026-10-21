package io.github.magisk317.mipush.platform.support

import android.widget.Toast
import java.io.File
import co.touchlab.kermit.Logger
import io.github.magisk317.xposed.logging.CrashLogRecorder
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.mipush.common.utils.Utils

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
            Logger.withTag(tag).e(e) { "Mi Push Crash" }
        }
        // Installed last so the recorder chains through the reporter above and owns the file:
        // the crash name and whether the stack is redacted come from the kit policy, and the
        // retention prune of this directory stays in LogUtils.pruneAllLogArtifacts.
        CrashLogRecorder.install(dirProvider = { logDir })
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
        CrashLogRecorder.uninstall()
        Thread.setDefaultUncaughtExceptionHandler(defaultHandler)
    }

    @JvmStatic
    fun initDefaultHandler() {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    }
}
