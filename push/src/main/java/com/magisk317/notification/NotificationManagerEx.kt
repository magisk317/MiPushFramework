package com.magisk317.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog

object NotificationManagerEx {
    private const val TAG = "NotificationManagerEx"
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = TAG)
        fun e(msg: String, t: Throwable? = null) = Napier.e(msg, t, tag = TAG)
    }

    private lateinit var notificationManager: NotificationManager

    @JvmField
    var isHooked: Boolean = false

    /**
     * Android 15+ tightens package/channel ownership checks.
     * If channels are created in XMSF process, posting as target package can be rejected with:
     * "No Channel found for pkg=<target>".
     * Prefer local NotificationManager APIs on these versions for stable delivery.
     */
    private fun canUsePackageScopedApis(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
    }

    @JvmStatic
    fun init(context: Context) {
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun notify(
        packageName: String,
        tag: String?, id: Int, notification: Notification
    ) {
        logger.d("notify() called with: packageName = $packageName, tag = $tag, id = $id, notification = $notification")
        if (canUsePackageScopedApis()) {
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
                logger.e("Failed to invoke notifyAsPackage", e)
            }
        }
        notificationManager.notify(tag, id, notification)
    }

    fun cancel(
        packageName: String,
        tag: String?, id: Int
    ) {
        logger.d("cancel() called with: packageName = $packageName, tag = $tag, id = $id")
        if (canUsePackageScopedApis()) {
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
                logger.e("Failed to invoke cancelAsPackage", e)
            }
        }
        notificationManager.cancel(tag, id)
    }

    fun createNotificationChannels(
        packageName: String,
        channels: List<NotificationChannel?>
    ) {
        logger.d("createNotificationChannels() called with: packageName = $packageName, channels = $channels")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (canUsePackageScopedApis()) {
                try {
                    val method = NotificationManager::class.java.getMethod(
                        "createNotificationChannelsForPackage",
                        String::class.java,
                        List::class.java
                    )
                    method.invoke(notificationManager, packageName, channels)
                    return
                } catch (e: Exception) {
                    logger.e("Failed to invoke createNotificationChannelsForPackage", e)
                }
            }
            notificationManager.createNotificationChannels(channels)
        }
    }

    fun getNotificationChannel(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        logger.d("getNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (canUsePackageScopedApis()) {
                try {
                    val method = NotificationManager::class.java.getMethod(
                        "getNotificationChannelForPackage",
                        String::class.java,
                        String::class.java
                    )
                    return method.invoke(notificationManager, packageName, channelId) as? NotificationChannel
                } catch (e: Exception) {
                    logger.e("Failed to invoke getNotificationChannelForPackage", e)
                }
            }
            return notificationManager.getNotificationChannel(channelId)
        } else {
            return null
        }
    }

    fun getNotificationChannels(
        packageName: String
    ): List<NotificationChannel?>? {
        logger.d("getNotificationChannels() called with: packageName = $packageName")
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
        logger.d("deleteNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(channelId)
        }
    }


    fun createNotificationChannelGroups(
        packageName: String,
        groups: List<NotificationChannelGroup?>
    ) {
        logger.d("createNotificationChannelGroups() called with: packageName = $packageName, groups = $groups")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannelGroups(groups)
        }
    }

    fun getNotificationChannelGroup(
        packageName: String,
        groupId: String?
    ): NotificationChannelGroup? {
        logger.d("getNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (canUsePackageScopedApis()) {
                try {
                    val method = NotificationManager::class.java.getMethod(
                        "getNotificationChannelGroupForPackage",
                        String::class.java,
                        String::class.java
                    )
                    return method.invoke(notificationManager, packageName, groupId) as? NotificationChannelGroup
                } catch (e: Exception) {
                    logger.e("Failed to invoke getNotificationChannelGroupForPackage", e)
                }
            }
            return notificationManager.getNotificationChannelGroup(groupId)
        } else {
            return null
        }
    }

    fun getNotificationChannelGroups(
        packageName: String
    ): List<NotificationChannelGroup?>? {
        logger.d("getNotificationChannelGroups() called with: packageName = $packageName")
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
        logger.d("deleteNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannelGroup(groupId)
        }
    }

    fun areNotificationsEnabled(
        packageName: String
    ): Boolean {
        logger.d("areNotificationsEnabled() called with: packageName = $packageName")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }
    }

    fun getActiveNotifications(
        packageName: String
    ): Array<StatusBarNotification?>? {
        logger.d("getActiveNotifications() called with: packageName = $packageName")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.getActiveNotifications()
        } else {
            emptyArray()
        }
    }

}
