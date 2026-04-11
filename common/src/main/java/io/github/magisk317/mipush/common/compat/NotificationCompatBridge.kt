package io.github.magisk317.mipush.common.compat

import android.app.Notification
import android.content.Context

object NotificationCompatBridge {
    fun buildSilencedRestoredNotification(context: Context, notification: Notification): Notification {
        val behavior = if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            Notification.GROUP_ALERT_CHILDREN
        } else {
            Notification.GROUP_ALERT_SUMMARY
        }
        return Notification.Builder
            .recoverBuilder(context, notification)
            .setGroupAlertBehavior(behavior)
            .build()
    }
}
