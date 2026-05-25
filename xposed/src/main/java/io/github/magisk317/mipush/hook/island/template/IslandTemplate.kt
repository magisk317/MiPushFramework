package io.github.magisk317.mipush.hook.island.template

import android.app.Notification
import android.content.Context

interface IslandTemplate {
    fun inject(context: Context, notification: Notification, data: NotifData)
}
