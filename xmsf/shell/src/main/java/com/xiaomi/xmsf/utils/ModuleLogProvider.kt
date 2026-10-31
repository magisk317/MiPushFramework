package com.xiaomi.xmsf.utils

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.xposed.logging.BaseXposedLogProvider
import io.github.magisk317.xposed.logging.LogProviderQuotaConfig
import io.github.magisk317.xposed.logging.LogProviderQuotaPolicy
import io.github.magisk317.xposed.logging.PackageCallerGuard
import io.github.magisk317.xposed.logging.XposedLogEvent

class ModuleLogProvider : BaseXposedLogProvider() {

    override val authority: String = AUTHORITY

    override val ingressPolicy: LogProviderQuotaPolicy = LogProviderQuotaPolicy(
        LogProviderQuotaConfig(
            maxEventsPerWindow = 600,
            windowMs = 60_000L,
            maxBytesPerDay = Long.MAX_VALUE,
            maxEventsPerDay = Long.MAX_VALUE,
        ),
    )

    private val writeLock = Any()

    override fun isCallerAllowed(context: Context): Boolean =
        ModuleLogIngressPolicy.isCallerAllowed(context)

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val context = context?.applicationContext ?: return null
        if (uri.authority != AUTHORITY || uri.pathSegments != listOf(ENTRY_PATH)) return null
        if (values == null || !isCallerAllowed(context)) return null
        return synchronized(writeLock) {
            super.insert(uri, values)
        }
    }

    override fun appendLog(event: XposedLogEvent) {
        val ctx = context?.applicationContext ?: return
        synchronized(writeLock) {
            val route = ModuleLogIngressPolicy.resolveRoute(event.route)
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

        fun entryUri() = XposedLogEvent.appendUri(AUTHORITY)

        fun authority(context: Context): String = AUTHORITY
    }
}

internal object ModuleLogIngressPolicy {
    private const val DEFAULT_ROUTE = "app"
    private val allowedRoutes = setOf(
        "app",
        "hook",
        "xmsf_hook",
        "nms_hook",
        "notification",
        "manager",
    )

    // Hook processes may only write logs when they are both declared in the module's
    // META-INF/xposed/scope.list and a system package; Personal Assistant ships from
    // /product/priv-app with FLAG_SYSTEM, so it clears PackageCallerGuard the same way the
    // other three do. MipushManifestContractTest pins the two lists against each other.
    private val trustedHookPackages = setOf(
        "com.android.systemui",
        "com.miui.securitycore",
        "com.google.android.documentsui",
        "com.miui.personalassistant",
    )
    private val callerGuard = PackageCallerGuard(trustedHookPackages)

    fun isCallerAllowed(context: Context): Boolean = callerGuard.isCallerAllowed(context)

    internal fun resolveRoute(route: String?): String =
        route?.trim()?.takeIf(allowedRoutes::contains) ?: DEFAULT_ROUTE

    internal fun isTrustedSystemPackage(packageName: String, flags: Int): Boolean =
        callerGuard.isPackageAllowed(packageName, flags)
}
