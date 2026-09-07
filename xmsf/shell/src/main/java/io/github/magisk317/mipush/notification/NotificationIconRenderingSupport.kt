package io.github.magisk317.mipush.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.UserHandle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import io.github.magisk317.mipush.common.R as CommonR
import io.github.magisk317.mipush.common.utils.ImgUtils
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.utils.ColorUtil

/** Keeps package/icon resolution out of the notification publication coordinator. */
internal object NotificationIconRenderingSupport {
    private const val TAG = "NotificationIconRendering"
    private const val NOTIFICATION_LARGE_ICON = "mipush_notification"
    private const val NOTIFICATION_SMALL_ICON = "mipush_small_notification"
    private const val EXTRA_LARGE_ICON = "android.largeIcon"
    private const val EXTRA_MIUI_APP_ICON = "miui.appIcon"
    private const val EXTRA_MIUI_OP_PKG = "miui.opPkg"

    fun getIconColor(context: Context, packageName: String): Int = Global.iconCache().getAppColor(
        context,
        packageName,
        object : io.github.magisk317.mipush.common.cache.IconCache.Converter<Bitmap, Int> {
            override fun convert(ctx: Context, b: Bitmap): Int {
                val color = ColorUtil.getIconColor(b)
                if (color == Notification.COLOR_DEFAULT) return Notification.COLOR_DEFAULT
                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(color, hsl)
                hsl[1] = 0.94f
                hsl[2] = minOf(hsl[2] * 0.6f, 0.31f)
                return ColorUtils.HSLToColor(hsl)
            }
        },
    )

    fun processIcon(
        context: Context,
        packageName: String,
        notificationBuilder: NotificationCompat.Builder,
    ): Int {
        var color = getIconColor(context, packageName)
        notificationBuilder.setSmallIcon(CommonR.drawable.ic_notifications_black_24dp)
        val packageContext = XMPushUtils.getPackageContext(context, packageName, Context.CONTEXT_IGNORE_SECURITY)
        if (packageContext === context) {
            setAppIconSmallIcon(context, packageName, notificationBuilder)
            return color
        }
        val largeIconId = getIconId(context, packageName, NOTIFICATION_LARGE_ICON)
        val smallIconId = getIconId(context, packageName, NOTIFICATION_SMALL_ICON)
        if (largeIconId > 0) {
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(packageContext.resources, largeIconId))
        }
        notificationBuilder.setColor(color)

        val iconConfig = Global.iconConfigurations().get(packageName)
        if (iconConfig != null && iconConfig.isEnabled == true && iconConfig.isEnabledAll == true) {
            iconConfig.bitmap()?.let {
                notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(it))
                color = iconConfig.color()
                notificationBuilder.setColor(color)
                return color
            }
        }
        if (smallIconId > 0) {
            notificationBuilder.setSmallIcon(IconCompat.createWithResource(packageContext, smallIconId))
            return color
        }
        if (largeIconId > 0) {
            notificationBuilder.setSmallIcon(IconCompat.createWithResource(packageContext, largeIconId))
            return color
        }
        val configuredBitmap = iconConfig?.bitmap()
        if (configuredBitmap != null && iconConfig.isEnabled == true) {
            notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(configuredBitmap))
            color = iconConfig.color()
            notificationBuilder.setColor(color)
            return color
        }
        Global.iconCache().getIconCache(
            context,
            packageName,
            object : io.github.magisk317.mipush.common.cache.IconCache.Converter<Bitmap, IconCompat> {
                override fun convert(ctx: Context, b: Bitmap): IconCompat = IconCompat.createWithBitmap(b)
            },
        )?.let {
            notificationBuilder.setSmallIcon(it)
            return color
        }
        setAppIconSmallIcon(context, packageName, notificationBuilder)
        return color
    }

    /**
     * Inject target app icon into notification extras so that the system UI displays the
     * app's own icon instead of the XMSF hosting-process icon.
     *
     * On non-MIUI systems (Samsung, AOSP, etc.) where notifications are posted locally as
     * com.xiaomi.xmsf, the system uses the posting package icon in the notification header.
     * MIUI-style extras (miui.appIcon / miui.opPkg) and the mSmallIcon field override allow
     * the correct target-app icon to surface.
     *
     * Called from [NotificationManagerEx.notifyDetailed] when falling back to local posting,
     * and from [NotificationHookBackend.notify] on the hook path.
     */
    @SuppressLint("DiscouragedPrivateApi")
    fun injectTargetAppIcons(
        context: Context,
        packageName: String,
        notification: Notification,
        colorStatusBarIcon: Boolean,
    ) {
        logD("injectTargetAppIcons pkg=$packageName colorStatusBarIcon=$colorStatusBarIcon")
        runCatching {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            if (appInfo.icon == 0) return

            val appIconBitmap = createAppIconBitmap(pm, appInfo)
            if (appIconBitmap != null) {
                val extras = ensureExtras(notification) ?: return
                extras.putParcelable(EXTRA_MIUI_APP_ICON, Icon.createWithBitmap(appIconBitmap))
                extras.putString(EXTRA_MIUI_OP_PKG, XMSF_PACKAGE_NAME)
                logD("Successfully injected MIUI custom app icon extras")
            }

            // In monochrome mode only publish the custom header source;
            // keep Notification.smallIcon for the status-bar monochrome hook.
            if (!colorStatusBarIcon) {
                logD("Kept original smallIcon and retained MIUI custom app icon extras")
                return
            }

            val fieldSmallIcon = Notification::class.java.getDeclaredField("mSmallIcon")
            fieldSmallIcon.isAccessible = true

            val badgedBitmap = createUserBadgedAppIconBitmap(pm, appInfo)
            if (badgedBitmap != null) {
                fieldSmallIcon.set(notification, Icon.createWithBitmap(badgedBitmap))
                logD("Successfully injected mSmallIcon with user-badged app icon")
                if (!hasLargeIcon(notification)) {
                    @Suppress("DEPRECATION")
                    notification.largeIcon = badgedBitmap
                    notification.extras?.putParcelable(EXTRA_LARGE_ICON, badgedBitmap)
                    logD("Successfully injected fallback largeIcon with user-badged app icon")
                }
            } else {
                fieldSmallIcon.set(notification, Icon.createWithResource(packageName, appInfo.icon))
                logD("Successfully injected mSmallIcon with app launcher icon")
            }
        }.onFailure {
            logE("Failed to inject target app icons", it)
        }
    }

    private fun getIconId(context: Context, packageName: String, resourceName: String): Int =
        context.resources.getIdentifier(resourceName, "drawable", packageName)

    private fun setAppIconSmallIcon(
        context: Context,
        packageName: String,
        notificationBuilder: NotificationCompat.Builder,
    ): Boolean {
        val bitmap = Global.iconCache().getRawIconBitmap(context, packageName) ?: return false
        notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(bitmap))
        return true
    }

    private fun createAppIconBitmap(
        packageManager: PackageManager,
        appInfo: android.content.pm.ApplicationInfo,
    ): Bitmap? {
        return runCatching {
            ImgUtils.drawableToBitmap(appInfo.loadIcon(packageManager))
        }.onFailure {
            logE("Failed to create app icon bitmap", it)
        }.getOrNull()
    }

    private fun createUserBadgedAppIconBitmap(
        packageManager: PackageManager,
        appInfo: android.content.pm.ApplicationInfo,
    ): Bitmap? {
        return runCatching {
            val rawIcon = appInfo.loadIcon(packageManager)
            val userHandle = resolveUserHandle()
            val iconForUser = if (userHandle != null) {
                packageManager.getUserBadgedIcon(rawIcon, userHandle)
            } else {
                rawIcon
            }
            ImgUtils.drawableToBitmap(iconForUser)
        }.onFailure {
            logE("Failed to create user-badged app icon", it)
        }.getOrNull()
    }

    private fun resolveUserHandle(): UserHandle? {
        return runCatching {
            val method = UserHandle::class.java.getDeclaredMethod("of", Integer.TYPE)
            method.isAccessible = true
            method.invoke(null, android.os.Process.myUserHandle().hashCode()) as UserHandle
        }.getOrNull()
    }

    private fun hasLargeIcon(notification: Notification): Boolean {
        val reflectedLargeIcon = runCatching { notification.getLargeIcon() }.getOrNull()
        if (reflectedLargeIcon != null) return true
        return notification.extras?.containsKey(EXTRA_LARGE_ICON) == true
    }

    private fun ensureExtras(notification: Notification): Bundle? {
        notification.extras?.let { return it }
        return runCatching {
            val extras = Bundle()
            val field = Notification::class.java.getDeclaredField("extras")
            field.isAccessible = true
            field.set(notification, extras)
            extras
        }.onFailure {
            logE("Failed to create extras bundle", it)
        }.getOrNull()
    }

    private fun logD(message: String) =
        co.touchlab.kermit.Logger.withTag(TAG).d { message }

    private fun logE(message: String, throwable: Throwable? = null) {
        if (throwable == null) co.touchlab.kermit.Logger.withTag(TAG).e { message }
        else co.touchlab.kermit.Logger.withTag(TAG).e(throwable) { message }
    }

    private fun logW(message: String) =
        co.touchlab.kermit.Logger.withTag(TAG).w { message }
}
