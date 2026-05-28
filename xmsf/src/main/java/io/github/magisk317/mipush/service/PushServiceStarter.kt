package io.github.magisk317.mipush.service

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.runtime.PushRuntimeComponents
import kotlinx.coroutines.launch

object PushServiceStarter {

    /**
     * Cached foreground-start preference to avoid blocking the caller thread.
     * Updated asynchronously; defaults to false (safe fallback — uses startService).
     */
    @Volatile
    private var cachedShouldForegroundStart: Boolean = false

    @JvmStatic
    fun refreshForegroundStartPreference() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            cachedShouldForegroundStart = try {
                Global.configCenter().shouldStartPushAsForegroundServiceAsync()
            } catch (_: Throwable) {
                false
            }
        }
    }

    @JvmStatic
    fun start(context: Context, intent: Intent) {
        try {
            val isXmPushServiceTarget = isXMPushServiceTarget(intent)
            if (isXmPushServiceTarget) {
                XMPushServiceLifecycleBridge.recordPendingStart(intent)
            }
            // The MiPush service is expected to foreground itself via lifecycle callbacks.
            // Starting it with startForegroundService has caused repeated 5s contract ANRs on some ROMs.
            if (isXmPushServiceTarget) {
                context.startService(intent)
                logD("startService target=XMPushService component=${intent.component}")
                return
            }

            val shouldUseForegroundStart = cachedShouldForegroundStart &&
                XMPushServiceLifecycleBridge.canStartForegroundImmediately()
            if (shouldUseForegroundStart) {
                ContextCompat.startForegroundService(context, intent)
                logD("startForegroundService component=${intent.component}")
            } else {
                context.startService(intent)
                logD("startService component=${intent.component}")
            }
        } catch (t: Throwable) {
            logE("failed to start service: ${intent.component}", t)
        }
    }

    private fun isXMPushServiceTarget(intent: Intent): Boolean {
        return intent.component?.className == PushRuntimeComponents.LEGACY_MAIN_SERVICE_CLASS
    }
}
