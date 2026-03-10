package com.magisk317.diagnostics

import android.app.Application
import android.content.Context
import com.magisk317.Global
import com.magisk317.service.XMPushServiceLifecycleBridge
import com.xiaomi.mipush.sdk.MiPushClient
import com.xiaomi.xmsf.push.control.PushControllerUtils
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking

object PushHealthSnapshotLogger {
    private const val TAG = "PushHealthSnapshot"

    data class Snapshot(
        val stage: String,
        val processName: String,
        val pushEnabled: Boolean,
        val regIdPresent: Boolean,
        val debugMode: Boolean,
        val lifecycleReady: Boolean,
        val lifecyclePendingCount: Int,
        val extra: String?
    )

    fun log(context: Context, stage: String, extra: String? = null) {
        runCatching {
            val snapshot = capture(context, stage, extra)
            Napier.i(format(snapshot), tag = TAG)
        }.onFailure {
            RateLimitedWarnLogger.warn(
                logTag = TAG,
                key = "capture:$stage",
                message = "failed to capture health snapshot",
                throwable = it
            )
        }
    }

    internal fun capture(context: Context, stage: String, extra: String? = null): Snapshot {
        val processName = runCatching { Application.getProcessName() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: context.applicationInfo?.processName
            ?: context.packageName
        val pushEnabled = runCatching { PushControllerUtils.isPrefsEnable(context) }.getOrDefault(false)
        val regIdPresent = runCatching { MiPushClient.getRegId(context).isNotBlank() }.getOrDefault(false)
        val debugMode = runCatching { runBlocking { Global.ConfigCenter().isDebugModeAsync() } }.getOrDefault(false)
        val lifecycle = XMPushServiceLifecycleBridge.snapshot()
        return Snapshot(
            stage = stage,
            processName = processName,
            pushEnabled = pushEnabled,
            regIdPresent = regIdPresent,
            debugMode = debugMode,
            lifecycleReady = lifecycle.serviceReady,
            lifecyclePendingCount = lifecycle.pendingStartCount,
            extra = extra
        )
    }

    internal fun format(snapshot: Snapshot): String {
        val extraInfo = snapshot.extra?.takeIf { it.isNotBlank() }?.let { " extra=$it" }.orEmpty()
        return "stage=${snapshot.stage}" +
            " process=${snapshot.processName}" +
            " pushEnabled=${snapshot.pushEnabled}" +
            " regIdPresent=${snapshot.regIdPresent}" +
            " debugMode=${snapshot.debugMode}" +
            " lifecycleReady=${snapshot.lifecycleReady}" +
            " lifecyclePending=${snapshot.lifecyclePendingCount}" +
            extraInfo
    }
}
