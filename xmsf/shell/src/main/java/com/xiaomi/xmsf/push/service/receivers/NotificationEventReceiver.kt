package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.diagnostics.PushHealthSnapshotLogger
import io.github.magisk317.mipush.runtime.PushRuntime

/**
 * Stock 7.4.67-C accepts only non-empty `type` and `data` fields, then forwards them to OneTrack.
 * Product telemetry is intentionally not re-enabled here; the same validated event is retained in
 * local diagnostics so restoring the stock component does not create an externally injectable
 * arbitrary-event path.
 */
class NotificationEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val type = intent?.getStringExtra(EXTRA_TYPE)?.takeIf(String::isNotBlank) ?: return
        val data = intent.getStringExtra(EXTRA_DATA)?.takeIf(String::isNotBlank) ?: return
        PushRuntime.observeNotificationEvent(
            packageName = SYSTEM_UI_PACKAGE,
            action = type,
            source = "NotificationEventReceiver.onReceive",
        )
        PushHealthSnapshotLogger.log(
            context,
            "NotificationEventReceiver.onReceive",
            "type=$type dataLength=${data.length}",
        )
    }

    private companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_DATA = "data"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
