package io.github.magisk317.mipush.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import com.xiaomi.channel.commonutils.android.MIUIUtils
import io.github.magisk317.mipush.platform.support.NotificationVendorAdapter
import com.xiaomi.push.service.NotificationUtils
import io.github.magisk317.mipush.common.utils.logE

internal object NotificationLocalStateSupport {
    private const val EXTRA_XMSF_TARGET_PACKAGE = "xmsf_target_package"
    private const val EXTRA_MIUI_TARGET_PACKAGE = "miui.targetPkg"
    private const val EXTRA_SUBSTITUTE_APP_NAME = "android.substName"

    fun filterActive(
        packageName: String,
        activeNotifications: Array<StatusBarNotification>,
        userId: Int,
    ): Array<StatusBarNotification?> {
        if (userId < 0) return emptyArray()
        return NotificationVendorAdapter
            .filterLocalActiveNotifications(packageName, activeNotifications, userId)
            .map { it as StatusBarNotification? }
            .toTypedArray()
    }

    @Suppress("DEPRECATION")
    fun hasTarget(
        context: Context,
        notificationManager: NotificationManager,
        packageName: String,
        tag: String?,
        id: Int,
        userId: Int,
    ): Boolean {
        if (packageName == context.packageName) return false
        return runCatching {
            notificationManager.activeNotifications.any { sbn ->
                if (sbn.userId != userId || sbn.packageName != context.packageName ||
                    sbn.tag != tag || sbn.id != id
                ) return@any false
                val extras = sbn.notification.extras ?: return@any false
                sequenceOf(EXTRA_XMSF_TARGET_PACKAGE, EXTRA_MIUI_TARGET_PACKAGE, "target_package")
                    .any { extras.getString(it) == packageName }
            }
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    fun hasLocal(
        context: Context,
        notificationManager: NotificationManager,
        tag: String?,
        id: Int,
        userId: Int,
    ): Boolean = runCatching {
        notificationManager.activeNotifications.any { sbn ->
            sbn.userId == userId && sbn.packageName == context.packageName &&
                sbn.tag == tag && sbn.id == id
        }
    }.getOrDefault(false)

    fun markTarget(context: Context, packageName: String, notification: Notification) {
        if (packageName == context.packageName) return
        runCatching {
            notification.extras?.let { extras ->
                extras.putString(EXTRA_XMSF_TARGET_PACKAGE, packageName)
                extras.putString(EXTRA_MIUI_TARGET_PACKAGE, packageName)
                extras.putString("target_package", packageName)
                val appLabel = runCatching {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                }.getOrNull()?.takeIf(String::isNotBlank)
                if (appLabel != null) {
                    extras.putString(EXTRA_SUBSTITUTE_APP_NAME, appLabel)
                    extras.putString("android.substName", appLabel)
                }
            }
            if (!MIUIUtils.isXMS() && MIUIUtils.isXMSF(context)) {
                NotificationUtils.setTargetPackage(notification, packageName)
            }
        }.onFailure { logE("Failed to mark local target package for $packageName", it) }
    }
}
