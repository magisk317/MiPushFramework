package io.github.magisk317.mipush.receiver

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import io.github.aakira.napier.Napier
import com.xiaomi.push.service.PushServiceConstants
import io.github.magisk317.mipush.service.PushServiceStarter
import io.github.magisk317.mipush.runtime.PushRuntimeComponents
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * @author zts
 */
class KeepAliveReceiver : BroadcastReceiver() {
    private val tag = KeepAliveReceiver::class.java.simpleName
    private var lastActive: Long = System.currentTimeMillis()

    override fun onReceive(context: Context, intent: Intent?) {
        val startedAt = System.nanoTime()
        try {
            val now = System.currentTimeMillis()
            if (now - lastActive < 1000 * 60 * 2) {
                MagiskOtel.event(
                    name = "push.keepalive",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to "0",
                        "process" to "main",
                        "reason" to "throttled",
                    ),
                    statusOk = true,
                )
                return
            }

            lastActive = now
            logD("start service when ${intent?.action}")
            val localIntent = PushRuntimeComponents.newCoreServiceIntent(context)
            localIntent.putExtra(PushServiceConstants.EXTRA_TIME_STAMP, now)
            localIntent.action = PushServiceConstants.ACTION_CHECK_ALIVE
            PushServiceStarter.start(context, localIntent)
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            MagiskOtel.event(
                name = "push.keepalive",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to durationMs.toString(),
                    "process" to "main",
                    "reason" to "check_alive",
                    "action" to (intent?.action ?: "unknown"),
                ),
                statusOk = true,
            )
        } catch (localException: Throwable) {
            logE(localException.message ?: "error", localException)
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            MagiskOtel.event(
                name = "push.keepalive",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to durationMs.toString(),
                    "process" to "main",
                    "reason" to localException.javaClass.simpleName,
                ),
                statusOk = false,
            )
        }
    }
}
