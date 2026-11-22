@file:SuppressLint("PrivateApi")

package io.github.magisk317.mipush.hook.systemui

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.common.notification.StatusBarMonochromeIconPolicy
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.getHookObjectField

// Hidden framework flag: Notification.FLAG_CAN_COLORIZE == 0x00000800.
private const val FLAG_CAN_COLORIZE = 0x00000800

internal fun statusBarNotificationFromEntry(entry: Any?): StatusBarNotification? {
        if (entry == null) return null
        return runCatching {
            getHookObjectField(entry, "mSbn") as? StatusBarNotification
        }.getOrNull() ?: runCatching {
            entry.callMethod("getSbn") as? StatusBarNotification
        }.getOrNull()
    }

    /**
     * Cache-only icon-pack hit probe for hot paths (substitution blocking). Reads the same
     * cache the descriptor hook populates; a MISS schedules a background prefetch and reports
     * false until the next icon rebuild.
     */
internal fun iconPackHitForStatus(
        context: Context,
        owner: String,
        postedPackage: String,
        userId: Int,
    ): Boolean {
        if (owner.isBlank() || userId < 0) return false
        if (StatusBarMonochromeIconPolicy.preferredMonochromeIconForPackageOrNull(
                context, owner, userId,
            ) != null) {
            return true
        }
        return owner != postedPackage &&
            StatusBarMonochromeIconPolicy.preferredMonochromeIconForPackageOrNull(
                context, postedPackage, userId,
            ) != null
    }

    /**
     * Extracts (type, resId, resPackage) from an [Icon] for the resource-loadability guard.
     *
     * `getType()` is public API; `getResId()`/`getResPackage()` are hidden, so they are read
     * reflectively. A null icon or any extraction failure yields a non-RESOURCE type marker so
     * the guard treats it as loadable and leaves the base intercept decision untouched.
     */
internal fun readIconResourceFields(icon: Icon?): Triple<Int, Int, String?> =
        PostedGlyphMonochromeProbe.resourceFields(icon)

    /**
     * Resolves the posted small icon's resource name against the package that owns it.
     *
     * SystemUI's own resources reinterpret a foreign resId as a SystemUI id and return an
     * unrelated name (a "$ic_device_camera_on_anim__0" style anim), which made the descriptor
     * log impossible to act on during triage. Foreign ids resolve through a package context.
     */
internal fun smallIconResourceName(
        context: Context,
        iconType: Int,
        resId: Int,
        ownerPackage: String?,
    ): String {
        val hexId = "0x${Integer.toHexString(resId)}"
        if (iconType != SystemUiNotificationPolicy.ICON_TYPE_RESOURCE || resId == 0) return hexId
        return runCatching {
            val resources = if (ownerPackage.isNullOrBlank() || ownerPackage == context.packageName) {
                context.resources
            } else {
                context.createPackageContext(ownerPackage, 0).resources
            }
            resources.getResourceName(resId)
        }.getOrNull() ?: hexId
    }

internal fun Notification.canColorize(): Boolean = flags and FLAG_CAN_COLORIZE != 0

internal fun notificationUserId(sbn: StatusBarNotification): Int? = runCatching {
        sbn.callMethod("getUserId") as? Int
    }.getOrNull()?.takeIf { it >= 0 }
    /**
     * True when [icon] is already a monochrome status glyph (or detection failed).
     * Fail-open to true so we keep the posted RESOURCE instead of swapping in a launcher
     * white square (SMS regression). Multi-color logos still get package silhouettes when
     * ContrastColorUtil reports false.
     */
    @SuppressLint("PrivateApi")
internal fun isGrayscaleSmallIcon(context: Context, icon: Icon?): Boolean =
        PostedGlyphMonochromeProbe.isGrayscale(context, icon)

    /**
     * Strict variant for the tint decision: only a positive grayscale verdict proves the glyph
     * survives a white SRC_IN tint. Detection failures return false so the icon keeps the
     * system's native rendering instead of being force-tinted into a white block.
     */
    @SuppressLint("PrivateApi")
internal fun isPositivelyGrayscaleSmallIcon(context: Context, icon: Icon?): Boolean =
        PostedGlyphMonochromeProbe.isPositivelyGrayscale(context, icon)
internal fun isSystemApplication(context: Context?, packageName: String?): Boolean {
        if (context == null || packageName.isNullOrBlank()) return false
        return runCatching {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    android.content.pm.PackageManager.ApplicationInfoFlags.of(0),
                ).flags
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0).flags
            }
            flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.getOrDefault(false)
    }
