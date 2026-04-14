package com.xiaomi.push.service

import android.content.Context
import android.text.TextUtils
import android.util.Pair
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import java.util.LinkedList

object MIPushNotificationCacheSupport {
    @JvmStatic
    fun clearNotification(
        context: Context,
        packageName: String,
        notifications: LinkedList<Pair<Int, XmPushActionContainer>>,
    ) {
        clearNotification(context, packageName, -1, notifications)
    }

    @JvmStatic
    fun clearNotification(
        context: Context,
        packageName: String,
        notificationId: Int,
        notifications: LinkedList<Pair<Int, XmPushActionContainer>>,
    ) {
        val notificationManager = NotificationManagerHelper.from(context, packageName)
        val hashedNotificationId = (packageName.hashCode() / 10) * 10 + notificationId
        val matchedNotifications = LinkedList<Pair<Int, XmPushActionContainer>>()
        if (notificationId >= 0) {
            notificationManager.cancel(hashedNotificationId)
        }
        synchronized(notifications) {
            for (pair in notifications) {
                val container = pair.second ?: continue
                val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
                if (notificationId >= 0) {
                    if (hashedNotificationId == pair.first && TextUtils.equals(targetPackage, packageName)) {
                        matchedNotifications.add(pair)
                    }
                } else if (notificationId == -1 && TextUtils.equals(targetPackage, packageName)) {
                    notificationManager.cancel(pair.first)
                    matchedNotifications.add(pair)
                }
            }
            notifications.removeAll(matchedNotifications)
        }
        uploadClearMessageData(context, matchedNotifications)
    }

    @JvmStatic
    fun clearNotification(
        context: Context,
        packageName: String,
        titleFilter: String,
        descriptionFilter: String,
        notifications: LinkedList<Pair<Int, XmPushActionContainer>>,
    ) {
        if (TextUtils.isEmpty(titleFilter) && TextUtils.isEmpty(descriptionFilter)) {
            return
        }
        val matchedNotifications = LinkedList<Pair<Int, XmPushActionContainer>>()
        synchronized(notifications) {
            for (pair in notifications) {
                val container = pair.second ?: continue
                val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
                val metaInfo: PushMetaInfo = container.metaInfo ?: continue
                if (TextUtils.equals(targetPackage, packageName)) {
                    val title = metaInfo.title
                    val description = metaInfo.description
                    if (!TextUtils.isEmpty(title) &&
                        !TextUtils.isEmpty(description) &&
                        checkMatch(titleFilter, title) &&
                        checkMatch(descriptionFilter, description)
                    ) {
                        NotificationManagerHelper.from(context, packageName).cancel(pair.first)
                        matchedNotifications.add(pair)
                    }
                }
            }
            notifications.removeAll(matchedNotifications)
        }
        uploadClearMessageData(context, matchedNotifications)
    }

    @JvmStatic
    fun cacheNotification(
        notification: Pair<Int, XmPushActionContainer>,
        notifications: LinkedList<Pair<Int, XmPushActionContainer>>,
    ) {
        synchronized(notifications) {
            notifications.add(notification)
            if (notifications.size > MIPushNotificationHelper.MAX_NOTIFY_ID_CACHE_SIZE) {
                notifications.remove()
            }
        }
    }

    @JvmStatic
    fun uploadClearMessageData(context: Context, notifications: LinkedList<*>) {
        if (notifications.isNullOrEmpty()) {
            return
        }
        TinyDataHelper.cacheTinyData(
            context,
            PushConstants.DOT_CATEGORY_CLEAR_NOTIFICATION,
            PushConstants.CLEAR_NOTIFICATION,
            notifications.size.toLong(),
            "",
        )
    }

    private fun checkMatch(filter: String, value: String): Boolean {
        return TextUtils.isEmpty(filter) || value.contains(filter)
    }
}
