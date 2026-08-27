package com.xiaomi.xmsf.utils

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.mipush.utils.LogBundleExporter
import io.github.magisk317.xposed.logging.BaseXposedLogProvider
import io.github.magisk317.mipush.common.logging.DailyRouteLogQuota
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
            super.insert(uri, values)
        }
    }

    override fun appendLog(event: XposedLogEvent) {
        val ctx = context?.applicationContext ?: return
        synchronized(writeLock) {
            val route = ModuleLogIngressPolicy.resolveRoute(event.source)
            val estimatedBytes = event.message.toByteArray(Charsets.UTF_8).size.toLong() +
                event.throwable.toByteArray(Charsets.UTF_8).size.toLong() + 1024L
            if (!ModuleLogIngressPolicy.ensurePersistentQuota(
                    logDir = LogBundleExporter.getLogDir(ctx),
                    route = route,
                    incomingBytes = estimatedBytes,
                )
            ) {
                return
            }
            LogUtils.appendModuleLog(
                context = ctx,
                source = route,
                level = event.level.ifBlank { "I" },
                tag = event.tag,
                packageName = event.packageName,
                processName = event.processName,
                message = event.message,
                throwable = event.throwable,
                alreadySanitized = event.sanitized,
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
    const val MAX_PERSISTED_LOG_BYTES = DailyRouteLogQuota.DEFAULT_MAX_BYTES
    internal const val MAX_EVENT_BYTES = 192L * 1024L
    private const val MODULE_ROUTE = "MiPush"

    private val trustedHookPackages = setOf(
        "com.android.systemui",
        "com.miui.securitycore",
        "com.google.android.documentsui",
    )
    private val callerGuard = PackageCallerGuard(trustedHookPackages)

    fun isCallerAllowed(context: Context): Boolean = callerGuard.isCallerAllowed(context)

    internal fun resolveRoute(@Suppress("UNUSED_PARAMETER") source: String): String = MODULE_ROUTE

    fun ensurePersistentQuota(
        logDir: File?,
        route: String = "MiPush",
        incomingBytes: Long = MAX_EVENT_BYTES,
        currentDay: String = java.time.LocalDate.now().toString(),
    ): Boolean = DailyRouteLogQuota.ensureCapacity(
        logDir = logDir,
        route = route,
        currentDay = currentDay,
        incomingBytes = incomingBytes.coerceAtMost(MAX_EVENT_BYTES),
        maxBytes = MAX_PERSISTED_LOG_BYTES,
    )

    internal fun isTrustedSystemPackage(packageName: String, flags: Int): Boolean =
        callerGuard.isPackageAllowed(packageName, flags)
}
