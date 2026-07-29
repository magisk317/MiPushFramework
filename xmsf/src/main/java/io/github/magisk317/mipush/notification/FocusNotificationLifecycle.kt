package io.github.magisk317.mipush.notification

import android.content.Context
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper

/**
 * Single programmatic cancellation path for MIUI focus and native Live Update notifications.
 * SystemUI owns its focus snapshot; this class only removes the real notification identity.
 *
 * Stock 7.4.67-C `com.xiaomi.push.sort.c/d` learns user removals from
 * `NotificationListenerService`, not from notification `deleteIntent` callbacks. The old
 * `recordDeleted` branch was removed because it mixed cancellation with collection state and
 * could suppress a later user-visible push.
 */
object FocusNotificationLifecycle {
    private const val TAG = "FocusNotificationLifecycle"

    @JvmStatic
    fun end(
        context: Context,
        packageName: String,
        notificationId: Int,
        tag: String? = MyMIPushNotificationHelper.getNotificationTag(packageName),
        cancelNotification: Boolean = true,
    ) {
        val appContext = context.applicationContext ?: context
        val resolvedTag = tag
        if (cancelNotification) {
            runCatching {
                NotificationManagerEx.cancel(packageName, resolvedTag, notificationId)
            }.onFailure {
                Napier.w("focus end target cancel failed pkg=$packageName id=$notificationId: ${it.message}", it, tag = TAG)
            }
            runCatching {
                appContext.getSystemService(android.app.NotificationManager::class.java)
                    ?.cancel(resolvedTag, notificationId)
            }.onFailure {
                Napier.w("focus end local cancel failed pkg=$packageName id=$notificationId: ${it.message}", it, tag = TAG)
            }
        }
    }
}
