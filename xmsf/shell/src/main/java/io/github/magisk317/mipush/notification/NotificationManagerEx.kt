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
import io.github.magisk317.mipush.platform.support.NotificationVendorAdapter
import io.github.magisk317.mipush.platform.support.XMPushUtils
import co.touchlab.kermit.Logger
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

    enum class NotifyOwner {
        TARGET,
        LOCAL_XMSF,
        NONE,
    }

    data class NotifyResult(
        val posted: Boolean,
        val owner: NotifyOwner,
        val reason: String,
    )

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
        val message = "$reason ${NotificationVendorAdapter.dumpIdentityDiagnostics(appContext, packageName, channelId, groupId)}"
        if (throwable != null) {
            logE(message, throwable)
        } else {
            logD(message)
        }
    }

    private fun filterLocalActiveNotifications(
        packageName: String,
        activeNotifications: Array<StatusBarNotification>,
        userId: Int,
    ): Array<StatusBarNotification?> =
        NotificationLocalStateSupport.filterActive(packageName, activeNotifications, userId)

    private fun hasLocalTargetNotification(
        packageName: String,
        tag: String?,
        id: Int,
        userId: Int,
    ): Boolean = ::appContext.isInitialized && NotificationLocalStateSupport.hasTarget(
        appContext, notificationManager, packageName, tag, id, userId,
    )

    private fun hasLocalNotification(tag: String?, id: Int, userId: Int): Boolean =
        ::appContext.isInitialized && NotificationLocalStateSupport.hasLocal(
            appContext, notificationManager, tag, id, userId,
        )

    private fun markLocalTargetPackage(packageName: String, notification: Notification) {
        if (::appContext.isInitialized) {
            NotificationLocalStateSupport.markTarget(appContext, packageName, notification)
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
            NotificationVendorAdapter.canCreateTargetChannels(appContext, packageName)
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
            NotificationVendorAdapter.getPreferredTargetChannel(appContext, packageName, preferredChannelId)
        } else {
            null
        }
    }

    private fun getNotificationManagerForPackage(packageName: String): NotificationManager? =
        channelRegistry().getNotificationManagerForPackage(packageName)

    private fun getDirectPackageNotificationChannel(
        packageName: String,
        channelId: String?,
    ): NotificationChannel? = channelRegistry().getDirectPackageNotificationChannel(packageName, channelId)

    private fun shouldNotifyAsPackage(
        packageName: String,
        notification: Notification
    ): Boolean {
        val channelId = notification.channelId
        if (shouldUseModernIdentityStrategy(packageName)) {
            val strategy = NotificationVendorAdapter.resolveIdentityStrategy(appContext, packageName)
            val compatAttempt = strategy == NotificationVendorAdapter.IdentityStrategy.UNSUPPORTED &&
                NotificationVendorAdapter.shouldAttemptCompatTargetPost(appContext, packageName)
            val visible = when (strategy) {
                NotificationVendorAdapter.IdentityStrategy.FRAMEWORK -> true
                NotificationVendorAdapter.IdentityStrategy.DELEGATED ->
                    isHooked || NotificationVendorAdapter.getTargetChannel(appContext, packageName, channelId) != null
                NotificationVendorAdapter.IdentityStrategy.UNSUPPORTED -> compatAttempt
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
        tag: String?, id: Int, notification: Notification,
        userId: Int = Utils.requireValidUserId(Utils.myUserId()),
    ): Boolean = notifyDetailed(packageName, tag, id, notification, userId).posted

    fun notifyDetailed(
        packageName: String,
        tag: String?, id: Int, notification: Notification,
        userId: Int = Utils.requireValidUserId(Utils.myUserId()),
    ): NotifyResult {
        // Fully replaced by HookPushNC when the Xposed module is active.
        Logger.withTag(TAG).d { "notify() called with: packageName = $packageName, tag = $tag, id = $id, channel = ${notification.channelId}, group = ${notification.group}" }
        val currentUserId = Utils.myUserId()
        if (!canNotifyForUser(userId, currentUserId)) {
            logW(
                "skip notification publish for foreign user=$userId currentUser=$currentUserId " +
                    "pkg=$packageName tag=$tag id=$id",
            )
            return NotifyResult(false, NotifyOwner.NONE, "foreign_user")
        }
        // Attribution marker: when isHooked is true the system NMS hook owns publishing and this
        // app-process body is normally bypassed. Seeing this line run with isHooked=true means the
        // hook did not intercept and we are about to publish as a local (non-owned) fallback.
        logD("notify() attribution pkg=$packageName isHooked=$isHooked id=$id channel=${notification.channelId}")
        if (!isTargetPackageAvailable(packageName)) {
            logD("drop notification for absent target package pkg=$packageName tag=$tag id=$id channel=${notification.channelId}")
            emitNotify(result = "skip", reason = "target_absent", packageName = packageName)
            return NotifyResult(false, NotifyOwner.NONE, "target_absent")
        }
        markLocalTargetPackage(packageName, notification)
        if (shouldUseModernIdentityStrategy(packageName)) {
            if (shouldNotifyAsPackage(packageName, notification)) {
                if (NotificationVendorAdapter.notifyAsTarget(appContext, packageName, tag, id, notification)) {
                    emitNotify(result = "ok", reason = "identity_target", packageName = packageName)
                    return NotifyResult(true, NotifyOwner.TARGET, "identity_target")
                }
                if (maybeRetryNotifyAsTargetAfterAppOpsGrant(packageName, tag, id, notification)) {
                    emitNotify(result = "ok", reason = "identity_retry", packageName = packageName)
                    return NotifyResult(true, NotifyOwner.TARGET, "identity_retry")
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
            return NotifyResult(local, if (local) NotifyOwner.LOCAL_XMSF else NotifyOwner.NONE, "local_fallback")
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
                return NotifyResult(true, NotifyOwner.TARGET, "notify_as_package")
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
        return NotifyResult(local, if (local) NotifyOwner.LOCAL_XMSF else NotifyOwner.NONE, "local")
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
            PermissionUtils.grantSilentPermissions(packageName = packageName)
        }.getOrDefault(false)
        if (!granted) {
            logD("live-update identity retry skipped: silent grant failed pkg=$packageName id=$id")
            return false
        }
        val ok = NotificationVendorAdapter.notifyAsTarget(appContext, packageName, tag, id, notification)
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

    internal fun cancelLocallySafely(
        tag: String?,
        id: Int,
        cancel: () -> Unit,
    ): Boolean = runCatching {
        cancel()
        true
    }.onFailure {
        // Notification ownership can change between activeNotifications and cancel().
        // A rejected cleanup must never take down the XMSF process.
        logW("Skipped local notification cancel after system rejection tag=$tag id=$id: ${it.message}")
    }.getOrDefault(false)

    fun cancel(
        packageName: String,
        tag: String?, id: Int,
        userId: Int = Utils.requireValidUserId(Utils.myUserId()),
    ) {
        // Fully replaced by HookPushNC when the Xposed module is active.
        Logger.withTag(TAG).d { "cancel() called with: packageName = $packageName, tag = $tag, id = $id" }
        val currentUserId = Utils.myUserId()
        if (!canCancelForUser(userId, currentUserId)) {
            logW(
                "skip notification cancel for foreign user=$userId currentUser=$currentUserId " +
                    "pkg=$packageName tag=$tag id=$id",
            )
            return
        }
        if (shouldUseModernIdentityStrategy(packageName)) {
            if (NotificationVendorAdapter.cancelAsTarget(appContext, packageName, tag, id)) {
                logD("cancel() completed via target identity pkg=$packageName tag=$tag id=$id")
                return
            }
            maybeLogDiagnosticsOnce("identity-cancel-fallback", packageName, null, null)
            if (hasLocalTargetNotification(packageName, tag, id, userId)) {
                val cancelled = cancelLocallySafely(tag, id) { notificationManager.cancel(tag, id) }
                logD("cancel() completed locally after identity fallback pkg=$packageName tag=$tag id=$id cancelled=$cancelled")
            } else {
                logW("skip local cancel for foreign target without matching local marker pkg=$packageName tag=$tag id=$id")
            }
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
        val canCancelLocal = if (packageName == appContext.packageName) {
            hasLocalNotification(tag, id, userId)
        } else {
            hasLocalTargetNotification(packageName, tag, id, userId)
        }
        if (canCancelLocal) {
            val cancelled = cancelLocallySafely(tag, id) { notificationManager.cancel(tag, id) }
            logD("cancel() local owner cleanup pkg=$packageName tag=$tag id=$id cancelled=$cancelled")
        }
        logD(
            "cancel() completed locally pkg=$packageName tag=$tag id=$id " +
                "targetCancelSucceeded=$targetCancelSucceeded"
        )
    }

    internal fun canCancelForUser(requestedUserId: Int, currentUserId: Int): Boolean =
        NotificationOwnershipPolicy.canAccessUser(requestedUserId, currentUserId)

    internal fun canNotifyForUser(requestedUserId: Int, currentUserId: Int): Boolean =
        NotificationOwnershipPolicy.canAccessUser(requestedUserId, currentUserId)

    private fun channelRegistry(): NotificationChannelRegistrySupport =
        NotificationChannelRegistrySupport(
            context = appContext,
            notificationManager = notificationManager,
            isHooked = isHooked,
            diagnostics = ::maybeLogDiagnosticsOnce,
        )

    fun createNotificationChannels(packageName: String, channels: List<NotificationChannel?>) =
        channelRegistry().createNotificationChannels(packageName, channels)

    fun getNotificationChannel(packageName: String, channelId: String?): NotificationChannel? =
        channelRegistry().getNotificationChannel(packageName, channelId)

    fun getNotificationChannels(packageName: String): List<NotificationChannel?>? =
        channelRegistry().getNotificationChannels(packageName)

    fun deleteNotificationChannel(packageName: String, channelId: String?): Boolean =
        channelRegistry().deleteNotificationChannel(packageName, channelId)

    fun createNotificationChannelGroups(packageName: String, groups: List<NotificationChannelGroup?>) =
        channelRegistry().createNotificationChannelGroups(packageName, groups)

    fun getNotificationChannelGroup(packageName: String, groupId: String?): NotificationChannelGroup? =
        channelRegistry().getNotificationChannelGroup(packageName, groupId)

    fun getNotificationChannelGroups(packageName: String): List<NotificationChannelGroup?>? =
        channelRegistry().getNotificationChannelGroups(packageName)

    fun deleteNotificationChannelGroup(packageName: String, groupId: String?) =
        channelRegistry().deleteNotificationChannelGroup(packageName, groupId)

    internal fun shouldUseLocalChannelFallback(
        packageName: String,
        channelId: String?,
        hostPackageName: String,
    ): Boolean = NotificationOwnershipPolicy.shouldUseLocalChannel(packageName, channelId, hostPackageName)

    internal fun shouldUseLocalGroupFallback(
        packageName: String,
        groupId: String?,
        hostPackageName: String,
    ): Boolean = NotificationOwnershipPolicy.shouldUseLocalGroup(packageName, groupId, hostPackageName)

    internal fun shouldUseLocalNotificationStateFallback(
        packageName: String,
        hostPackageName: String,
    ): Boolean = NotificationOwnershipPolicy.shouldUseLocalNotificationState(packageName, hostPackageName)

    fun areNotificationsEnabled(
        packageName: String
    ): Boolean {
        // Fully replaced by HookPushNC when the Xposed module is active.
        logD("areNotificationsEnabled() called with: packageName = $packageName")

        // 1. Check if the target app has notifications enabled in the system
        val systemEnabled = runCatching {
            getNotificationManagerForPackage(packageName)?.areNotificationsEnabled()
        }.getOrNull() ?: if (shouldUseLocalNotificationStateFallback(packageName, appContext.packageName)) {
            notificationManager.areNotificationsEnabled()
        } else {
            maybeLogDiagnosticsOnce("target-notification-state-unavailable", packageName, null, null)
            false
        }

        if (!systemEnabled) {
            logD("System notifications disabled for $packageName")
            return false
        }

        // 2. Check if identity strategy is supported for this package
        if (shouldUseModernIdentityStrategy(packageName)) {
            val strategy = NotificationVendorAdapter.resolveIdentityStrategy(appContext, packageName)
            val strategySupported = strategy != NotificationVendorAdapter.IdentityStrategy.UNSUPPORTED
            if (!strategySupported) {
                maybeLogDiagnosticsOnce("identity-unsupported", packageName, null, null)
            }
            return strategySupported
        }

        return true
    }

    @Suppress("DEPRECATION") // StatusBarNotification.userId is the only API available in the compile SDK.
    fun getActiveNotifications(
        packageName: String
    ): Array<StatusBarNotification?>? {
        logD("getActiveNotifications() called with: packageName = $packageName")
        val userId = Utils.myUserId()
        if (userId < 0) {
            logW("getActiveNotifications() rejected invalid current userId=$userId pkg=$packageName")
            return emptyArray()
        }
        if (shouldUseModernIdentityStrategy(packageName)) {
            // Framework/delegated identity posts are owned by the target package. Query that
            // package first; looking only at XMSF-local markers misses real ongoing notifications
            // and prevents lifecycle code from cancelling them on timeout.
            val targetActive = runCatching {
                NotificationVendorAdapter.getPlatformActiveNotifications(packageName, userId)
            }.onFailure {
                maybeLogDiagnosticsOnce("target-active-unavailable", packageName, null, null, it)
            }.getOrNull()
            if (!targetActive.isNullOrEmpty()) {
                return targetActive.map { it as StatusBarNotification? }.toTypedArray()
            }
            return filterLocalActiveNotifications(packageName, notificationManager.getActiveNotifications(), userId)
        } else if (!canUseLegacyPackageScopedApis()) {
            val packageNotificationManager = getNotificationManagerForPackage(packageName)
            if (packageNotificationManager != null && packageNotificationManager !== notificationManager) {
                try {
                    return packageNotificationManager.activeNotifications
                        .filter { it.userId == userId }
                        .toTypedArray()
                } catch (e: Exception) {
                    logE("Failed to query active notifications via package context for $packageName", e)
                }
            }
        }
        // Legacy package-scoped APIs are unavailable on these paths, so the host
        // NotificationManager can contain delegated records for several target apps.
        // Keep the same target marker fence used by the modern compatibility path.
        return filterLocalActiveNotifications(packageName, notificationManager.getActiveNotifications(), userId)
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
            cancelLocallySafely(null, dummyId) { notificationManager.cancel(null, dummyId) }
            notificationManager.deleteNotificationChannel("xmsf_trigger")
        }.onFailure {
            logE("triggerStatusBarRefresh failed", it)
        }
    }

}
