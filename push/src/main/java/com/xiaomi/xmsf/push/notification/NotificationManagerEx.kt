package com.nihility.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.Context
import android.service.notification.StatusBarNotification

object NotificationManagerEx {
    var isHooked: Boolean
        get() = com.magisk317.notification.NotificationManagerEx.isHooked
        set(value) {
            com.magisk317.notification.NotificationManagerEx.isHooked = value
        }

    @JvmStatic
    fun init(context: Context) = com.magisk317.notification.NotificationManagerEx.init(context)

    fun notify(packageName: String, tag: String?, id: Int, notification: Notification) =
        com.magisk317.notification.NotificationManagerEx.notify(packageName, tag, id, notification)

    fun cancel(packageName: String, tag: String?, id: Int) =
        com.magisk317.notification.NotificationManagerEx.cancel(packageName, tag, id)

    fun createNotificationChannels(packageName: String, channels: List<NotificationChannel?>) =
        com.magisk317.notification.NotificationManagerEx.createNotificationChannels(packageName, channels)

    fun getNotificationChannel(packageName: String, channelId: String?): NotificationChannel? =
        com.magisk317.notification.NotificationManagerEx.getNotificationChannel(packageName, channelId)

    fun getNotificationChannels(packageName: String): List<NotificationChannel?>? =
        com.magisk317.notification.NotificationManagerEx.getNotificationChannels(packageName)

    fun deleteNotificationChannel(packageName: String, channelId: String?) =
        com.magisk317.notification.NotificationManagerEx.deleteNotificationChannel(packageName, channelId)

    fun createNotificationChannelGroups(packageName: String, groups: List<NotificationChannelGroup?>) =
        com.magisk317.notification.NotificationManagerEx.createNotificationChannelGroups(packageName, groups)

    fun getNotificationChannelGroup(packageName: String, groupId: String?): NotificationChannelGroup? =
        com.magisk317.notification.NotificationManagerEx.getNotificationChannelGroup(packageName, groupId)

    fun getNotificationChannelGroups(packageName: String): List<NotificationChannelGroup?>? =
        com.magisk317.notification.NotificationManagerEx.getNotificationChannelGroups(packageName)

    fun deleteNotificationChannelGroup(packageName: String, groupId: String?) =
        com.magisk317.notification.NotificationManagerEx.deleteNotificationChannelGroup(packageName, groupId)

    fun areNotificationsEnabled(packageName: String): Boolean =
        com.magisk317.notification.NotificationManagerEx.areNotificationsEnabled(packageName)

    fun getActiveNotifications(packageName: String): Array<StatusBarNotification?>? =
        com.magisk317.notification.NotificationManagerEx.getActiveNotifications(packageName)
}
