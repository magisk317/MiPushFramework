package io.github.magisk317.mipush.hook.island.template

import android.app.Notification
import android.graphics.drawable.Icon

data class IslandViewModel(
    val title: String,
    val content: String,
    val icon: Icon?,
    val actions: List<Notification.Action>,
    val timeoutSecs: Int = 5,
    val firstFloat: Boolean = true,
    val enableFloat: Boolean = true,
    val showNotification: Boolean = true,
)
