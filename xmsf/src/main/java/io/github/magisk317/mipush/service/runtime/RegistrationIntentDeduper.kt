package io.github.magisk317.mipush.service.runtime

import android.content.Intent
import com.xiaomi.push.service.PushConstants
import java.util.concurrent.ConcurrentHashMap

object RegistrationIntentDeduper {
    const val DEDUP_WINDOW_MS = 30_000L

    private val lastSeenAtMs = ConcurrentHashMap<String, Long>()

    @JvmStatic
    fun shouldDrop(scope: String, intent: Intent?, nowMs: Long = System.currentTimeMillis()): Boolean {
        return shouldDrop(
            scope = scope,
            action = intent?.action,
            packageName = intent?.let(::packageName),
            nowMs = nowMs
        )
    }

    @JvmStatic
    fun shouldDrop(
        scope: String,
        action: String?,
        packageName: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (action != PushConstants.MIPUSH_ACTION_REGISTER_APP) return false
        return shouldDropRegister(scope, packageName, nowMs)
    }

    @JvmStatic
    fun shouldDropRegister(
        scope: String,
        packageName: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (packageName.isNullOrBlank()) return false
        val previous = lastSeenAtMs.put("$scope:$packageName", nowMs)
        return previous != null && nowMs - previous < DEDUP_WINDOW_MS
    }

    @JvmStatic
    fun shouldDrop(intent: Intent?, nowMs: Long = System.currentTimeMillis()): Boolean {
        return shouldDrop("default", intent, nowMs)
    }

    @JvmStatic
    fun reset() {
        lastSeenAtMs.clear()
    }

    @JvmStatic
    fun reset(packageName: String) {
        lastSeenAtMs.keys.removeIf { it.endsWith(":$packageName") || it == packageName }
    }

    @JvmStatic
    fun packageName(intent: Intent): String? {
        return packageName(
            appPackage = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE),
            extraPackage = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME),
            intentPackage = intent.`package`
        )
    }

    @JvmStatic
    fun packageName(
        appPackage: String?,
        extraPackage: String?,
        intentPackage: String?
    ): String? {
        return appPackage?.takeIf { it.isNotBlank() }
            ?: extraPackage?.takeIf { it.isNotBlank() }
            ?: intentPackage?.takeIf { it.isNotBlank() }
    }
}
