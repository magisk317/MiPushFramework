package io.github.magisk317.mipush.service

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.runtime.PushRuntimeComponents
import kotlinx.coroutines.launch
import io.github.magisk317.xposed.logging.MagiskOtel

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
        val startedAt = System.nanoTime()
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
                emitStart(
                    startedAt = startedAt,
                    result = "ok",
                    reason = "xmpush_start_service",
                    statusOk = true,
                )
                return
            }

            val shouldUseForegroundStart = cachedShouldForegroundStart &&
                XMPushServiceLifecycleBridge.canStartForegroundImmediately()
            if (shouldUseForegroundStart) {
                ContextCompat.startForegroundService(context, intent)
                logD("startForegroundService component=${intent.component}")
                emitStart(
                    startedAt = startedAt,
                    result = "ok",
                    reason = "foreground_service",
                    statusOk = true,
                )
            } else {
                context.startService(intent)
                logD("startService component=${intent.component}")
                emitStart(
                    startedAt = startedAt,
                    result = "ok",
                    reason = "start_service",
                    statusOk = true,
                )
            }
        } catch (t: Throwable) {
            logE("failed to start service: ${intent.component}", t)
            emitStart(
                startedAt = startedAt,
                result = "error",
                reason = "exception",
                statusOk = false,
                errorClass = t.javaClass.simpleName,
            )
        }
    }

    private fun emitStart(
        startedAt: Long,
        result: String,
        reason: String,
        statusOk: Boolean,
        errorClass: String? = null,
    ) {
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "main",
            "stage" to "start",
            "reason" to reason,
        )
        if (!errorClass.isNullOrBlank()) {
            attrs["error_class"] = errorClass
        }
        MagiskOtel.event(name = "push.service", attributes = attrs, statusOk = statusOk)
    }

    private fun isXMPushServiceTarget(intent: Intent): Boolean {
        return intent.component?.className == PushRuntimeComponents.CORE_SERVICE_CLASS
    }
}
