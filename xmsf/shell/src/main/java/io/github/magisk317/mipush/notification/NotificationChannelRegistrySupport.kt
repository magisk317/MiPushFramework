package io.github.magisk317.mipush.notification

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.xiaomi.push.service.NotificationIdentityBridge
import com.xiaomi.push.service.NotificationManagerPlatformSupport
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.platform.support.XMPushUtils

internal class NotificationChannelRegistrySupport(
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val isHooked: Boolean,
    private val diagnostics: (String, String, String?, String?, Throwable?) -> Unit,
) {
    private fun canUseLegacyPackageScopedApis(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM

    private fun shouldUseModernIdentityStrategy(packageName: String): Boolean =
        packageName != context.packageName && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private fun isTargetPackageAvailable(packageName: String): Boolean =
        packageName == context.packageName || Utils.isAppInstalled(context, packageName)

    private fun maybeLogDiagnosticsOnce(
        reason: String,
        packageName: String,
        channelId: String?,
        groupId: String?,
        throwable: Throwable? = null,
    ) = diagnostics(reason, packageName, channelId, groupId, throwable)

    fun getNotificationManagerForPackage(packageName: String): NotificationManager? {
        if (packageName == context.packageName) return notificationManager
        val packageContext = XMPushUtils.getPackageContext(context, packageName)
        if (packageContext === context) return null
        return try {
            packageContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            logE("Failed to query package NotificationManager for $packageName", e)
            null
        }
    }

    fun getDirectPackageNotificationChannel(packageName: String, channelId: String?): NotificationChannel? {
        if (channelId.isNullOrEmpty()) return null
        if (shouldUseModernIdentityStrategy(packageName)) {
            val channel = NotificationIdentityBridge.getTargetNotificationChannel(context, packageName, channelId)
            if (channel == null) maybeLogDiagnosticsOnce("target-channel-unavailable", packageName, channelId, null)
            return channel
        }
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            return if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.getNotificationChannel(channelId)
                } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                    logE("Failed to query package channel via package context for $packageName/$channelId", e)
                    null
                }
            } else null
        }
        return try {
            val method = NotificationManager::class.java.getMethod(
                "getNotificationChannelForPackage", String::class.java, String::class.java,
            )
            method.invoke(notificationManager, packageName, channelId) as? NotificationChannel
        } catch (e: ReflectiveOperationException) {
            logE("Failed to invoke getNotificationChannelForPackage", e)
            null
        }
    }

    private fun getDirectPackageNotificationChannelGroup(
        packageName: String,
        groupId: String?,
    ): NotificationChannelGroup? {
        if (groupId.isNullOrEmpty() || shouldUseModernIdentityStrategy(packageName)) return null
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            return if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.getNotificationChannelGroup(groupId)
                } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                    logE("Failed to query package group via package context for $packageName/$groupId", e)
                    null
                }
            } else null
        }
        return try {
            val method = NotificationManager::class.java.getMethod(
                "getNotificationChannelGroupForPackage", String::class.java, String::class.java,
            )
            method.invoke(notificationManager, packageName, groupId) as? NotificationChannelGroup
        } catch (e: ReflectiveOperationException) {
            logE("Failed to invoke getNotificationChannelGroupForPackage", e)
            null
        }
    }

    private fun createLocalNotificationChannels(channels: List<NotificationChannel>) {
        if (channels.isNotEmpty()) notificationManager.createNotificationChannels(channels)
    }

    private fun createLocalNotificationChannelGroups(groups: List<NotificationChannelGroup>) {
        if (groups.isNotEmpty()) notificationManager.createNotificationChannelGroups(groups)
    }

    private fun shouldUseLocalChannelFallback(packageName: String, channelId: String?): Boolean =
        NotificationOwnershipPolicy.shouldUseLocalChannel(packageName, channelId, context.packageName)

    private fun shouldUseLocalGroupFallback(packageName: String, groupId: String?): Boolean =
        NotificationOwnershipPolicy.shouldUseLocalGroup(packageName, groupId, context.packageName)

    private fun shouldUseLocalChannelFallback(
        packageName: String,
        channelId: String?,
        hostPackageName: String,
    ): Boolean = NotificationOwnershipPolicy.shouldUseLocalChannel(packageName, channelId, hostPackageName)

    private fun shouldUseLocalGroupFallback(
        packageName: String,
        groupId: String?,
        hostPackageName: String,
    ): Boolean = NotificationOwnershipPolicy.shouldUseLocalGroup(packageName, groupId, hostPackageName)

    fun createNotificationChannels(
        packageName: String,
        channels: List<NotificationChannel?>
    ) {
        logD("createNotificationChannels() called with: packageName = $packageName, channels = $channels")
        val nonNullChannels = channels.filterNotNull()
        if (!isTargetPackageAvailable(packageName)) {
            logD("skip createNotificationChannels for absent target package pkg=$packageName")
            return
        }
        if (shouldUseModernIdentityStrategy(packageName)) {
            if (NotificationIdentityBridge.createTargetNotificationChannels(context, packageName, nonNullChannels)) {
                if (!isHooked) {
                    createLocalNotificationChannels(
                        nonNullChannels.filter {
                            shouldUseLocalChannelFallback(packageName, it.id, context.packageName)
                        },
                    )
                }
                return
            }
            maybeLogDiagnosticsOnce(
                "target-channel-create-unsupported",
                packageName,
                nonNullChannels.firstOrNull()?.id,
                nonNullChannels.firstOrNull()?.group
            )
            if (!isHooked) {
                createLocalNotificationChannels(
                    nonNullChannels.filter {
                        shouldUseLocalChannelFallback(packageName, it.id, context.packageName)
                    },
                )
            }
            return
        }
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.createNotificationChannels(nonNullChannels)
                    return
                } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
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
            } catch (e: ReflectiveOperationException) {
                logE("Failed to invoke createNotificationChannelsForPackage", e)
            }
        }
        if (!isHooked) {
            notificationManager.createNotificationChannels(
                nonNullChannels.filter {
                    shouldUseLocalChannelFallback(packageName, it.id, context.packageName)
                },
            )
        }
    }

    fun getNotificationChannel(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        val directChannel = getDirectPackageNotificationChannel(packageName, channelId)
        if (directChannel != null) {
            return directChannel
        }
        // Only fall back to XMSF-local lookup for this process package or MiPush-managed ids.
        if (packageName == context.packageName ||
            io.github.magisk317.mipush.common.utils.NotificationUtils.isMiPushManagedChannelId(packageName, channelId)
        ) {
            return notificationManager.getNotificationChannel(channelId)
        }
        return null
    }

    fun getNotificationChannels(
        packageName: String
    ): List<NotificationChannel?>? {
        logD(
            "getNotificationChannels() called with: packageName = $packageName " +
                "isHooked=$isHooked modern=${shouldUseModernIdentityStrategy(packageName)} " +
                "legacyScoped=${canUseLegacyPackageScopedApis()} sdk=${Build.VERSION.SDK_INT}"
        )
        if (packageName == context.packageName) {
            val local = notificationManager.notificationChannels
            logD("getNotificationChannels self pkg count=${local.size}")
            return local
        }

        val strategy = runCatching {
            NotificationIdentityBridge.resolveStrategy(context, packageName)
        }.getOrNull()
        logD("getNotificationChannels strategy=$strategy for $packageName")

        if (shouldUseModernIdentityStrategy(packageName)) {
            val targetChannels = NotificationIdentityBridge.getTargetNotificationChannels(context, packageName)
            logD("getNotificationChannels identity count=${targetChannels.size} pkg=$packageName")
            if (targetChannels.isNotEmpty()) {
                return targetChannels
            }
            maybeLogDiagnosticsOnce("target-channel-list-empty", packageName, null, null)

            val platformChannels = queryPlatformNotificationChannels(packageName)
            if (platformChannels.isNotEmpty()) {
                logI("getNotificationChannels platform fallback count=${platformChannels.size} pkg=$packageName")
                return platformChannels
            }

            // Package-context NM is expected to fail for foreign packages on modern SDKs.
            if (!canUseLegacyPackageScopedApis()) {
                val packageNotificationManager = getNotificationManagerForPackage(packageName)
                if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                    try {
                        val packageChannels = packageNotificationManager.notificationChannels
                        logD("getNotificationChannels package-context count=${packageChannels.size} pkg=$packageName")
                        if (packageChannels.isNotEmpty()) {
                            return packageChannels
                        }
                    } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                        logW("Failed to query channels via package context for $packageName: ${e.message}")
                    }
                }
            }

            val managedLocal = localManagedChannels(packageName)
            if (managedLocal.isNotEmpty()) {
                logI("getNotificationChannels local-managed fallback count=${managedLocal.size} pkg=$packageName")
                return managedLocal
            }
            logW("getNotificationChannels empty after all fallbacks pkg=$packageName")
            return emptyList()
        }

        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    return packageNotificationManager.notificationChannels
                } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                    logW("Failed to query channels via package context for $packageName: ${e.message}")
                }
            }
            val platformChannels = queryPlatformNotificationChannels(packageName)
            if (platformChannels.isNotEmpty()) {
                return platformChannels
            }
            return localManagedChannels(packageName)
        }

        val platformChannels = queryPlatformNotificationChannels(packageName)
        if (platformChannels.isNotEmpty()) {
            return platformChannels
        }
        return localManagedChannels(packageName)
    }

    private fun queryPlatformNotificationChannels(packageName: String): List<NotificationChannel?> {
        return runCatching {
            NotificationManagerPlatformSupport.init(context)
            NotificationManagerPlatformSupport.getNotificationChannels(packageName).orEmpty()
        }.onFailure {
            logE("queryPlatformNotificationChannels failed pkg=$packageName", it)
        }.getOrDefault(emptyList())
    }

    private fun localManagedChannels(packageName: String): List<NotificationChannel?> {
        return notificationManager.notificationChannels
            .filter { channel ->
                channel != null && (
                    channel.group == io.github.magisk317.mipush.common.utils.NotificationUtils.getGroupIdByPkg(packageName) ||
                        io.github.magisk317.mipush.common.utils.NotificationUtils.isMiPushManagedChannelId(packageName, channel.id)
                    )
            }
    }

    fun deleteNotificationChannel(
        packageName: String,
        channelId: String?
    ): Boolean {
        logD("deleteNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        if (channelId.isNullOrEmpty()) {
            return false
        }
        if (packageName == context.packageName) {
            return runCatching {
                notificationManager.deleteNotificationChannel(channelId)
                getNotificationChannel(packageName, channelId) == null
            }.getOrElse {
                logE("Failed to delete local notification channel $channelId", it)
                false
            }
        }
        if (shouldUseModernIdentityStrategy(packageName)) {
            // Prefer deleting under the target package identity when possible.
            if (NotificationIdentityBridge.deleteTargetNotificationChannel(context, packageName, channelId)) {
                return true
            }
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.deleteNotificationChannel(channelId)
                    // Same no-op risk as createPackageContext; only stop if channel is gone.
                    if (getNotificationChannel(packageName, channelId) == null) {
                        return true
                    }
                } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                    logE("Failed to delete channel via package context for $packageName/$channelId", e)
                }
            }
            // Only fall back to local XMSF NM for MiPush-managed channels that may live here.
            if (io.github.magisk317.mipush.common.utils.NotificationUtils.isMiPushManagedChannelId(packageName, channelId)) {
                return runCatching {
                    notificationManager.deleteNotificationChannel(channelId)
                    getNotificationChannel(packageName, channelId) == null
                }.getOrElse {
                    logE("Failed to delete managed notification channel $packageName/$channelId", it)
                    false
                }
            } else {
                maybeLogDiagnosticsOnce("target-channel-delete-unsupported", packageName, channelId, null)
            }
            return false
        }
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.deleteNotificationChannel(channelId)
                    if (getNotificationChannel(packageName, channelId) == null) {
                        return true
                    }
                } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                    logE("Failed to delete channel via package context for $packageName/$channelId", e)
                }
            }
        }
        if (io.github.magisk317.mipush.common.utils.NotificationUtils.isMiPushManagedChannelId(packageName, channelId) ||
            packageName == context.packageName
        ) {
            return runCatching {
                notificationManager.deleteNotificationChannel(channelId)
                getNotificationChannel(packageName, channelId) == null
            }.getOrElse {
                logE("Failed to delete managed notification channel $packageName/$channelId", it)
                false
            }
        }
        return false
    }


    fun createNotificationChannelGroups(
        packageName: String,
        groups: List<NotificationChannelGroup?>
    ) {
        logD("createNotificationChannelGroups() called with: packageName = $packageName, groups = $groups")
        val nonNullGroups = groups.filterNotNull()
        if (!isTargetPackageAvailable(packageName)) {
            logD("skip createNotificationChannelGroups for absent target package pkg=$packageName")
            return
        }
        if (shouldUseModernIdentityStrategy(packageName)) {
            if (NotificationIdentityBridge.createTargetNotificationChannelGroups(context, packageName, nonNullGroups)) {
                if (!isHooked) {
                    createLocalNotificationChannelGroups(
                        nonNullGroups.filter {
                            shouldUseLocalGroupFallback(packageName, it.id, context.packageName)
                        },
                    )
                }
                return
            }
            maybeLogDiagnosticsOnce(
                "target-group-create-unsupported",
                packageName,
                null,
                nonNullGroups.firstOrNull()?.id
            )
            if (!isHooked) {
                createLocalNotificationChannelGroups(
                    nonNullGroups.filter {
                        shouldUseLocalGroupFallback(packageName, it.id, context.packageName)
                    },
                )
            }
            return
        }
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.createNotificationChannelGroups(nonNullGroups)
                    return
                } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                    logE("Failed to create groups via package context for $packageName", e)
                }
            }
        }
        notificationManager.createNotificationChannelGroups(
            nonNullGroups.filter {
                shouldUseLocalGroupFallback(packageName, it.id, context.packageName)
            },
        )
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
        if (packageName == context.packageName ||
            io.github.magisk317.mipush.common.utils.NotificationUtils.isMiPushManagedGroupId(packageName, groupId)
        ) {
            return notificationManager.getNotificationChannelGroup(groupId)
        }
        return null
    }

    fun getNotificationChannelGroups(
        packageName: String
    ): List<NotificationChannelGroup?>? {
        logD(
            "getNotificationChannelGroups() called with: packageName = $packageName " +
                "isHooked=$isHooked modern=${shouldUseModernIdentityStrategy(packageName)}"
        )
        if (packageName == context.packageName) {
            return notificationManager.notificationChannelGroups
        }
        if (shouldUseModernIdentityStrategy(packageName)) {
            val targetGroups = NotificationIdentityBridge.getTargetNotificationChannelGroups(context, packageName)
            logD("getNotificationChannelGroups identity count=${targetGroups.size} pkg=$packageName")
            if (targetGroups.isNotEmpty()) {
                return targetGroups
            }
            maybeLogDiagnosticsOnce("target-group-list-empty", packageName, null, null)

            val platformGroups = queryPlatformNotificationChannelGroups(packageName)
            if (platformGroups.isNotEmpty()) {
                logI("getNotificationChannelGroups platform fallback count=${platformGroups.size} pkg=$packageName")
                return platformGroups
            }

            if (!canUseLegacyPackageScopedApis()) {
                val packageNotificationManager = getNotificationManagerForPackage(packageName)
                if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                    try {
                        val packageGroups = packageNotificationManager.notificationChannelGroups
                        logD("getNotificationChannelGroups package-context count=${packageGroups.size} pkg=$packageName")
                        if (packageGroups.isNotEmpty()) {
                            return packageGroups
                        }
                    } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                        logW("Failed to query groups via package context for $packageName: ${e.message}")
                    }
                }
            }
            val managedLocal = localManagedGroups(packageName)
            if (managedLocal.isNotEmpty()) {
                logI("getNotificationChannelGroups local-managed fallback count=${managedLocal.size} pkg=$packageName")
                return managedLocal
            }
            logW("getNotificationChannelGroups empty after all fallbacks pkg=$packageName")
            return emptyList()
        }
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    return packageNotificationManager.notificationChannelGroups
                } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                    logW("Failed to query groups via package context for $packageName: ${e.message}")
                }
            }
            val platformGroups = queryPlatformNotificationChannelGroups(packageName)
            if (platformGroups.isNotEmpty()) {
                return platformGroups
            }
            return localManagedGroups(packageName)
        }
        val platformGroups = queryPlatformNotificationChannelGroups(packageName)
        if (platformGroups.isNotEmpty()) {
            return platformGroups
        }
        return localManagedGroups(packageName)
    }

    private fun queryPlatformNotificationChannelGroups(packageName: String): List<NotificationChannelGroup?> {
        return runCatching {
            NotificationManagerPlatformSupport.init(context)
            NotificationManagerPlatformSupport.getNotificationChannelGroups(packageName).orEmpty()
        }.onFailure {
            logE("queryPlatformNotificationChannelGroups failed pkg=$packageName", it)
        }.getOrDefault(emptyList())
    }

    private fun localManagedGroups(packageName: String): List<NotificationChannelGroup?> {
        return notificationManager.notificationChannelGroups
            .filter { group ->
                group != null &&
                    io.github.magisk317.mipush.common.utils.NotificationUtils.isMiPushManagedGroupId(
                        packageName,
                        group.id,
                    )
            }
    }

    fun deleteNotificationChannelGroup(
        packageName: String,
        groupId: String?
    ) {
        logD("deleteNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        if (shouldUseModernIdentityStrategy(packageName)) {
            // The host NotificationManager is scoped to XMSF. Never use it for an arbitrary
            // target package, or a same-named group in XMSF can be deleted instead.
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.deleteNotificationChannelGroup(groupId)
                    return
                } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                    logE("Failed to delete group via package context for $packageName/$groupId", e)
                }
            }
            if (shouldUseLocalGroupFallback(packageName, groupId, context.packageName)) {
                runCatching { notificationManager.deleteNotificationChannelGroup(groupId) }
                    .onFailure { logE("Failed to delete managed notification group $packageName/$groupId", it) }
            } else {
                maybeLogDiagnosticsOnce("target-group-delete-unsupported", packageName, null, groupId)
            }
            return
        }
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.deleteNotificationChannelGroup(groupId)
                    return
                } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                    logE("Failed to delete group via package context for $packageName/$groupId", e)
                }
            }
        }
        if (shouldUseLocalGroupFallback(packageName, groupId, context.packageName)) {
            runCatching { notificationManager.deleteNotificationChannelGroup(groupId) }
                .onFailure { logE("Failed to delete notification group $packageName/$groupId", it) }
        }
    }

}
