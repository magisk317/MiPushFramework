package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.push.service.NotificationUtils
import com.xiaomi.push.service.NotificationIdentityBridge
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.aakira.napier.Napier
import java.util.Collections

object NotificationManagerEx {
    private const val TAG = "NotificationManagerEx"
    private const val MODERN_IDENTITY_FIRST_SDK = Build.VERSION_CODES.Q
    @JvmField
    val HOOK_API_VERSION = 2
    private val diagnosticsLogged = Collections.synchronizedSet(mutableSetOf<String>())

    private lateinit var appContext: Context
    private lateinit var notificationManager: NotificationManager

    @JvmField
    var isHooked: Boolean = false

    /**
     * SDK 37+ gets a modern identity-first path:
     * 1. framework/ROM support for belong-to-app notification identity
     * 2. delegated notifyAsPackage if system allows it
     * 3. local XMSF fallback
     */
    private fun canUseLegacyPackageScopedApis(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
    }

    @JvmStatic
    fun init(context: Context) {
        appContext = context.applicationContext
        notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun shouldUseModernIdentityStrategy(packageName: String): Boolean {
        return packageName != appContext.packageName && Build.VERSION.SDK_INT >= MODERN_IDENTITY_FIRST_SDK
    }

    private fun isModuleEnhancedModeActive(packageName: String): Boolean {
        return ::appContext.isInitialized &&
            isHooked &&
            packageName != appContext.packageName &&
            MIUIUtils.isMIUI() &&
            MIUIUtils.isXMSF(appContext)
    }

    private fun maybeLogDiagnosticsOnce(
        reason: String,
        packageName: String,
        channelId: String?,
        groupId: String?,
        throwable: Throwable? = null
    ) {
        if (!::appContext.isInitialized || !shouldUseModernIdentityStrategy(packageName)) {
            return
        }
        val key = listOf(reason, packageName, channelId, groupId).joinToString("|")
        if (!diagnosticsLogged.add(key)) {
            return
        }
        val message = "$reason ${NotificationIdentityBridge.dumpDiagnostics(appContext, packageName, channelId, groupId)}"
        if (throwable != null) {
            logE(message, throwable)
        } else {
            logD(message)
        }
    }

    private fun createLocalNotificationChannels(channels: List<NotificationChannel>) {
        if (channels.isNotEmpty()) {
            notificationManager.createNotificationChannels(channels)
        }
    }

    private fun createLocalNotificationChannelGroups(groups: List<NotificationChannelGroup>) {
        if (groups.isNotEmpty()) {
            notificationManager.createNotificationChannelGroups(groups)
        }
    }

    private fun filterLocalActiveNotifications(
        packageName: String,
        activeNotifications: Array<StatusBarNotification>
    ): Array<StatusBarNotification?> {
        if (!MIUIUtils.isMIUI()) {
            return activeNotifications.map { it as StatusBarNotification? }.toTypedArray()
        }
        return activeNotifications
            .filter { packageName == NotificationUtils.getTargetPackage(it.notification) }
            .map { it as StatusBarNotification? }
            .toTypedArray()
    }

    private fun markLocalTargetPackage(packageName: String, notification: Notification) {
        if (!::appContext.isInitialized || packageName == appContext.packageName) {
            return
        }
        runCatching {
            if (notification.extras != null) {
                notification.extras.putString("xmsf_target_package", packageName)
                notification.extras.putString("miui.targetPkg", packageName)
            }
            if (!MIUIUtils.isXMS() && MIUIUtils.isXMSF(appContext)) {
                NotificationUtils.setTargetPackage(notification, packageName)
            }
        }.onFailure {
            logE("Failed to mark local target package for $packageName", it)
        }
    }

    @JvmStatic
    fun supportsTargetChannelProvisioning(packageName: String): Boolean {
        if (!::appContext.isInitialized) {
            return false
        }
        if (packageName == appContext.packageName) {
            return true
        }
        return if (shouldUseModernIdentityStrategy(packageName)) {
            NotificationIdentityBridge.canCreateTargetChannels(appContext, packageName)
        } else {
            canUseLegacyPackageScopedApis()
        }
    }

    @JvmStatic
    fun findPreferredTargetChannel(
        packageName: String,
        preferredChannelId: String?
    ): NotificationChannel? {
        if (!::appContext.isInitialized) {
            return null
        }
        if (preferredChannelId.isNullOrBlank()) {
            return null
        }
        if (packageName == appContext.packageName) {
            return notificationManager.getNotificationChannel(preferredChannelId)
        }
        return if (shouldUseModernIdentityStrategy(packageName)) {
            NotificationIdentityBridge.getPreferredTargetNotificationChannel(appContext, packageName, preferredChannelId)
        } else {
            null
        }
    }

    private fun getNotificationManagerForPackage(packageName: String): NotificationManager? {
        if (!::appContext.isInitialized) {
            return null
        }
        if (packageName == appContext.packageName) {
            return notificationManager
        }
        val packageContext = XMPushUtils.getPackageContext(appContext, packageName)
        if (packageContext === appContext) {
            return null
        }
        return try {
            packageContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        } catch (e: Exception) {
            logE("Failed to query package NotificationManager for $packageName", e)
            null
        }
    }

    private fun getDirectPackageNotificationChannel(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        if (channelId.isNullOrEmpty()) {
            return null
        }
        if (shouldUseModernIdentityStrategy(packageName)) {
            val channel = NotificationIdentityBridge.getTargetNotificationChannel(appContext, packageName, channelId)
            if (channel == null) {
                maybeLogDiagnosticsOnce("target-channel-unavailable", packageName, channelId, null)
            }
            return channel
        }
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            return if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.getNotificationChannel(channelId)
                } catch (e: Exception) {
                    logE("Failed to query package channel via package context for $packageName/$channelId", e)
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
            logE("Failed to invoke getNotificationChannelForPackage", e)
            null
        }
    }

    private fun getDirectPackageNotificationChannelGroup(
        packageName: String,
        groupId: String?
    ): NotificationChannelGroup? {
        if (groupId.isNullOrEmpty()) {
            return null
        }
        if (shouldUseModernIdentityStrategy(packageName)) {
            return null
        }
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            return if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.getNotificationChannelGroup(groupId)
                } catch (e: Exception) {
                    logE("Failed to query package group via package context for $packageName/$groupId", e)
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
            logE("Failed to invoke getNotificationChannelGroupForPackage", e)
            null
        }
    }

    private fun shouldNotifyAsPackage(
        packageName: String,
        notification: Notification
    ): Boolean {
        val channelId = notification.channelId
        if (shouldUseModernIdentityStrategy(packageName)) {
            val strategy = NotificationIdentityBridge.resolveStrategy(appContext, packageName)
            val compatAttempt = strategy == NotificationIdentityBridge.Strategy.UNSUPPORTED &&
                NotificationIdentityBridge.shouldAttemptCompatTargetPost(appContext, packageName)
            val visible = when (strategy) {
                NotificationIdentityBridge.Strategy.FRAMEWORK -> true
                NotificationIdentityBridge.Strategy.DELEGATED ->
                    isHooked || NotificationIdentityBridge.getTargetNotificationChannel(appContext, packageName, channelId) != null
                NotificationIdentityBridge.Strategy.UNSUPPORTED -> compatAttempt
            }
            if (!visible) {
                maybeLogDiagnosticsOnce("identity-precheck-failed", packageName, channelId, notification.group)
            }
            return visible
        }
        val packageChannelVisible = getDirectPackageNotificationChannel(packageName, channelId) != null
        return packageChannelVisible
    }

    fun notify(
        packageName: String,
        tag: String?, id: Int, notification: Notification
    ): Boolean {
        // Fully replaced by HookPushNC when the Xposed module is active.
        Napier.d("notify() called with: packageName = $packageName, tag = $tag, id = $id, channel = ${notification.channelId}, group = ${notification.group}", tag = TAG)
        markLocalTargetPackage(packageName, notification)
        if (shouldUseModernIdentityStrategy(packageName)) {
            if (shouldNotifyAsPackage(packageName, notification)) {
                if (NotificationIdentityBridge.notifyAsTargetPackage(appContext, packageName, tag, id, notification)) {
                    return true
                }
                maybeLogDiagnosticsOnce("identity-notify-fallback", packageName, notification.channelId, notification.group)
            }
            return notifyLocally(tag, id, notification)
        }
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
                return true
            } catch (e: Exception) {
                logE("Failed to invoke notifyAsPackage", e)
            }
        }
        return notifyLocally(tag, id, notification)
    }

    private fun notifyLocally(tag: String?, id: Int, notification: Notification): Boolean {
        return runCatching {
            notificationManager.notify(tag, id, notification)
            true
        }.onFailure {
            logE("Failed to notify locally tag=$tag id=$id channel=${notification.channelId}", it)
        }.getOrDefault(false)
    }

    fun cancel(
        packageName: String,
        tag: String?, id: Int
    ) {
        // Fully replaced by HookPushNC when the Xposed module is active.
        Napier.d("cancel() called with: packageName = $packageName, tag = $tag, id = $id", tag = TAG)
        if (shouldUseModernIdentityStrategy(packageName)) {
            if (NotificationIdentityBridge.cancelAsTargetPackage(appContext, packageName, tag, id)) {
                logD("cancel() completed via target identity pkg=$packageName tag=$tag id=$id")
                return
            }
            maybeLogDiagnosticsOnce("identity-cancel-fallback", packageName, null, null)
            notificationManager.cancel(tag, id)
            logD("cancel() completed locally after identity fallback pkg=$packageName tag=$tag id=$id")
            return
        }
        var targetCancelSucceeded = false
        if (canUseLegacyPackageScopedApis() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                val method = NotificationManager::class.java.getMethod(
                    "cancelAsPackage",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                method.invoke(notificationManager, packageName, tag, id)
                targetCancelSucceeded = true
            } catch (e: Exception) {
                logE("Failed to invoke cancelAsPackage", e)
            }
        }
        notificationManager.cancel(tag, id)
        logD(
            "cancel() completed locally pkg=$packageName tag=$tag id=$id " +
                "targetCancelSucceeded=$targetCancelSucceeded"
        )
    }

    fun createNotificationChannels(
        packageName: String,
        channels: List<NotificationChannel?>
    ) {
        logD("createNotificationChannels() called with: packageName = $packageName, channels = $channels")
        val nonNullChannels = channels.filterNotNull()
        if (shouldUseModernIdentityStrategy(packageName)) {
            if (NotificationIdentityBridge.createTargetNotificationChannels(appContext, packageName, nonNullChannels)) {
                createLocalNotificationChannels(nonNullChannels)
                return
            }
            maybeLogDiagnosticsOnce(
                "target-channel-create-unsupported",
                packageName,
                nonNullChannels.firstOrNull()?.id,
                nonNullChannels.firstOrNull()?.group
            )
            createLocalNotificationChannels(nonNullChannels)
            return
        }
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.createNotificationChannels(nonNullChannels)
                    return
                } catch (e: Exception) {
                    logE("Failed to create notification channels via package context for $packageName", e)
                }
            }
        }
        if (canUseLegacyPackageScopedApis()) {
            try {
                val method = NotificationManager::class.java.getMethod(
                    "createNotificationChannelsForPackage",
                    String::class.java,
                    List::class.java
                )
                method.invoke(notificationManager, packageName, nonNullChannels)
                return
            } catch (e: Exception) {
                logE("Failed to invoke createNotificationChannelsForPackage", e)
            }
        }
        notificationManager.createNotificationChannels(nonNullChannels)
    }

    fun getNotificationChannel(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        logD("getNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        val directChannel = getDirectPackageNotificationChannel(packageName, channelId)
        if (directChannel != null) {
            return directChannel
        }
        return notificationManager.getNotificationChannel(channelId)
    }

    fun getNotificationChannels(
        packageName: String
    ): List<NotificationChannel?>? {
        logD("getNotificationChannels() called with: packageName = $packageName")
        if (shouldUseModernIdentityStrategy(packageName)) {
            val targetChannels = NotificationIdentityBridge.getTargetNotificationChannels(appContext, packageName)
            if (targetChannels.isNotEmpty()) {
                return targetChannels
            }
            maybeLogDiagnosticsOnce("target-channel-list-empty", packageName, null, null)
            if (!canUseLegacyPackageScopedApis()) {
                val packageNotificationManager = getNotificationManagerForPackage(packageName)
                if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                    try {
                        return packageNotificationManager.notificationChannels
                    } catch (e: Exception) {
                        logE("Failed to query channels via package context for $packageName", e)
                    }
                }
            }
            return try {
                val method = NotificationManager::class.java.getMethod(
                    "getNotificationChannelsForPackage",
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                @Suppress("UNCHECKED_CAST")
                method.invoke(notificationManager, packageName, 0) as? List<NotificationChannel?>
            } catch (e: Exception) {
                logE("Failed to invoke getNotificationChannelsForPackage", e)
                emptyList()
            }
        } else if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    return packageNotificationManager.notificationChannels
                } catch (e: Exception) {
                    logE("Failed to query channels via package context for $packageName", e)
                }
            }
        }
        return notificationManager.getNotificationChannels()
    }

    fun deleteNotificationChannel(
        packageName: String,
        channelId: String?
    ) {
        logD("deleteNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        if (shouldUseModernIdentityStrategy(packageName)) {
            notificationManager.deleteNotificationChannel(channelId)
            return
        }
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.deleteNotificationChannel(channelId)
                    return
                } catch (e: Exception) {
                    logE("Failed to delete channel via package context for $packageName/$channelId", e)
                }
            }
        }
        notificationManager.deleteNotificationChannel(channelId)
    }


    fun createNotificationChannelGroups(
        packageName: String,
        groups: List<NotificationChannelGroup?>
    ) {
        logD("createNotificationChannelGroups() called with: packageName = $packageName, groups = $groups")
        val nonNullGroups = groups.filterNotNull()
        if (shouldUseModernIdentityStrategy(packageName)) {
            if (NotificationIdentityBridge.createTargetNotificationChannelGroups(appContext, packageName, nonNullGroups)) {
                createLocalNotificationChannelGroups(nonNullGroups)
                return
            }
            maybeLogDiagnosticsOnce(
                "target-group-create-unsupported",
                packageName,
                null,
                nonNullGroups.firstOrNull()?.id
            )
            createLocalNotificationChannelGroups(nonNullGroups)
            return
        }
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.createNotificationChannelGroups(nonNullGroups)
                    return
                } catch (e: Exception) {
                    logE("Failed to create groups via package context for $packageName", e)
                }
            }
        }
        notificationManager.createNotificationChannelGroups(nonNullGroups)
    }

    fun getNotificationChannelGroup(
        packageName: String,
        groupId: String?
    ): NotificationChannelGroup? {
        logD("getNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        val directGroup = getDirectPackageNotificationChannelGroup(packageName, groupId)
        if (directGroup != null) {
            return directGroup
        }
        return notificationManager.getNotificationChannelGroup(groupId)
    }

    fun getNotificationChannelGroups(
        packageName: String
    ): List<NotificationChannelGroup?>? {
        logD("getNotificationChannelGroups() called with: packageName = $packageName")
        if (shouldUseModernIdentityStrategy(packageName)) {
            val targetGroups = NotificationIdentityBridge.getTargetNotificationChannelGroups(appContext, packageName)
            if (targetGroups.isNotEmpty()) {
                return targetGroups
            }
            maybeLogDiagnosticsOnce("target-group-list-empty", packageName, null, null)
            if (!canUseLegacyPackageScopedApis()) {
                val packageNotificationManager = getNotificationManagerForPackage(packageName)
                if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                    try {
                        return packageNotificationManager.notificationChannelGroups
                    } catch (e: Exception) {
                        logE("Failed to query groups via package context for $packageName", e)
                    }
                }
            }
            return try {
                val method = NotificationManager::class.java.getMethod(
                    "getNotificationChannelGroupsForPackage",
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                @Suppress("UNCHECKED_CAST")
                method.invoke(notificationManager, packageName, 0) as? List<NotificationChannelGroup?>
            } catch (e: Exception) {
                logE("Failed to invoke getNotificationChannelGroupsForPackage", e)
                emptyList()
            }
        } else if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    return packageNotificationManager.notificationChannelGroups
                } catch (e: Exception) {
                    logE("Failed to query groups via package context for $packageName", e)
                }
            }
        }
        return notificationManager.getNotificationChannelGroups()
    }

    fun deleteNotificationChannelGroup(
        packageName: String,
        groupId: String?
    ) {
        logD("deleteNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        if (shouldUseModernIdentityStrategy(packageName)) {
            notificationManager.deleteNotificationChannelGroup(groupId)
            return
        }
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.deleteNotificationChannelGroup(groupId)
                    return
                } catch (e: Exception) {
                    logE("Failed to delete group via package context for $packageName/$groupId", e)
                }
            }
        }
        notificationManager.deleteNotificationChannelGroup(groupId)
    }

    fun areNotificationsEnabled(
        packageName: String
    ): Boolean {
        // Fully replaced by HookPushNC when the Xposed module is active.
        logD("areNotificationsEnabled() called with: packageName = $packageName")

        // 1. Check if the target app has notifications enabled in the system
        val systemEnabled = try {
            val packageNM = getNotificationManagerForPackage(packageName)
            packageNM?.areNotificationsEnabled() ?: notificationManager.areNotificationsEnabled()
        } catch (e: Exception) {
            notificationManager.areNotificationsEnabled()
        }
        
        if (!systemEnabled) {
            logD("System notifications disabled for $packageName")
            return false
        }

        // 2. Check if identity strategy is supported for this package
        if (shouldUseModernIdentityStrategy(packageName)) {
            val strategy = NotificationIdentityBridge.resolveStrategy(appContext, packageName)
            val strategySupported = strategy != NotificationIdentityBridge.Strategy.UNSUPPORTED
            if (!strategySupported) {
                maybeLogDiagnosticsOnce("identity-unsupported", packageName, null, null)
            }
            return strategySupported
        }
        
        return true
    }

    fun getActiveNotifications(
        packageName: String
    ): Array<StatusBarNotification?>? {
        logD("getActiveNotifications() called with: packageName = $packageName")
        if (shouldUseModernIdentityStrategy(packageName)) {
            filterLocalActiveNotifications(packageName, notificationManager.getActiveNotifications())
        } else if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    return packageNotificationManager.activeNotifications
                } catch (e: Exception) {
                    logE("Failed to query active notifications via package context for $packageName", e)
                }
            }
        }
        return notificationManager.getActiveNotifications()
    }

}
