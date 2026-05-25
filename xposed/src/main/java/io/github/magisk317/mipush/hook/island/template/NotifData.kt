package io.github.magisk317.mipush.hook.island.template

import android.app.Notification
import android.app.PendingIntent
import android.graphics.drawable.Icon

data class NotifData(
    val packageName: String,
    val notificationId: Int,
    val channelId: String?,
    val title: String,
    val content: String,
    val icon: Icon?,
    val contentIntent: PendingIntent?,
    val isOngoing: Boolean,
    val actions: List<Notification.Action>,
)
