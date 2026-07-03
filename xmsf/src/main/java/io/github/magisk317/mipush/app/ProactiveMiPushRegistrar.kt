package io.github.magisk317.mipush.app

import android.content.Context
import android.content.pm.PackageManager
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.platform.support.LegacyComponentNames
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scans installed apps for MiPush credentials in their manifests and proactively
 * triggers registration for apps that haven't registered yet.
 *
 * This handles cases where the app's push framework (e.g. mPaaS) discovers xmsf
 * but doesn't send a registration intent.
 */
object ProactiveMiPushRegistrar {
    private const val TAG = "ProactiveMiPushRegistrar"
    private const val MIN_SCAN_INTERVAL_MS = 120_000L

    private val running = AtomicBoolean(false)
    private val lastScanAtMs = java.util.concurrent.atomic.AtomicLong(0L)

    // Known metadata keys for MiPush credentials
    private val appIdKeys = arrayOf(
        "com.xiaomi.push.api_id",
        "com.xiaomi.push.app_id",
        "com.xiaomi.mipush.APP_ID",
        "org.android.agoo.xiaomi.app_id",
        "mipush_app_id",
        "MIPUSH_APPID",
        "MI_PUSH_APP_ID",
        "MIAPP_ID",
        "XM_APP_ID",
        "XIAOMI_APP_ID",
        "XIAOMI_PUSH_APP_ID",
        "xiaomi_appid",
    )

    private val appKeyKeys = arrayOf(
        "com.xiaomi.push.api_key",
        "com.xiaomi.push.app_key",
        "com.xiaomi.mipush.APP_KEY",
        "org.android.agoo.xiaomi.app_key",
        "mipush_app_key",
        "MIPUSH_APPKEY",
        "MI_PUSH_APP_KEY",
        "MIAPP_KEY",
        "XM_APP_KEY",
        "XIAOMI_APP_KEY",
        "XIAOMI_PUSH_APP_KEY",
        "xiaomi_appkey",
    )

    fun schedule(context: Context) {
        val nowMs = System.currentTimeMillis()
        val previous = lastScanAtMs.get()
        if (previous > 0 && nowMs - previous in 0 until MIN_SCAN_INTERVAL_MS) return
        if (!lastScanAtMs.compareAndSet(previous, nowMs)) return
        if (!running.compareAndSet(false, true)) return

        val appContext = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scanAndRegister(appContext)
            } finally {
                running.set(false)
            }
        }
    }

    private fun scanAndRegister(context: Context) {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA or PackageManager.GET_SERVICES)
        var registered = 0

        for (pkg in packages) {
            val packageName = pkg.packageName
            if (packageName == context.packageName) continue
            if (!isUserApplication(pkg)) continue
            if (RegisteredApplicationDb.isBlocked(packageName)) continue

            // Check if already registered
            val app = RegisteredApplicationDb.getRegisteredApplication(packageName)
            if (app != null && app.registeredType != io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication.RegisteredType.NotRegistered) continue

            // Check if app has MiPush credentials
            val hasCredentials = hasMiPushCredentials(pkg)
            if (!hasCredentials) continue

            // Check if app has XMPushService (required for MiPush)
            val hasXMPushService = hasXMPushService(pkg)
            if (!hasXMPushService) continue

            logI("found unregistered app with MiPush credentials: $packageName, triggering registration")
            triggerRegistration(context, packageName)
            registered++
        }

        if (registered > 0) {
            logI("proactively registered $registered apps")
        } else {
            logD("no new apps to register")
        }
    }

    private fun hasMiPushCredentials(pkgInfo: android.content.pm.PackageInfo): Boolean {
        return try {
            val metaData = pkgInfo.applicationInfo?.metaData ?: return false
            val hasAppId = appIdKeys.any { key ->
                val value = metaData.getString(key)
                !value.isNullOrBlank()
            }
            val hasAppKey = appKeyKeys.any { key ->
                val value = metaData.getString(key)
                !value.isNullOrBlank()
            }
            hasAppId && hasAppKey
        } catch (_: Throwable) {
            false
        }
    }

    private fun hasXMPushService(pkgInfo: android.content.pm.PackageInfo): Boolean {
        return pkgInfo.services?.any { service ->
            service.name == LegacyComponentNames.LEGACY_MAIN_SERVICE_CLASS
        } == true
    }

    private fun isUserApplication(pkgInfo: android.content.pm.PackageInfo): Boolean {
        val flags = pkgInfo.applicationInfo?.flags ?: 0
        return (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
    }

    private fun triggerRegistration(context: Context, packageName: String) {
        try {
            // Register in database
            RegisteredApplicationDb.registerApplication(packageName)

            // Request registration through PushRuntime
            val success = PushRuntime.requestApplicationRegistration(
                packageName = packageName,
                source = "ProactiveMiPushRegistrar",
                reason = "proactive_scan"
            )
            logI("requested registration for $packageName, success=$success")
        } catch (e: Throwable) {
            logW("failed to trigger registration for $packageName: ${e.message}")
        }
    }
}
