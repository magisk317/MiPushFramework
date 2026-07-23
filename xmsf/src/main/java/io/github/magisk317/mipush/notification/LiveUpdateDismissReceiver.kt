package io.github.magisk317.mipush.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier

/**
 * Ensures native Live Update / promoted-ongoing notifications are fully cancelled when the user
 * dismisses them from the shade (or island dismiss paths that fire deleteIntent).
 *
 * HyperOS may keep the island chip while the shade row is gone if cancel is not synchronized across
 * target-package and local xmsf identities.
 */
class LiveUpdateDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_LIVE_UPDATE_DISMISSED) return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (notificationId == Int.MIN_VALUE) return
        val tag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG)
        Napier.d(
            "live-update dismiss cancel pkg=$packageName id=$notificationId tag=$tag",
            tag = TAG,
        )
        // Cancel both target identity and local fallback (tag/id as posted by xmsf).
        runCatching {
            NotificationManagerEx.cancel(packageName, tag, notificationId)
        }.onFailure {
            Napier.w("live-update target cancel failed: ${it.message}", it, tag = TAG)
        }
        runCatching {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            nm?.cancel(tag, notificationId)
        }.onFailure {
            Napier.w("live-update local cancel failed: ${it.message}", it, tag = TAG)
        }
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
