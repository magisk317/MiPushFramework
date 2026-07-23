package io.github.magisk317.mipush.hook.util

import android.app.Notification
import android.content.Context
import io.github.magisk317.mipush.common.notification.NotificationContentSupport
import io.github.magisk317.xposed.newInstance

fun Notification.newBuilder(context: Context): Notification.Builder {
    return Notification.Builder::class.java.newInstance(context, this) as Notification.Builder
}

fun Notification.hasCustomVisualContent(): Boolean {
    return NotificationContentSupport.hasCustomVisualContent(this)
}

fun Notification.normalizedVisibleTextCandidates(): List<String> {
    return NotificationContentSupport.normalizedVisibleTextCandidates(this)
}

fun Notification.hasMeaningfulVisibleText(): Boolean {
    return NotificationContentSupport.hasMeaningfulVisibleText(this)
}

fun Notification.hasMeaningfulVisibleText(
    context: Context,
    packageName: String,
    channelName: CharSequence? = null,
    channelDescription: String? = null
): Boolean {
    return NotificationContentSupport.hasMeaningfulVisibleText(
        context = context,
        packageName = packageName,
        notification = this,
        channelName = channelName,
        channelDescription = channelDescription,
    )
}

fun Context.getApplicationLabelOrNull(packageName: String): String? {
    return NotificationContentSupport.applicationLabelOrNull(this, packageName)
}

fun CharSequence?.normalizedVisibleText(): String {
    return NotificationContentSupport.normalizedVisibleText(this)
}
