package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.diagnostics.PushHealthSnapshotLogger
import io.github.magisk317.mipush.runtime.PushRuntime

class NotificationEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
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
    }
}
