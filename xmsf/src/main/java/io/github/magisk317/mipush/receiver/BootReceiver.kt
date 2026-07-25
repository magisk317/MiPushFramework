package io.github.magisk317.mipush.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaomi.push.service.ClientEventDispatcher
import io.github.magisk317.mipush.app.XSpaceXmsfInstallKeeper
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Created by Trumeet on 2017/8/25.
 * @author Trumeet
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "main",
                "action" to action,
            )
            if (reason != null) attrs["reason"] = reason
            MagiskOtel.event(name = "push.boot", attributes = attrs, statusOk = statusOk)
        }
        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                PushRuntime.observeChannelEvent(
                    packageName = context.packageName,
                    action = action,
                    source = "BootReceiver.onReceive",
                )
                runCatching {
                    XSpaceXmsfInstallKeeper.scheduleForced(context, "BootReceiver.BOOT_COMPLETED")
                    ClientEventDispatcher().notifyServiceStarted(
                        context,
                        io.github.magisk317.mipush.bridge.MiPushRuntimeObserverBridge(context),
                    )
                    PushRuntime.handleBootCompleted("BootReceiver.onReceive")
                }.fold(
                    onSuccess = { emit(result = "ok", reason = "boot_completed") },
                    onFailure = { error ->
                        emit(
                            result = "error",
                            statusOk = false,
                            reason = error.javaClass.simpleName,
                        )
                    },
                )
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // adb install -r reinstalls into every user that already has the package (incl.
                // XSpace 999). Force dual-app package sync so a disabled toggle cannot leave a
                // zombie dual-space push service (and its status-bar FGS) after updates.
                runCatching {
                    XSpaceXmsfInstallKeeper.scheduleForced(context, "BootReceiver.MY_PACKAGE_REPLACED")
                }.fold(
                    onSuccess = { emit(result = "ok", reason = "package_replaced") },
                    onFailure = { error ->
                        emit(
                            result = "error",
                            statusOk = false,
                            reason = error.javaClass.simpleName,
                        )
                    },
                )
            }
            else -> emit(result = "skip", reason = "unhandled_action")
        }
    }
}
