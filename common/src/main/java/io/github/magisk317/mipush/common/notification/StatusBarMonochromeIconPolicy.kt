package io.github.magisk317.mipush.common.notification

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import io.github.magisk317.mipush.common.utils.ImgUtils
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Rewrite notification smallIcon to a white-alpha package silhouette.
 *
 * Must run at post/enqueue time (xmsf or NMS), never on SystemUI getSmallIcon hot path.
 * Global monochrome mode applies to every non-island app; color mode is a no-op.
 */
object StatusBarMonochromeIconPolicy {
    private const val EXTRA_MIUI_APP_ICON = "miui.appIcon"
    private const val EXTRA_MIUI_OP_PKG = "miui.opPkg"
    private const val MAX_CACHE = 48

    private val whiteIconCache: MutableMap<String, Icon> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Icon>(MAX_CACHE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Icon>?): Boolean {
                return size > MAX_CACHE
            }
        },
    )

    @JvmStatic
    fun shouldRewrite(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        extras: Bundle?,
    ): Boolean {
        if (colorStatusBarIcon) return false
        if (!forceGlobalStatusBarIcons) return false
        if (SinglePackageNotificationGroupPolicy.isIslandProxy(extras)) return false
        return true
    }

    @JvmStatic
    fun apply(context: Context, postingPackage: String, notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (postingPackage.isBlank()) return false
        val owner = SinglePackageNotificationGroupPolicy.resolveGroupOwnerPackage(
            postingPackage,
            notification.extras,
        )
        if (owner.isBlank()) return false
        val white = whiteIconForPackage(context, owner) ?: return false
        // Same Notification instance often re-enters Binder + enqueueNotificationInternal.
        // Keep the second path a pure no-op to avoid hot-path work and log spam.
        if (getSmallIcon(notification) === white) {
            notification.extras?.remove(EXTRA_MIUI_APP_ICON)
            notification.extras?.remove(EXTRA_MIUI_OP_PKG)
            return false
        }
        if (!setSmallIcon(notification, white)) return false
        runCatching { notification.color = Notification.COLOR_DEFAULT }
        notification.extras?.remove(EXTRA_MIUI_APP_ICON)
        notification.extras?.remove(EXTRA_MIUI_OP_PKG)
        return true
    }

    @JvmStatic
    fun applyToNmsEnqueueArgs(
        context: Context,
        args: Array<Any?>,
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
    ): Boolean {
        val index = args.indexOfFirst { it is Notification }
        if (index < 0) return false
        val notification = args[index] as Notification
        if (!shouldRewrite(colorStatusBarIcon, forceGlobalStatusBarIcons, notification.extras)) {
            return false
        }
        val postingPackage = args.firstOrNull { it is String && (it as String).isNotBlank() } as? String
            ?: return false
        return apply(context, postingPackage, notification)
    }

    @JvmStatic
    fun clearCacheForTest() {
        whiteIconCache.clear()
    }

    /**
     * White-alpha package silhouette for status-bar use. Safe from SystemUI hot path when the
     * posted smallIcon is unusable (RESOURCE resId=0 AUTOGROUP summaries).
     */
    @JvmStatic
    fun whiteIconForPackageOrNull(context: Context, packageName: String): Icon? {
        if (packageName.isBlank()) return null
        return whiteIconForPackage(context, packageName)
    }

    private fun whiteIconForPackage(context: Context, packageName: String): Icon? {
        whiteIconCache[packageName]?.let { return it }
        val created = runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val raw = ImgUtils.drawableToBitmap(drawable)
            val white = ImgUtils.convertToTransparentAndWhite(raw)
            Icon.createWithBitmap(white)
        }.getOrNull() ?: return null
        whiteIconCache[packageName] = created
        return created
    }

    private fun getSmallIcon(notification: Notification): Icon? {
        return runCatching {
            val field = Notification::class.java.getDeclaredField("mSmallIcon")
            field.isAccessible = true
            field.get(notification) as? Icon
        }.getOrNull()
    }

    private fun setSmallIcon(notification: Notification, icon: Icon): Boolean {
        return runCatching {
            val field = Notification::class.java.getDeclaredField("mSmallIcon")
            field.isAccessible = true
            field.set(notification, icon)
            true
        }.getOrDefault(false)
    }
}
