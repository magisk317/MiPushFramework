package com.magisk317.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import com.elvishew.xlog.XLog

object NotificationManagerEx {
    private const val TAG = "NotificationManagerEx"

    private lateinit var notificationManager: NotificationManager

    @JvmField
    var isHooked: Boolean = false

    @JvmStatic
    fun init(context: Context) {
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun notify(
        packageName: String,
        tag: String?, id: Int, notification: Notification
    ) {
        XLog.d(TAG, "notify() called with: packageName = $packageName, tag = $tag, id = $id, notification = $notification")
        try {
            val method = NotificationManager::class.java.getMethod(
                "notifyAsPackage",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Notification::class.java
            )
            method.invoke(notificationManager, packageName, tag, id, notification)
            return
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to invoke notifyAsPackage", e)
        }
        notificationManager.notify(tag, id, notification)
    }

    fun cancel(
        packageName: String,
        tag: String?, id: Int
    ) {
        XLog.d(TAG, "cancel() called with: packageName = $packageName, tag = $tag, id = $id")
        try {
            val method = NotificationManager::class.java.getMethod(
                "cancelAsPackage",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            method.invoke(notificationManager, packageName, tag, id)
            return
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to invoke cancelAsPackage", e)
        }
        notificationManager.cancel(tag, id)
    }

    fun createNotificationChannels(
        packageName: String,
        channels: List<NotificationChannel?>
    ) {
        XLog.d(TAG, "createNotificationChannels() called with: packageName = $packageName, channels = $channels")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val method = NotificationManager::class.java.getMethod(
                    "createNotificationChannelsForPackage",
                    String::class.java,
                    List::class.java
                )
                method.invoke(notificationManager, packageName, channels)
                return
            } catch (e: Exception) {
                 XLog.e(TAG, "Failed to invoke createNotificationChannelsForPackage", e)
            }
            notificationManager.createNotificationChannels(channels)
        }
    }

    fun getNotificationChannel(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        XLog.d(TAG, "getNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val method = NotificationManager::class.java.getMethod(
                    "getNotificationChannelForPackage",
                    String::class.java,
                    String::class.java
                )
                return method.invoke(notificationManager, packageName, channelId) as? NotificationChannel
            } catch (e: Exception) {
                XLog.e(TAG, "Failed to invoke getNotificationChannelForPackage", e)
            }
            return notificationManager.getNotificationChannel(channelId)
        } else {
            return null
        }
    }

    fun getNotificationChannels(
        packageName: String
    ): List<NotificationChannel?>? {
        XLog.d(TAG, "getNotificationChannels() called with: packageName = $packageName")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.getNotificationChannels()
        } else {
            emptyList()
        }
    }

    fun deleteNotificationChannel(
        packageName: String,
        channelId: String?
    ) {
        XLog.d(TAG, "deleteNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(channelId)
        }
    }


    fun createNotificationChannelGroups(
        packageName: String,
        groups: List<NotificationChannelGroup?>
    ) {
        XLog.d(TAG, "createNotificationChannelGroups() called with: packageName = $packageName, groups = $groups")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannelGroups(groups)
        }
    }

    fun getNotificationChannelGroup(
        packageName: String,
        groupId: String?
    ): NotificationChannelGroup? {
        XLog.d(TAG, "getNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val method = NotificationManager::class.java.getMethod(
                    "getNotificationChannelGroupForPackage",
                    String::class.java,
                    String::class.java
                )
                return method.invoke(notificationManager, packageName, groupId) as? NotificationChannelGroup
            } catch (e: Exception) {
                 XLog.e(TAG, "Failed to invoke getNotificationChannelGroupForPackage", e)
            }
            return notificationManager.getNotificationChannelGroup(groupId)
        } else {
            return null
        }
    }

    fun getNotificationChannelGroups(
        packageName: String
    ): List<NotificationChannelGroup?>? {
        XLog.d(TAG, "getNotificationChannelGroups() called with: packageName = $packageName")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.getNotificationChannelGroups()
        } else {
            emptyList()
        }
    }

    fun deleteNotificationChannelGroup(
        packageName: String,
        groupId: String?
    ) {
        XLog.d(TAG, "deleteNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannelGroup(groupId)
        }
    }

    fun areNotificationsEnabled(
        packageName: String
    ): Boolean {
        XLog.d(TAG, "areNotificationsEnabled() called with: packageName = $packageName")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }
    }

    fun getActiveNotifications(
        packageName: String
    ): Array<StatusBarNotification?>? {
        XLog.d(TAG, "getActiveNotifications() called with: packageName = $packageName")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.getActiveNotifications()
        } else {
            emptyArray()
        }
    }

}
