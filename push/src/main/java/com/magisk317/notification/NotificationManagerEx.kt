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

    private lateinit var appContext: Context
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
        appContext = context.applicationContext
        notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun getNotificationManagerForPackage(packageName: String): NotificationManager? {
        if (!::appContext.isInitialized) {
            return null
        }
        if (packageName == appContext.packageName) {
            return notificationManager
        }
        return try {
            val packageContext = appContext.createPackageContext(packageName, 0)
            packageContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        } catch (e: Exception) {
            logger.e("Failed to create package NotificationManager for $packageName", e)
            null
        }
    }

    private fun getDirectPackageNotificationChannel(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        if (channelId.isNullOrEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null
        }
        if (!canUsePackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            return if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.getNotificationChannel(channelId)
                } catch (e: Exception) {
                    logger.e("Failed to query package channel via package context for $packageName/$channelId", e)
                    null
                }
            } else {
                null
            }
        }
        return try {
            val method = NotificationManager::class.java.getMethod(
                "getNotificationChannelForPackage",
                String::class.java,
                String::class.java
            )
            method.invoke(notificationManager, packageName, channelId) as? NotificationChannel
        } catch (e: Exception) {
            logger.e("Failed to invoke getNotificationChannelForPackage", e)
            null
        }
    }

    private fun getDirectPackageNotificationChannelGroup(
        packageName: String,
        groupId: String?
    ): NotificationChannelGroup? {
        if (groupId.isNullOrEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return null
        }
        if (!canUsePackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            return if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.getNotificationChannelGroup(groupId)
                } catch (e: Exception) {
                    logger.e("Failed to query package group via package context for $packageName/$groupId", e)
                    null
                }
            } else {
                null
            }
        }
        return try {
            val method = NotificationManager::class.java.getMethod(
                "getNotificationChannelGroupForPackage",
                String::class.java,
                String::class.java
            )
            method.invoke(notificationManager, packageName, groupId) as? NotificationChannelGroup
        } catch (e: Exception) {
            logger.e("Failed to invoke getNotificationChannelGroupForPackage", e)
            null
        }
    }

    private fun shouldNotifyAsPackage(
        packageName: String,
        notification: Notification
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true
        }
        val channelId = notification.channelId
        val packageChannelVisible = getDirectPackageNotificationChannel(packageName, channelId) != null
        logger.d(
            "shouldNotifyAsPackage() packageName=$packageName channelId=$channelId " +
                "visible=$packageChannelVisible sdk=${Build.VERSION.SDK_INT}"
        )
        return packageChannelVisible
    }

    fun notify(
        packageName: String,
        tag: String?, id: Int, notification: Notification
    ) {
        logger.d("notify() called with: packageName = $packageName, tag = $tag, id = $id, notification = $notification")
        if (shouldNotifyAsPackage(packageName, notification)) {
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
        if (canUsePackageScopedApis() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                val method = NotificationManager::class.java.getMethod(
                    "cancelAsPackage",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                method.invoke(notificationManager, packageName, tag, id)
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
            val nonNullChannels = channels.filterNotNull()
            if (!canUsePackageScopedApis()) {
                val packageNotificationManager = getNotificationManagerForPackage(packageName)
                if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                    try {
                        packageNotificationManager.createNotificationChannels(nonNullChannels)
                        return
                    } catch (e: Exception) {
                        logger.e("Failed to create notification channels via package context for $packageName", e)
                    }
                }
            }
            if (canUsePackageScopedApis()) {
                try {
                    val method = NotificationManager::class.java.getMethod(
                        "createNotificationChannelsForPackage",
                        String::class.java,
                        List::class.java
                    )
                    method.invoke(notificationManager, packageName, nonNullChannels)
                    return
                } catch (e: Exception) {
                    logger.e("Failed to invoke createNotificationChannelsForPackage", e)
                }
            }
            notificationManager.createNotificationChannels(nonNullChannels)
        }
    }

    fun getNotificationChannel(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        logger.d("getNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val directChannel = getDirectPackageNotificationChannel(packageName, channelId)
            if (directChannel != null) {
                return directChannel
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
            if (!canUsePackageScopedApis()) {
                val packageNotificationManager = getNotificationManagerForPackage(packageName)
                if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                    try {
                        return packageNotificationManager.notificationChannels
                    } catch (e: Exception) {
                        logger.e("Failed to query channels via package context for $packageName", e)
                    }
                }
            }
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
            if (!canUsePackageScopedApis()) {
                val packageNotificationManager = getNotificationManagerForPackage(packageName)
                if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                    try {
                        packageNotificationManager.deleteNotificationChannel(channelId)
                        return
                    } catch (e: Exception) {
                        logger.e("Failed to delete channel via package context for $packageName/$channelId", e)
                    }
                }
            }
            notificationManager.deleteNotificationChannel(channelId)
        }
    }


    fun createNotificationChannelGroups(
        packageName: String,
        groups: List<NotificationChannelGroup?>
    ) {
        logger.d("createNotificationChannelGroups() called with: packageName = $packageName, groups = $groups")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nonNullGroups = groups.filterNotNull()
            if (!canUsePackageScopedApis()) {
                val packageNotificationManager = getNotificationManagerForPackage(packageName)
                if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                    try {
                        packageNotificationManager.createNotificationChannelGroups(nonNullGroups)
                        return
                    } catch (e: Exception) {
                        logger.e("Failed to create groups via package context for $packageName", e)
                    }
                }
            }
            notificationManager.createNotificationChannelGroups(nonNullGroups)
        }
    }

    fun getNotificationChannelGroup(
        packageName: String,
        groupId: String?
    ): NotificationChannelGroup? {
        logger.d("getNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val directGroup = getDirectPackageNotificationChannelGroup(packageName, groupId)
            if (directGroup != null) {
                return directGroup
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
            if (!canUsePackageScopedApis()) {
                val packageNotificationManager = getNotificationManagerForPackage(packageName)
                if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                    try {
                        return packageNotificationManager.notificationChannelGroups
                    } catch (e: Exception) {
                        logger.e("Failed to query groups via package context for $packageName", e)
                    }
                }
            }
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
            if (!canUsePackageScopedApis()) {
                val packageNotificationManager = getNotificationManagerForPackage(packageName)
                if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                    try {
                        packageNotificationManager.deleteNotificationChannelGroup(groupId)
                        return
                    } catch (e: Exception) {
                        logger.e("Failed to delete group via package context for $packageName/$groupId", e)
                    }
                }
            }
            notificationManager.deleteNotificationChannelGroup(groupId)
        }
    }

    fun areNotificationsEnabled(
        packageName: String
    ): Boolean {
        logger.d("areNotificationsEnabled() called with: packageName = $packageName")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!canUsePackageScopedApis()) {
                val packageNotificationManager = getNotificationManagerForPackage(packageName)
                if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                    try {
                        return packageNotificationManager.areNotificationsEnabled()
                    } catch (e: Exception) {
                        logger.e("Failed to query notifications enabled via package context for $packageName", e)
                    }
                }
            }
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
            if (!canUsePackageScopedApis()) {
                val packageNotificationManager = getNotificationManagerForPackage(packageName)
                if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                    try {
                        return packageNotificationManager.activeNotifications
                    } catch (e: Exception) {
                        logger.e("Failed to query active notifications via package context for $packageName", e)
                    }
                }
            }
            notificationManager.getActiveNotifications()
        } else {
            emptyArray()
        }
    }

}
