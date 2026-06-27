package com.xiaomi.xmsf.utils

import android.content.Context
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.xposed.logging.BaseXposedLogProvider
import io.github.magisk317.xposed.logging.XposedLogEvent

class ModuleLogProvider : BaseXposedLogProvider() {

    override val authority: String = AUTHORITY

    private val writeLock = Any()

    override fun appendLog(event: XposedLogEvent) {
        val ctx = context?.applicationContext ?: return
        synchronized(writeLock) {
            LogUtils.appendModuleLog(
                context = ctx,
                source = event.source,
                level = event.level.ifBlank { "I" },
                tag = event.tag,
                packageName = event.packageName,
                processName = event.processName,
                message = event.message,
                throwable = event.throwable,
            )
        }
    }

    companion object {
        private const val AUTHORITY = "com.xiaomi.xmsf.module.log"

        fun entryUri() = XposedLogEvent.appendUri(AUTHORITY)

        fun authority(context: Context): String = AUTHORITY
    }
}
