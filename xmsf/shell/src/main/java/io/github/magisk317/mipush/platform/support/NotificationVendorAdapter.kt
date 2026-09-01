package io.github.magisk317.mipush.platform.support

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.Context
import android.service.notification.StatusBarNotification
import com.xiaomi.push.service.NotificationIdentityBridge
import com.xiaomi.push.service.NotificationManagerPlatformSupport

/**
 * Product-owned boundary for the retained vendor notification identity/runtime facades.
 *
 * XMSF notification policy must not spread direct vendor imports across feature classes. The
 * vendor implementations remain the stock/fallback route; this adapter owns the translation to
 * product-facing types and strategy names.
 */
internal object NotificationVendorAdapter {
    enum class IdentityStrategy {
        FRAMEWORK,
        DELEGATED,
        UNSUPPORTED,
    }

    fun dumpIdentityDiagnostics(
        context: Context,
        packageName: String,
        channelId: String?,
        groupId: String?,
    ): String = NotificationIdentityBridge.dumpDiagnostics(context, packageName, channelId, groupId)

    fun canCreateTargetChannels(context: Context, packageName: String): Boolean =
        NotificationIdentityBridge.canCreateTargetChannels(context, packageName)

    fun getPreferredTargetChannel(
        context: Context,
        packageName: String,
        preferredChannelId: String?,
    ): NotificationChannel? = NotificationIdentityBridge.getPreferredTargetNotificationChannel(
        context,
        packageName,
        preferredChannelId,
    )

    fun resolveIdentityStrategy(context: Context, packageName: String): IdentityStrategy =
        when (NotificationIdentityBridge.resolveStrategy(context, packageName)) {
            NotificationIdentityBridge.Strategy.FRAMEWORK -> IdentityStrategy.FRAMEWORK
            NotificationIdentityBridge.Strategy.DELEGATED -> IdentityStrategy.DELEGATED
            NotificationIdentityBridge.Strategy.UNSUPPORTED -> IdentityStrategy.UNSUPPORTED
        }

    fun shouldAttemptCompatTargetPost(context: Context, packageName: String): Boolean =
        NotificationIdentityBridge.shouldAttemptCompatTargetPost(context, packageName)

    fun getTargetChannel(
        context: Context,
        packageName: String,
        channelId: String?,
    ): NotificationChannel? = NotificationIdentityBridge.getTargetNotificationChannel(
        context,
        packageName,
        channelId,
    )

    fun notifyAsTarget(
        context: Context,
        packageName: String,
        tag: String?,
        id: Int,
        notification: android.app.Notification,
    ): Boolean = NotificationIdentityBridge.notifyAsTargetPackage(context, packageName, tag, id, notification)

    fun cancelAsTarget(
        context: Context,
        packageName: String,
        tag: String?,
        id: Int,
    ): Boolean = NotificationIdentityBridge.cancelAsTargetPackage(context, packageName, tag, id)

    fun getTargetChannels(context: Context, packageName: String): List<NotificationChannel> =
        NotificationIdentityBridge.getTargetNotificationChannels(context, packageName)

    fun createTargetChannels(
        context: Context,
        packageName: String,
        channels: List<NotificationChannel>,
    ): Boolean = NotificationIdentityBridge.createTargetNotificationChannels(context, packageName, channels)

    fun deleteTargetChannel(context: Context, packageName: String, channelId: String): Boolean =
        NotificationIdentityBridge.deleteTargetNotificationChannel(context, packageName, channelId)

    fun createTargetGroups(
        context: Context,
        packageName: String,
        groups: List<NotificationChannelGroup>,
    ): Boolean = NotificationIdentityBridge.createTargetNotificationChannelGroups(context, packageName, groups)

    fun getTargetGroups(context: Context, packageName: String): List<NotificationChannelGroup> =
        NotificationIdentityBridge.getTargetNotificationChannelGroups(context, packageName)

    fun initPlatform(context: Context) {
        NotificationManagerPlatformSupport.init(context)
    }

    fun isRomNotificationBelongToAppSupported(context: Context): Boolean =
        NotificationManagerPlatformSupport.isRomSupportNotificationBelongToApp(context)

    fun getPlatformChannels(packageName: String): List<NotificationChannel>? =
        NotificationManagerPlatformSupport.getNotificationChannels(packageName)

    fun getPlatformGroups(packageName: String): List<NotificationChannelGroup>? =
        NotificationManagerPlatformSupport.getNotificationChannelGroups(packageName)

    @Suppress("DEPRECATION") // StatusBarNotification.userId is the only API available in the compile SDK.
    fun getPlatformActiveNotifications(packageName: String, userId: Int): List<StatusBarNotification>? =
        NotificationManagerPlatformSupport.getActiveNotifications(packageName, userId)
            ?.filter { it.userId == userId }

    @Suppress("DEPRECATION") // StatusBarNotification.userId is the only API available in the compile SDK.
    fun filterLocalActiveNotifications(
        packageName: String,
        notifications: Array<StatusBarNotification>,
        userId: Int,
    ): List<StatusBarNotification> = NotificationManagerPlatformSupport
        .filterLocalActiveNotifications(packageName, notifications)
        .filter { it.userId == userId }
}
