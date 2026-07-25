package io.github.magisk317.mipush.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Ensures native Live Update / promoted-ongoing notifications are fully cancelled when the user
 * dismisses them from the shade (or island dismiss paths that fire deleteIntent).
 *
 * HyperOS may keep the island chip / AOD focus while the shade row is gone if cancel is not
 * synchronized across target-package identity, local xmsf posts, and
 * Settings.Secure updatable_focus_notifs.
 */
class LiveUpdateDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "xmsf",
                "stage" to "live_dismiss",
                "action" to (intent?.action.orEmpty()),
            )
            if (reason != null) attrs["reason"] = reason
            MagiskOtel.event(name = "push.event", attributes = attrs, statusOk = statusOk)
        }
        if (intent?.action != ACTION_LIVE_UPDATE_DISMISSED) {
            emit(result = "skip", reason = "action_mismatch")
            return
        }
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (packageName == null) {
            emit(result = "skip", reason = "missing_package")
            return
        }
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (notificationId == Int.MIN_VALUE) {
            emit(result = "skip", reason = "missing_id")
            return
        }
        val tag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG)
        Napier.d(
            "live-update dismiss cancel pkg=$packageName id=$notificationId tag=$tag",
            tag = TAG,
        )
        FocusNotificationLifecycle.end(
            context = context,
            packageName = packageName,
            notificationId = notificationId,
            tag = tag,
            cancelNotification = true,
            recordDeleted = true,
            unregisterFocus = true,
        )
        MagiskOtel.event(
            name = "push.event",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                "process" to "xmsf",
                "stage" to "live_dismiss",
                "target_package" to packageName,
            ),
            statusOk = true,
        )
    }

    companion object {
        private const val TAG = "LiveUpdateDismiss"
        const val ACTION_LIVE_UPDATE_DISMISSED =
            "io.github.magisk317.mipush.action.LIVE_UPDATE_DISMISSED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_NOTIFICATION_TAG = "notification_tag"
    }
}
