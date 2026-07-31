package io.github.magisk317.mipush.notification

import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.common.utils.Utils

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
import com.xiaomi.push.service.NotificationManagerPlatformSupport
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.platform.support.PermissionUtils
import java.util.Collections

object NotificationManagerEx {
    private const val TAG = "NotificationManagerEx"
    private const val MODERN_IDENTITY_FIRST_SDK = Build.VERSION_CODES.Q
    private const val EXTRA_XMSF_TARGET_PACKAGE = "xmsf_target_package"
    private const val EXTRA_MIUI_TARGET_PACKAGE = "miui.targetPkg"
    private const val EXTRA_SUBSTITUTE_APP_NAME = "android.substName"
    @JvmField
    val HOOK_API_VERSION = 2
    private val diagnosticsLogged = Collections.synchronizedSet(mutableSetOf<String>())

    private lateinit var appContext: Context
    private lateinit var notificationManager: NotificationManager

    @JvmField
    @Volatile
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

    private fun isTargetPackageAvailable(packageName: String): Boolean {
        if (!::appContext.isInitialized) {
            return false
        }
        return packageName == appContext.packageName || Utils.isAppInstalled(appContext, packageName)
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
        // Stock 7.4.67-C g1.h scopes local XMSF records by target_package on every ROM.
        // The old non-MIUI bypass returned all delegated posts and made package-specific
        // clear unsafe once focus notifications began falling back to native live updates.
        return NotificationManagerPlatformSupport
            .filterLocalActiveNotifications(packageName, activeNotifications)
            .map { it as StatusBarNotification? }
            .toTypedArray()
    }

    private fun markLocalTargetPackage(packageName: String, notification: Notification) {
        if (!::appContext.isInitialized || packageName == appContext.packageName) {
            return
        }
        runCatching {
            if (notification.extras != null) {
                notification.extras.putString(EXTRA_XMSF_TARGET_PACKAGE, packageName)
                notification.extras.putString(EXTRA_MIUI_TARGET_PACKAGE, packageName)
                notification.extras.putString("target_package", packageName)
                // When identity falls back to posting as xmsf (e.g. Live Update AppOps), SystemUI
                // still shows the posting package label ("推送服务"). Prefer the target app name.
                val appLabel = runCatching {
                    val pm = appContext.packageManager
                    val info = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(info).toString()
                }.getOrNull()?.takeIf { it.isNotBlank() }
                if (!appLabel.isNullOrBlank()) {
                    notification.extras.putString(EXTRA_SUBSTITUTE_APP_NAME, appLabel)
                    notification.extras.putString("android.substName", appLabel)
                }
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
        // Attribution marker: when isHooked is true the system NMS hook owns publishing and this
        // app-process body is normally bypassed. Seeing this line run with isHooked=true means the
        // hook did not intercept and we are about to publish as a local (non-owned) fallback.
        logD("notify() attribution pkg=$packageName isHooked=$isHooked id=$id channel=${notification.channelId}")
        if (!isTargetPackageAvailable(packageName)) {
            logD("drop notification for absent target package pkg=$packageName tag=$tag id=$id channel=${notification.channelId}")
            emitNotify(
                result = "skip",
                reason = "target_absent",
                packageName = packageName,
            )
            return false
        }
        markLocalTargetPackage(packageName, notification)
        if (shouldUseModernIdentityStrategy(packageName)) {
            if (shouldNotifyAsPackage(packageName, notification)) {
                if (NotificationIdentityBridge.notifyAsTargetPackage(appContext, packageName, tag, id, notification)) {
                    emitNotify(result = "ok", reason = "identity_target", packageName = packageName)
                    return true
                }
                if (maybeRetryNotifyAsTargetAfterAppOpsGrant(packageName, tag, id, notification)) {
                    emitNotify(result = "ok", reason = "identity_retry", packageName = packageName)
                    return true
                }
                maybeLogDiagnosticsOnce("identity-notify-fallback", packageName, notification.channelId, notification.group)
            }
            val local = notifyLocally(tag, id, notification)
            emitNotify(
                result = if (local) "ok" else "error",
                reason = "local_fallback",
                packageName = packageName,
                statusOk = local,
            )
            return local
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
                emitNotify(result = "ok", reason = "notify_as_package", packageName = packageName)
                return true
            } catch (e: Exception) {
                logE("Failed to invoke notifyAsPackage", e)
            }
        }
        val local = notifyLocally(tag, id, notification)
        emitNotify(
            result = if (local) "ok" else "error",
            reason = "local",
            packageName = packageName,
            statusOk = local,
        )
        return local
    }

    private fun emitNotify(
        result: String,
        reason: String,
        packageName: String,
        statusOk: Boolean = true,
    ) {
        MagiskOtel.event(
            name = "push.dispatch",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "notify_publish",
                "reason" to reason,
                "target_package" to packageName,
            ),
            statusOk = statusOk,
        )
    }


    /**
     * Android 16+ Live Updates (ProgressStyle / promoted ongoing) can require
     * UPDATE_APP_OPS_STATS for notifyAsPackage. After reinstall that grant is often missing
     * until root silent-grant runs; retry once after best-effort grant.
     */
    private fun maybeRetryNotifyAsTargetAfterAppOpsGrant(
        packageName: String,
        tag: String?,
        id: Int,
        notification: Notification,
    ): Boolean {
        val isLiveUpdate = notification.extras?.getBoolean("xmsf.live_update", false) == true ||
            notification.extras?.getBoolean("android.requestPromotedOngoing", false) == true
        if (!isLiveUpdate) return false
        val granted = runCatching {
            PermissionUtils.grantSilentPermissions(packageName = appContext.packageName)
        }.getOrDefault(false)
        if (!granted) {
            logD("live-update identity retry skipped: silent grant failed pkg=$packageName id=$id")
            return false
        }
        val ok = NotificationIdentityBridge.notifyAsTargetPackage(appContext, packageName, tag, id, notification)
        logD("live-update identity retry after appops grant pkg=$packageName id=$id ok=$ok")
        return ok
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
        if (!isTargetPackageAvailable(packageName)) {
            logD("skip createNotificationChannels for absent target package pkg=$packageName")
            return
        }
        if (shouldUseModernIdentityStrategy(packageName)) {
            if (NotificationIdentityBridge.createTargetNotificationChannels(appContext, packageName, nonNullChannels)) {
                if (!isHooked) {
                    createLocalNotificationChannels(nonNullChannels)
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
                createLocalNotificationChannels(nonNullChannels)
            }
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
        if (!isHooked) {
            notificationManager.createNotificationChannels(nonNullChannels)
        }
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
        // Only fall back to XMSF-local lookup for this process package or MiPush-managed ids.
        if (packageName == appContext.packageName ||
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
        if (packageName == appContext.packageName) {
            val local = notificationManager.notificationChannels
            logD("getNotificationChannels self pkg count=${local.size}")
            return local
        }

        val strategy = runCatching {
            NotificationIdentityBridge.resolveStrategy(appContext, packageName)
        }.getOrNull()
        logD("getNotificationChannels strategy=$strategy for $packageName")

        if (shouldUseModernIdentityStrategy(packageName)) {
            val targetChannels = NotificationIdentityBridge.getTargetNotificationChannels(appContext, packageName)
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
                    } catch (e: Exception) {
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
                } catch (e: Exception) {
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
            NotificationManagerPlatformSupport.init(appContext)
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
    ) {
        logD("deleteNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        if (channelId.isNullOrEmpty()) {
            return
        }
        if (packageName == appContext.packageName) {
            notificationManager.deleteNotificationChannel(channelId)
            return
        }
        if (shouldUseModernIdentityStrategy(packageName)) {
            // Prefer deleting under the target package identity when possible.
            if (NotificationIdentityBridge.deleteTargetNotificationChannel(appContext, packageName, channelId)) {
                return
            }
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.deleteNotificationChannel(channelId)
                    // Same no-op risk as createPackageContext; only stop if channel is gone.
                    if (getNotificationChannel(packageName, channelId) == null) {
                        return
                    }
                } catch (e: Exception) {
                    logE("Failed to delete channel via package context for $packageName/$channelId", e)
                }
            }
            // Only fall back to local XMSF NM for MiPush-managed channels that may live here.
            if (io.github.magisk317.mipush.common.utils.NotificationUtils.isMiPushManagedChannelId(packageName, channelId)) {
                notificationManager.deleteNotificationChannel(channelId)
            } else {
                maybeLogDiagnosticsOnce("target-channel-delete-unsupported", packageName, channelId, null)
            }
            return
        }
        if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    packageNotificationManager.deleteNotificationChannel(channelId)
                    if (getNotificationChannel(packageName, channelId) == null) {
                        return
                    }
                } catch (e: Exception) {
                    logE("Failed to delete channel via package context for $packageName/$channelId", e)
                }
            }
        }
        if (io.github.magisk317.mipush.common.utils.NotificationUtils.isMiPushManagedChannelId(packageName, channelId) ||
            packageName == appContext.packageName
        ) {
            notificationManager.deleteNotificationChannel(channelId)
        }
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
            if (NotificationIdentityBridge.createTargetNotificationChannelGroups(appContext, packageName, nonNullGroups)) {
                if (!isHooked) {
                    createLocalNotificationChannelGroups(nonNullGroups)
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
                createLocalNotificationChannelGroups(nonNullGroups)
            }
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
        if (packageName == appContext.packageName ||
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
        if (packageName == appContext.packageName) {
            return notificationManager.notificationChannelGroups
        }
        if (shouldUseModernIdentityStrategy(packageName)) {
            val targetGroups = NotificationIdentityBridge.getTargetNotificationChannelGroups(appContext, packageName)
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
                    } catch (e: Exception) {
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
                } catch (e: Exception) {
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
            NotificationManagerPlatformSupport.init(appContext)
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
        } catch (_: Exception) {
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
            return filterLocalActiveNotifications(packageName, notificationManager.getActiveNotifications())
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

    /**
     * Triggers SystemUI to re-render notification icons by posting and immediately
     * cancelling a dummy notification. This forces the status bar to refresh without
     * needing to re-post existing notifications (which fails because the system strips
     * MIUI-specific fields from notification objects read via getActiveNotifications).
     */
    fun triggerStatusBarRefresh() {
        runCatching {
            val dummyId = Int.MIN_VALUE + 1
            val builder = android.app.Notification.Builder(appContext, "xmsf_trigger")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("")
                .setWhen(0)
            val channel = android.app.NotificationChannel(
                "xmsf_trigger", "trigger", NotificationManager.IMPORTANCE_MIN
            )
            notificationManager.createNotificationChannels(listOf(channel))
            notificationManager.notify(null, dummyId, builder.build())
            notificationManager.cancel(null, dummyId)
            notificationManager.deleteNotificationChannel("xmsf_trigger")
        }.onFailure {
            logE("triggerStatusBarRefresh failed", it)
        }
    }

}
