package io.github.magisk317.mipush.notification

import android.content.Context
import co.touchlab.kermit.Logger
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
        userId: Int = io.github.magisk317.mipush.common.utils.Utils.myUserId(),
    ) {
        val resolvedTag = tag
        if (cancelNotification) {
            runCatching {
                NotificationManagerEx.cancel(packageName, resolvedTag, notificationId, userId)
            }.onFailure {
                Logger.withTag(TAG).w(it) { "focus end target cancel failed pkg=$packageName id=$notificationId: ${it.message}" }
            }
        }
    }
}
