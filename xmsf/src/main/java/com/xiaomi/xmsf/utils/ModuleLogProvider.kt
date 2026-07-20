package com.xiaomi.xmsf.utils

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.mipush.utils.LogBundleExporter
import io.github.magisk317.xposed.logging.BaseXposedLogProvider
import io.github.magisk317.xposed.logging.FixedWindowIngressLimiter
import io.github.magisk317.xposed.logging.PackageCallerGuard
import io.github.magisk317.xposed.logging.XposedLogEvent
import java.io.File

class ModuleLogProvider : BaseXposedLogProvider() {

    override val authority: String = AUTHORITY

    private val writeLock = Any()

    override fun isCallerAllowed(context: Context): Boolean =
        ModuleLogIngressPolicy.isCallerAllowed(context)

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val context = context?.applicationContext ?: return null
        if (uri.authority != AUTHORITY || uri.pathSegments != listOf(ENTRY_PATH)) return null
        if (values == null || !isCallerAllowed(context)) return null
        val callingUid = Binder.getCallingUid()
        if (!ingressLimiter.tryAcquire(callingUid, System.currentTimeMillis())) return null
        return synchronized(writeLock) {
            if (!ModuleLogIngressPolicy.ensurePersistentQuota(LogBundleExporter.getLogDir(context))) return null
            super.insert(uri, values)
        }
    }

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
        private const val ENTRY_PATH = "entry"
        private val ingressLimiter = FixedWindowIngressLimiter(maxEvents = 600, windowMs = 60_000L)

        fun entryUri() = XposedLogEvent.appendUri(AUTHORITY)

        fun authority(context: Context): String = AUTHORITY
    }
}

internal object ModuleLogIngressPolicy {
    const val MAX_PERSISTED_LOG_BYTES = 32L * 1024L * 1024L
    internal const val MAX_EVENT_BYTES = 192L * 1024L

    private const val PERSISTED_LOG_HEADROOM_BYTES = 64L * 1024L
    private const val MAX_EVENT_FILE_COPIES = 2
    private val trustedHookPackages = setOf(
        "com.android.systemui",
        "com.miui.securitycore",
        "com.google.android.documentsui",
    )
    private val callerGuard = PackageCallerGuard(trustedHookPackages)

    fun isCallerAllowed(context: Context): Boolean = callerGuard.isCallerAllowed(context)

    fun ensurePersistentQuota(logDir: File?): Boolean {
        val runtimeLogs = logDir
            ?.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith("runtime") && it.extension == "jsonl" }
            .sortedWith(compareBy(File::lastModified, File::getName))
        var totalBytes = runtimeLogs.sumOf(File::length)
        val targetBytes = MAX_PERSISTED_LOG_BYTES -
            PERSISTED_LOG_HEADROOM_BYTES -
            MAX_EVENT_FILE_COPIES * MAX_EVENT_BYTES
        if (totalBytes <= targetBytes) return true
        for (file in runtimeLogs) {
            val fileBytes = file.length()
            if (file.delete()) totalBytes -= fileBytes
            if (totalBytes <= targetBytes) return true
        }
        return totalBytes <= targetBytes
    }

    internal fun isTrustedSystemPackage(packageName: String, flags: Int): Boolean =
        callerGuard.isPackageAllowed(packageName, flags)
}
