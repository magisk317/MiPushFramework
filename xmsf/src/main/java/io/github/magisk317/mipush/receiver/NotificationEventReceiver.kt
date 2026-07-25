package io.github.magisk317.mipush.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.diagnostics.PushHealthSnapshotLogger
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.xposed.logging.MagiskOtel

class NotificationEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val startedAt = System.nanoTime()
        val action = intent?.action ?: "unknown"
        val packageName = intent?.getStringExtra("target_package")
            ?: intent?.getStringExtra("package_name")
            ?: intent?.getStringExtra("pkg")
            ?: intent?.getStringExtra("source_package")
            ?: intent?.`package`
        PushRuntime.observeNotificationEvent(
            packageName = packageName,
            action = action,
            source = "NotificationEventReceiver.onReceive"
        )
        PushHealthSnapshotLogger.log(
            context,
            "NotificationEventReceiver.onReceive",
            "action=$action pkg=${packageName ?: "unknown"}"
        )
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        val attrs = mutableMapOf(
            "result" to "ok",
            "duration_ms" to durationMs.toString(),
            "process" to "main",
            "action" to action,
        )
        if (!packageName.isNullOrBlank()) {
            attrs["target_package"] = packageName
        }
        MagiskOtel.event(name = "push.event", attributes = attrs, statusOk = true)
    }
}
