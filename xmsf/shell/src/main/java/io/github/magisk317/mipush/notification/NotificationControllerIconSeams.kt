package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.magisk317.mipush.common.notification.iconpack.ICON_PACK_SOURCE_IDENTITY_EXTRA
import io.github.magisk317.mipush.common.notification.iconpack.IconPackResolver
import io.github.magisk317.mipush.common.notification.iconpack.ResolveFailure
import io.github.magisk317.mipush.common.notification.iconpack.ResolveResult
import io.github.magisk317.mipush.common.notification.iconpack.thirdPartyPackSourceIdentity
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.common.utils.ImgUtils

/**
 * Icon-pipeline seams of [NotificationController], split out of the facade
 * object so the controller keeps only the publish/notify/cancel flow.
 *
 * Same-package extension functions: call sites keep the plain
 * `NotificationController.foo(...)` syntax (an import is required outside
 * this package). The icon-pack resolver and the mipush icon resource names
 * moved here because only this pipeline touches them.
 */

private val iconPackResolver = IconPackResolver()

private const val NOTIFICATION_LARGE_ICON = "mipush_notification"
private const val NOTIFICATION_SMALL_ICON = "mipush_small_notification"

fun NotificationController.getLargeIcon(context: Context, metaInfo: PushMetaInfo, iconUri: String?): Bitmap? =
    NotificationLargeIconSupport.getLargeIcon(context, metaInfo, iconUri)

internal fun NotificationController.clearIconPackCache() {
    iconPackResolver.clearCache()
}

fun NotificationController.roundLargeIconIfConfigured(metaInfo: PushMetaInfo, largeIcon: Bitmap): Bitmap =
    NotificationLargeIconSupport.roundLargeIconIfConfigured(metaInfo, largeIcon)

fun NotificationController.getBitmapFromUri(context: Context, iconUri: String?, maxDownloadBytes: Int): Bitmap? =
    NotificationLargeIconSupport.getBitmapFromUri(context, iconUri, maxDownloadBytes)

internal fun Notification.isGroupSummary(): Boolean {
    return flags and Notification.FLAG_GROUP_SUMMARY != 0
}

internal fun Notification.getLargeIconCompat(): Icon? {
    return runCatching { getLargeIcon() }.getOrNull()
}

fun NotificationController.getIconColor(ctx: Context, pkg: String): Int {
    return NotificationIconRenderingSupport.getIconColor(ctx, pkg)
}

fun NotificationController.processIcon(context: Context, packageName: String, notificationBuilder: NotificationCompat.Builder): Int {
    return NotificationIconRenderingSupport.processIcon(context, packageName, notificationBuilder)
}

internal fun NotificationController.applyStatusBarIcon(
    context: Context,
    packageName: String,
    notificationBuilder: NotificationCompat.Builder,
    colorStatusBarIcon: Boolean,
): Int {
    // Monochrome mode: the ANIP icon-pack bitmap (already single-color) is applied as-is;
    // when the pack has no entry the app's original smallIcon is kept and the system
    // monochrome tint handles the status bar. [旧单色处理] The white-alpha silhouette
    // pipeline (HyperOS SRC_IN cannot desaturate multi-color pixels) is retired below.
    val color = if (colorStatusBarIcon) {
        processIcon(context, packageName, notificationBuilder)
    } else {
        processMonochromeStatusBarIcon(context, packageName, notificationBuilder)
    }
    if (colorStatusBarIcon) {
        notificationBuilder.setColor(color)
        return color
    }
    notificationBuilder.setColor(Notification.COLOR_DEFAULT)
    return Notification.COLOR_DEFAULT
}

@SuppressLint("RestrictedApi")
internal fun NotificationController.applyIconPackSmallIcon(
    context: Context,
    targetPackage: String,
    notificationBuilder: NotificationCompat.Builder,
    colorStatusBarIcon: Boolean,
    userId: Int? = null,
    resolver: IconPackResolver = iconPackResolver,
): ResolveResult? {
    if (targetPackage.isBlank()) return null

    // The configured directory is the existing product icon-pack source. ConfigCenter loads
    // <tree>/icon/*.json into IconConfigurations; use it before the optional protocol seam.
    // A non-null, enabled, decodable entry is the concrete "path exists" signal.
    val configuredIcon = runCatching {
        Global.iconConfigurations().get(targetPackage)
    }.getOrNull()
    val configuredBitmap = configuredIcon?.bitmap()
    if (configuredIcon?.isEnabled == true && configuredBitmap != null && !configuredBitmap.isRecycled) {
        runCatching {
            notificationBuilder.setSmallIcon(
                IconCompat.createFromIcon(Icon.createWithBitmap(configuredBitmap)),
            )
            notificationBuilder.addExtras(
                Bundle().apply {
                    putString(
                        ICON_PACK_SOURCE_IDENTITY_EXTRA,
                        thirdPartyPackSourceIdentity(targetPackage),
                    )
                },
            )
            if (colorStatusBarIcon) {
                val iconColor = configuredIcon.color()
                if (iconColor != NotificationCompat.COLOR_DEFAULT) {
                    notificationBuilder.setColor(iconColor)
                }
            }
            logI("applied configured icon pack target=$targetPackage")
        }.onFailure {
            logW("failed to apply configured icon pack target=$targetPackage", it)
        }.getOrNull()?.let { return null }
    }

    val result = runCatching {
        resolver.resolve(targetPackage, userId, context)
    }.getOrNull() ?: return null
    // Do not let a reused builder carry a prior notification's source identity into a
    // blocked/failed resolution.
    notificationBuilder.addExtras(Bundle().apply { putString(ICON_PACK_SOURCE_IDENTITY_EXTRA, null) })
    if (result is ResolveResult.Available) {
        // NotificationCompat carries this as IconCompat, but the framework Notification gets
        // the exact platform Icon.createWithBitmap source. No second bitmap is synthesized.
        val applied = runCatching {
            notificationBuilder.setSmallIcon(
                IconCompat.createFromIcon(Icon.createWithBitmap(result.value.bitmap)),
            )
            // The marker audits the source identity; the bitmap remains on the framework
            // Notification.smallIcon and is the only header transport used by SystemUI.
            notificationBuilder.addExtras(
                Bundle().apply {
                    putString(ICON_PACK_SOURCE_IDENTITY_EXTRA, result.value.sourceIdentity)
                },
            )
            // iconColor is only the existing notification color metadata. Its absence never
            // invalidates the bitmap, and monochrome mode keeps its established default color.
            val iconColor = result.value.iconColor
            if (colorStatusBarIcon && iconColor != null) {
                notificationBuilder.setColor(iconColor)
            }
            true
        }.getOrDefault(false)
        if (!applied) {
            notificationBuilder.addExtras(Bundle().apply {
                putString(ICON_PACK_SOURCE_IDENTITY_EXTRA, null)
            })
            return ResolveResult.Unavailable(ResolveFailure.LOAD_FAILED)
        }
    }
    return result
}

/**
 * Monochrome mode: apply the (already single-color) ANIP icon-pack bitmap as-is; when the
 * pack has no entry, keep the app's original small icon and let the system monochrome tint
 * handle the status bar. Returns the brand color; monochrome callers discard it via
 * [applyStatusBarIcon].
 */
internal fun NotificationController.processMonochromeStatusBarIcon(
    context: Context,
    packageName: String,
    notificationBuilder: NotificationCompat.Builder,
): Int {
    val color = getIconColor(context, packageName)
    val pkgContext = XMPushUtils.getPackageContext(
        context,
        packageName,
        Context.CONTEXT_IGNORE_SECURITY,
    )
    // Shade avatars stay on largeIcon.
    if (pkgContext !== context) {
        val largeIconId = getIconId(context, packageName, NOTIFICATION_LARGE_ICON)
        if (largeIconId > 0) {
            notificationBuilder.setLargeIcon(
                BitmapFactory.decodeResource(pkgContext.resources, largeIconId),
            )
        }
    }
    // --- ANIP / configured icon takes top priority in monochrome mode ---
    // The ANIP icon library is single-color by design, so the bitmap is applied as-is:
    // 打开单色 = 使用图标包,不再做二次单色加工。
    val iconConfig = runCatching { Global.iconConfigurations().get(packageName) }.getOrNull()
    val configuredBitmap = iconConfig?.bitmap()
    if (iconConfig?.isEnabled == true && configuredBitmap != null && !configuredBitmap.isRecycled) {
        // [旧单色处理,已停用保留] The transparent-and-white rework dates from the colorful
        // icon-pack era; ANIP bitmaps are already monochrome and must not be reworked.
        // val monoBitmap = runCatching { ImgUtils.convertToTransparentAndWhite(configuredBitmap) }
        //     .onFailure {
        //         Logger.withTag(TAG).w(it) {
        //             "processMonochromeStatusBarIcon: ANIP rework failed for $packageName, using raw bitmap"
        //         }
        //     }
        //     .getOrNull()
        //     ?.takeIf { it.width > 0 && it.height > 0 }
        //     ?: configuredBitmap
        // notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(monoBitmap))
        notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(configuredBitmap))
        logI("processMonochromeStatusBarIcon: applied ANIP/configured icon for $packageName")
        return color
    }
    // [旧单色处理,已停用保留] White-alpha silhouettes from the launcher icon date from the
    // colorful icon-pack era. 单色模式下图标包未收录时不再生成白色剪影,保留应用原始
    // smallIcon 交给系统单色 tint 着色。
    // Prefer package white-alpha silhouette. Never seed Material bell first — that seed became
    // the status-bar icon for WeWork/xinyi when IconCache missed.
    // val whiteStatusBarIcon = Global.iconCache().getIconCache(
    //     context,
    //     packageName,
    //     object : io.github.magisk317.mipush.common.cache.IconCache.Converter<Bitmap, IconCompat> {
    //         override fun convert(ctx: Context, b: Bitmap): IconCompat = IconCompat.createWithBitmap(b)
    //     },
    // )
    // if (whiteStatusBarIcon != null) {
    //     notificationBuilder.setSmallIcon(whiteStatusBarIcon)
    //     return color
    // }
    // val rawIcon = Global.iconCache().getRawIconBitmap(context, packageName)
    // if (rawIcon != null) {
    //     val white = ImgUtils.convertToTransparentAndWhite(rawIcon)
    //     notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(white))
    //     return color
    // }
    // Icon pack miss: keep the app's original small icon (mipush resources — proper white
    // glyphs) so SystemUI applies its own monochrome tint. The flattened launcher icon is
    // NOT a glyph: fully opaque, the tint renders it as a solid white square (WorkBuddy),
    // so it must be reworked into a silhouette before handoff.
    if (pkgContext !== context) {
        val smallIconId = getIconId(context, packageName, NOTIFICATION_SMALL_ICON)
        if (smallIconId > 0) {
            logI("processMonochromeStatusBarIcon: applied mipush_small_notification resource for $packageName")
            notificationBuilder.setSmallIcon(IconCompat.createWithResource(pkgContext, smallIconId))
            return color
        }
        val largeIconId = getIconId(context, packageName, NOTIFICATION_LARGE_ICON)
        if (largeIconId > 0) {
            logI("processMonochromeStatusBarIcon: applied mipush_notification resource as small icon for $packageName")
            notificationBuilder.setSmallIcon(IconCompat.createWithResource(pkgContext, largeIconId))
            return color
        }
    }
    Global.iconCache().getRawIconBitmap(context, packageName)?.let { originalIcon ->
        val silhouette = runCatching { createMonochromeSilhouette(context, packageName, originalIcon) }
            .onFailure {
                logW("processMonochromeStatusBarIcon: silhouette rework failed for $packageName", it)
            }
            .getOrNull()
            ?.takeIf { it.width > 0 && it.height > 0 }
        if (silhouette == null) {
            // 同彩色模式:完全听系统。stock 链(normalStyleNotificationBuilder)已设置
            // mipush/applicationInfo.icon 资源图标,保留原样交系统渲染,不再自定义兜底。
            logW("processMonochromeStatusBarIcon: launcher silhouette unavailable for $packageName, deferring to system smallIcon")
            return color
        }
        logI("processMonochromeStatusBarIcon: applied launcher-icon silhouette for $packageName")
        notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(silhouette))
        return color
    }
    // 无任何本地图标来源:保留 stock 链的 smallIcon,交系统渲染。
    logW("processMonochromeStatusBarIcon: no icon source for $packageName, deferring to system smallIcon")
    return color
}

/**
 * Build a white-alpha silhouette for the system monochrome tint from the launcher icon.
 * Source priority: AdaptiveIconDrawable monochrome layer (API 33+) > foreground layer >
 * the flattened launcher bitmap. The flattened bitmap alone is fully opaque — handing it
 * to the tint renders a solid white square, so a shaped source is always preferred.
 */
internal fun NotificationController.createMonochromeSilhouette(
    context: Context,
    packageName: String,
    flattened: Bitmap,
): Bitmap? {
    val appIcon = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0).loadIcon(context.packageManager)
    }.getOrNull()
    val adaptiveLayer = (appIcon as? AdaptiveIconDrawable)?.let { adaptive ->
        when {
            Build.VERSION.SDK_INT >= 33 -> adaptive.monochrome ?: adaptive.foreground
            else -> adaptive.foreground
        }
    }
    val source = adaptiveLayer
        ?.takeIf { it.intrinsicWidth > 0 && it.intrinsicHeight > 0 }
        ?.let { ImgUtils.drawableToBitmap(it) }
        ?: flattened
    return ImgUtils.convertToTransparentAndWhite(source)
}

internal fun NotificationController.getIconId(context: Context, packageName: String, resourceName: String): Int =
    context.resources.getIdentifier(resourceName, "drawable", packageName)
