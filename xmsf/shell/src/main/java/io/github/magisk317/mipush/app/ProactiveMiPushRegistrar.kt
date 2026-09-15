package io.github.magisk317.mipush.app

import android.content.Context
import android.content.pm.PackageManager
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.platform.support.XmsfComponentNames
import com.xiaomi.push.service.PushConstants
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.PushRuntimeComponents
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.service.PushServiceStarter
import io.github.magisk317.mipush.service.runtime.RegistrationPayloadRepair
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
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
    private const val SYNTHESIS_COOLDOWN_MS = 15 * 60_000L

    private val running = AtomicBoolean(false)
    private val lastScanAtMs = java.util.concurrent.atomic.AtomicLong(0L)
    private val lastSynthesisAtMs = ConcurrentHashMap<String, Long>()

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
        val startedAt = System.nanoTime()
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA or PackageManager.GET_SERVICES)
        var registered = 0

        for (pkg in packages) {
            val packageName = pkg.packageName
            if (packageName == context.packageName) continue
            val applicationInfo = pkg.applicationInfo ?: continue
            if (!Utils.isAppInstalled(applicationInfo)) continue
            if (!isUserApplication(pkg)) continue
            if (RegisteredApplicationDb.isBlocked(packageName)) continue

            // Check if already registered
            val app = RegisteredApplicationDb.getRegisteredApplication(packageName)
            if (app != null && app.registeredType != RegisteredAppRegisteredType.NotRegistered) continue

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
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to durationMs.toString(),
                "process" to "main",
                "stage" to "proactive",
                "reason" to if (registered > 0) "triggered" else "none",
                "found_count" to registered.toString(),
            ),
            statusOk = true,
        )
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
            service.name == XmsfComponentNames.COMPAT_SERVICE_CLASS
        } == true
    }

    private fun isUserApplication(pkgInfo: android.content.pm.PackageInfo): Boolean {
        val flags = pkgInfo.applicationInfo?.flags ?: 0
        return (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
    }

    private fun triggerRegistration(context: Context, packageName: String) {
        runCatching {
            // Register in database
            RegisteredApplicationDb.registerApplication(packageName)

            // Request registration through PushRuntime
            val success = PushRuntime.requestApplicationRegistration(
                packageName = packageName,
                source = "ProactiveMiPushRegistrar",
                reason = "proactive_scan"
            )
            logI("requested registration for $packageName, success=$success")

            synthesizeServerSideRegistration(context, packageName)
        }.onFailure { error ->
            logW("failed to trigger registration for $packageName: ${error.message}")
        }
    }

    /**
     * Service-side synthetic registration. The in-app nudge above only works while the target
     * SDK keeps the xiaomi channel enabled; apps like com.sgcc.wsgw.cn gate it behind their own
     * cloud config (and some refuse LSPosed injection entirely), so REGISTER_APP never arrives
     * and the app stays unregistered forever. Synthesize the very wire format the app SDK would
     * send (RegistrationPayloadRepair, credential from compat-profiles.json or manifest
     * meta-data) and hand it to XMPushServiceCore over the standard REGISTER_APP intent; the
     * core-side plan keeps the blocked check and the registration throttle.
     */
    private fun synthesizeServerSideRegistration(context: Context, packageName: String) {
        val nowMs = System.currentTimeMillis()
        val previous = lastSynthesisAtMs.put(packageName, nowMs)
        if (previous != null && nowMs - previous < SYNTHESIS_COOLDOWN_MS) return
        val repair = runCatching { RegistrationPayloadRepair.repair(context, packageName) }.getOrNull()
        val payload = repair?.payload
        if (payload == null) {
            logD("no synthetic registration credential for $packageName")
            return
        }
        runCatching {
            val intent = PushRuntimeComponents.newCoreServiceIntent(
                context,
                PushConstants.MIPUSH_ACTION_REGISTER_APP,
            ).apply {
                putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
                putExtra(PushConstants.MIPUSH_EXTRA_APP_ID, repair.appId)
                putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
            }
            PushServiceStarter.start(context, intent)
            logI(
                "synthetic registration submitted for $packageName " +
                    "appId=${repair.appId.take(6)}... payloadBytes=${payload.size}",
            )
            MagiskOtel.event(
                name = "push.register",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "proactive_synthesize",
                    "reason" to "submitted",
                    "target_package" to packageName,
                ),
                statusOk = true,
            )
        }.onFailure {
            lastSynthesisAtMs.remove(packageName)
            logW("failed to submit synthetic registration for $packageName: ${it.message}")
        }
    }
}
