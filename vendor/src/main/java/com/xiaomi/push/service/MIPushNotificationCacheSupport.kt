package com.xiaomi.push.service

import android.app.Notification
import android.content.Context
import android.text.TextUtils

object MIPushNotificationCacheSupport {
    @JvmStatic
    fun clearNotification(
        context: Context,
        packageName: String,
    ) {
        clearNotification(context, packageName, -1)
    }

    @JvmStatic
    fun clearNotification(
        context: Context,
        packageName: String,
        notificationId: Int,
    ) {
        if (notificationId < MIPushNotificationHelper.NOTIFY_ALL) {
            return
        }
        val notificationManager = NotificationManagerHelper.from(context, packageName)
        val hashedNotificationId = (packageName.hashCode() / 10) * 10 + notificationId
        val clearAll = notificationId == MIPushNotificationHelper.NOTIFY_ALL
        var clearedCount = 0
        // Stock 7.4.67-C t0.b moved clear-by-id/all from the pinned 3.7.9 100-entry
        // process cache to g1.g active notifications. The old cache lost ownership state
        // after an XMSF restart and could not clear older system records; scanning the
        // managed active set preserves stock message_id/opPkg/channel ownership checks.
        for (statusBarNotification in notificationManager.getManagedActiveNotifications()) {
            val activeId = statusBarNotification.id
            if (clearAll || activeId == hashedNotificationId) {
                notificationManager.cancel(activeId)
                clearedCount += 1
                if (!clearAll) {
                    break
                }
            }
        }
        uploadClearMessageData(context, clearedCount)
    }

    @JvmStatic
    fun clearNotification(
        context: Context,
        packageName: String,
        titleFilter: String,
        descriptionFilter: String,
    ) {
        // Stock 7.4.67-C t0.c tightened the old 3.7.9 API: both filters must be present.
        if (TextUtils.isEmpty(titleFilter) || TextUtils.isEmpty(descriptionFilter)) {
            return
        }
        val notificationManager = NotificationManagerHelper.from(context, packageName)
        var clearedCount = 0
        // Unlike t0.b, stock 7.4.67-C t0.c intentionally scans the target-scoped g1.h
        // list rather than only MiPush-managed records. DEX confirms that each request
        // filter contains the rendered notification text; pinned 3.7.9 checked the
        // reverse direction and could therefore clear a different set of notifications.
        for (statusBarNotification in notificationManager.getActiveNotifications().orEmpty()) {
            val notification = statusBarNotification.notification ?: continue
            val title = getTitle(notification)
            val description = getDescription(notification)
            if (title.isNotEmpty() &&
                description.isNotEmpty() &&
                titleFilter.contains(title) &&
                descriptionFilter.contains(description)
            ) {
                notificationManager.cancel(statusBarNotification.id)
                clearedCount += 1
            }
        }
        uploadClearMessageData(context, clearedCount)
    }

    @JvmStatic
    fun uploadClearMessageData(context: Context, clearedCount: Int) {
        if (clearedCount <= 0) {
            return
        }
        TinyDataHelper.cacheTinyData(
            context,
            PushConstants.DOT_CATEGORY_CLEAR_NOTIFICATION,
            PushConstants.CLEAR_NOTIFICATION,
            clearedCount.toLong(),
            "",
        )
    }

    @JvmStatic
    fun uploadClearMessageData(context: Context, notifications: Collection<*>) {
        // Keep the pinned 3.7.9 collection-shaped entry point for hybrid SDK callers, but
        // funnel it into the stock 7.4.67-C count contract used by active-notification clear.
        uploadClearMessageData(context, notifications.size)
    }

    // Stock 7.4.67-C h1.r/p reads these keys in this exact fallback order. Keeping the
    // custom RemoteViews keys matters because such notifications may not populate the
    // standard framework title/text extras used by the old local implementation.
    private fun getTitle(notification: Notification): String {
        val extras = notification.extras ?: return ""
        return sequenceOf(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            extras.getCharSequence("mipush.customTitle"),
        ).firstOrNull { !it.isNullOrEmpty() }?.toString().orEmpty()
    }

    private fun getDescription(notification: Notification): String {
        val extras = notification.extras ?: return ""
        return sequenceOf(
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence("mipush.customContent"),
        ).firstOrNull { !it.isNullOrEmpty() }?.toString().orEmpty()
    }
}
