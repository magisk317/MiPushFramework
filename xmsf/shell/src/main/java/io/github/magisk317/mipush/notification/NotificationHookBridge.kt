package io.github.magisk317.mipush.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.Context
import android.service.notification.StatusBarNotification

/**
 * Target-side entrypoint for the Xposed adaptation plane.
 *
 * The implementation and all privileged notification decisions remain owned by XMSF. Xposed
 * resolves this class through the com.xiaomi.xmsf classloader and only dispatches hook callbacks.
 */
object NotificationHookBridge {
    const val HOOK_API_VERSION = 1

    @JvmStatic
    fun init(context: Context) {
        NotificationHookBackend.init(context)
    }

    @JvmStatic
    fun notify(packageName: String, tag: String?, id: Int, notification: Notification): Boolean =
        NotificationHookBackend.notify(packageName, tag, id, notification)

    @JvmStatic
    fun cancel(packageName: String, tag: String?, id: Int): Boolean {
        NotificationHookBackend.cancel(packageName, tag, id)
        return true
    }

    @JvmStatic
    fun createNotificationChannels(packageName: String, channels: List<NotificationChannel>): Boolean =
        NotificationHookBackend.createNotificationChannels(packageName, channels)

    @JvmStatic
    fun getNotificationChannel(packageName: String, channelId: String?): NotificationChannel? =
        NotificationHookBackend.getNotificationChannel(packageName, channelId)

    @JvmStatic
    fun getNotificationChannels(packageName: String): List<NotificationChannel?>? =
        NotificationHookBackend.getNotificationChannels(packageName)

    @JvmStatic
    fun deleteNotificationChannel(packageName: String, channelId: String?) {
        if (!channelId.isNullOrEmpty()) NotificationHookBackend.deleteNotificationChannel(packageName, channelId)
    }

    @JvmStatic
    fun createNotificationChannelGroups(packageName: String, groups: List<NotificationChannelGroup>): Boolean {
        NotificationHookBackend.createNotificationChannelGroups(packageName, groups)
        return true
    }

    @JvmStatic
    fun getNotificationChannelGroup(packageName: String, groupId: String?): NotificationChannelGroup? =
        if (groupId.isNullOrEmpty()) null else NotificationHookBackend.getNotificationChannelGroup(packageName, groupId)

    @JvmStatic
    fun getNotificationChannelGroups(packageName: String): List<NotificationChannelGroup?>? =
        NotificationHookBackend.getNotificationChannelGroups(packageName)

    @JvmStatic
    fun deleteNotificationChannelGroup(packageName: String, groupId: String?) {
        if (!groupId.isNullOrEmpty()) NotificationHookBackend.deleteNotificationChannelGroup(packageName, groupId)
    }

    @JvmStatic
    fun areNotificationsEnabled(packageName: String): Boolean =
        NotificationHookBackend.areNotificationsEnabled(packageName)

    @JvmStatic
    fun getActiveNotifications(packageName: String): Array<StatusBarNotification?>? =
        NotificationHookBackend.getActiveNotifications(packageName)

    @JvmStatic
    fun supportsTargetChannelProvisioning(packageName: String): Boolean = true

    @JvmStatic
    fun findPreferredTargetChannel(packageName: String, preferredChannelId: String?): NotificationChannel? =
        NotificationHookBackend.findPreferredTargetChannel(packageName, preferredChannelId)

    @JvmStatic
    fun getTargetNotificationChannels(packageName: String): List<NotificationChannel> =
        NotificationHookBackend.getNotificationChannels(packageName).orEmpty().filterNotNull()

    @JvmStatic
    fun getTargetNotificationChannelGroups(packageName: String): List<NotificationChannelGroup> =
        NotificationHookBackend.getNotificationChannelGroups(packageName).orEmpty().filterNotNull()

    @JvmStatic
    fun getTargetNotificationChannel(packageName: String, channelId: String?): NotificationChannel? =
        NotificationHookBackend.getNotificationChannel(packageName, channelId)

    @JvmStatic
    fun getPreferredTargetNotificationChannel(packageName: String, preferredChannelId: String?): NotificationChannel? =
        NotificationHookBackend.findPreferredTargetChannel(packageName, preferredChannelId)

    @JvmStatic
    fun createTargetNotificationChannelGroups(packageName: String, groups: List<NotificationChannelGroup>): Boolean {
        NotificationHookBackend.createNotificationChannelGroups(packageName, groups)
        return true
    }

    @JvmStatic
    fun createTargetNotificationChannels(packageName: String, channels: List<NotificationChannel>): Boolean =
        NotificationHookBackend.createNotificationChannels(packageName, channels)

    @JvmStatic
    fun notifyAsTargetPackage(
        packageName: String,
        tag: String?,
        id: Int,
        notification: Notification,
    ): Boolean = NotificationHookBackend.notify(packageName, tag, id, notification)

    @JvmStatic
    fun cancelAsTargetPackage(packageName: String, tag: String?, id: Int): Boolean {
        NotificationHookBackend.cancel(packageName, tag, id)
        return true
    }
}
