package io.github.magisk317.mipush.hook.systemui

import android.annotation.SuppressLint
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.MethodHookParam

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.common.island.IslandOptions
import android.view.View
import android.widget.ImageView
import java.util.Collections
import java.util.WeakHashMap
import io.github.magisk317.mipush.common.notification.NotificationOwnerResolver
import io.github.magisk317.mipush.common.notification.StatusBarMonochromeIconPolicy
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.mipush.hook.island.IslandDispatcher
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.get
import io.github.magisk317.xposed.getHookIntField
import io.github.magisk317.xposed.getHookObjectField
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.hookAllMethods
import io.github.magisk317.xposed.hookMethod
import io.github.magisk317.xposed.setHookObjectField

class HookSystemUI : BaseHook() {
    companion object {
        private const val TAG = "HookSystemUI"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        // Hidden framework flag: Notification.FLAG_CAN_COLORIZE == 0x00000800.
        private const val FLAG_CAN_COLORIZE = 0x00000800
    }

    private val idIconIsPreL: Int by lazy(::resolveIconIsPreL)

    /**
     * Descriptors (and their notification entries) whose icon this module replaced with a
     * monochrome glyph, keyed weakly so the entry dies with SystemUI's descriptor.
     *
     * IconManager.setIcon must only tint such a substituted glyph: tinting the app's own opaque
     * logo (icon-pack miss + no silhouette) is what produced the status-bar white blocks.
     */
    private val substitutedGlyphKeys: MutableMap<Any, Boolean> =
        Collections.synchronizedMap(WeakHashMap<Any, Boolean>())

    /**
     * Views whose descriptor keeps the app's own glyph. Their tint stays off so the system
     * renders the icon natively instead of forcing SRC_IN white over an opaque logo.
     */
    private val systemTintDeferredViews: MutableSet<View> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))

    @SuppressLint("DiscouragedApi")
    private fun resolveIconIsPreL(): Int {
        val app = currentApplication() ?: return 0
        return app.resources.getIdentifier("icon_is_pre_L", "id", app.packageName)
    }

    override fun onHotReloading() {
        // SystemUI owns the island dispatcher receiver. Drop it before hot reload so the old
        // module ClassLoader (and its DEX mappings) is not pinned by the registered receiver.
        IslandDispatcher.unregister()
    }

    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        val classLoader = param.classLoader
        XLog.i(TAG, "HookSystemUI.hook() called commit=${io.github.magisk317.mipush.common.BuildConfig.GIT_COMMIT}")
        MiuiHeaderAppIconHook().hook(classLoader)
        hookNativeCustomAppIcon(classLoader)
        hookAndroid17StatusBarIconDescriptor(classLoader)
        hookGlobalStatusBarIconTint(classLoader)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Always install the hook; check colorStatusBarIcon dynamically per-call
            // so toggling the setting takes effect without restarting SystemUI.
            try {
                val notifImageUtilClass =
                    classLoader.findClass("com.android.systemui.statusbar.notification.utils.NotifImageUtil")
                notifImageUtilClass.hookMethod(
                    "getSmallIcon",
                    Context::class.java,
                    StatusBarNotification::class.java,
                    Int::class.javaPrimitiveType!!,
                    Boolean::class.javaPrimitiveType!!,
                ) {
                    doBefore {
                        val options = IslandPreferences.current()
                        val context = args[0] as? Context ?: return@doBefore
                        val sbn = args[1] as? StatusBarNotification ?: return@doBefore
                        val notification = sbn.notification ?: return@doBefore
                        val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(
                            notification.extras
                        )
                        val smallIcon = notification.smallIcon
                        val (iconType, resId, resPackage) = readIconResourceFields(smallIcon)
                        val isSystemApp = isSystemApplication(context, sbn.packageName)
                        val canColorize = notification.canColorize()
                        val owner = NotificationOwnerResolver.resolve(
                            sbn.packageName,
                            notification.extras,
                        )
                        val userId = notificationUserId(sbn) ?: return@doBefore
                        fun monochromeFallback(): Icon? {
                            if (options.colorStatusBarIconGlobal) {
                                StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(
                                    context = context,
                                    packageName = owner,
                                    userId = userId,
                                )?.let {
                                    XLog.d(TAG, "status bar icon source=icon-pack owner=$owner")
                                    return it
                                }
                                if (owner != sbn.packageName) {
                                    StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(
                                        context = context,
                                        packageName = sbn.packageName,
                                        userId = userId,
                                    )?.let {
                                        XLog.d(TAG, "status bar icon source=icon-pack pkg=${sbn.packageName}")
                                        return it
                                    }
                                }
                                // The icon pack only covers configured apps and new apps keep
                                // shipping, so fall back to a white silhouette built from the
                                // app's shaped adaptive layer before giving up on the glyph.
                                StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(
                                    context = context,
                                    packageName = owner,
                                )?.let {
                                    XLog.d(TAG, "status bar icon source=silhouette owner=$owner")
                                    return it
                                }
                                if (owner != sbn.packageName) {
                                    StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(
                                        context = context,
                                        packageName = sbn.packageName,
                                    )?.let {
                                        XLog.d(TAG, "status bar icon source=silhouette pkg=${sbn.packageName}")
                                        return it
                                    }
                                }
                            }
                            // Last resort: keep the notification's own status glyph. It may be an
                            // opaque logo (or a resId=0 summary), so IconManager.setIcon drops the
                            // monochrome tint for it instead of painting a white block.
                            return notification.smallIcon
                        }
                        // [决策] 聚合/破损图标替换受单色开关门控:单色用图标包(单色)替换,
                        // 彩色用应用自身彩色图标替换(图标包不参与)。
                        fun colorfulFallback(): Icon? {
                            StatusBarMonochromeIconPolicy.applicationIconForPackageOrNull(
                                context = context,
                                packageName = owner,
                            )?.let {
                                XLog.d(TAG, "status bar icon source=app-icon(color) owner=$owner")
                                return it
                            }
                            if (owner != sbn.packageName) {
                                StatusBarMonochromeIconPolicy.applicationIconForPackageOrNull(
                                    context = context,
                                    packageName = sbn.packageName,
                                )?.let {
                                    XLog.d(TAG, "status bar icon source=app-icon(color) pkg=${sbn.packageName}")
                                    return it
                                }
                            }
                            return notification.smallIcon
                        }
                        fun brokenResourceFallback(): Icon? =
                            if (options.colorStatusBarIcon) colorfulFallback() else monochromeFallback()
                        // AUTOGROUP/system summaries: resId=0 white-block, or android
                        // ic_notification_summary_auto generic glyph (Alipay aggregate case).
                        if (SystemUiNotificationPolicy.shouldReplaceBrokenResourceSmallIcon(
                                iconType = iconType,
                                resId = resId,
                                resPackage = resPackage,
                                notificationFlags = notification.flags,
                            )
                        ) {
                            val fallback = brokenResourceFallback()
                            if (fallback != null) {
                                result = fallback
                                return@doBefore
                            }
                        }
                        if (SystemUiNotificationPolicy.shouldReplaceBitmapWithPackageMonochrome(
                                colorStatusBarIcon = options.colorStatusBarIcon,
                                isMiPushManaged = isMiPushManaged,
                                iconType = iconType,
                            )
                        ) {
                            val fallback = monochromeFallback()
                            if (fallback != null) {
                                result = fallback
                                return@doBefore
                            }
                        }
                        // Strong monochrome: replace multi-color RESOURCE logos with a white
                        // package silhouette. Keep grayscale glyphs (Messaging stat_notify_sms)
                        // so launcher adaptive badges do not become status-bar white blocks.
                        val isGrayscaleIcon = isGrayscaleSmallIcon(context, smallIcon)
                        if (SystemUiNotificationPolicy.shouldReplaceResourceWithPackageMonochrome(
                                colorStatusBarIcon = options.colorStatusBarIcon,
                                forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
                                isMiPushManaged = isMiPushManaged,
                                iconType = iconType,
                                packageName = sbn.packageName,
                                uid = sbn.uid,
                                isSystemApp = isSystemApp,
                                canColorize = canColorize,
                                isGrayscaleIcon = isGrayscaleIcon,
                            )
                        ) {
                            val fallback = monochromeFallback()
                            if (fallback != null) {
                                result = fallback
                                return@doBefore
                            }
                        }
                        val shouldIntercept =
                            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                                colorStatusBarIcon = options.colorStatusBarIcon,
                                forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
                                isMiPushManaged = isMiPushManaged,
                                iconType = iconType,
                                resId = resId,
                                resPackage = resPackage,
                                packageName = sbn.packageName,
                                uid = sbn.uid,
                                isSystemApp = isSystemApp,
                                canColorize = canColorize,
                                hasMonochromeResource = isGrayscaleIcon,
                            )
                        val transport = SystemUiNotificationPolicy.statusBarSmallIconTransport(
                            notificationSmallIcon = smallIcon,
                            shouldIntercept = shouldIntercept,
                        )
                        if (
                            transport.source ==
                                SystemUiNotificationPolicy.StatusBarSmallIconSource.FRAMEWORK_NOTIFICATION_SMALL_ICON
                        ) {
                            result = transport.icon
                            return@doBefore
                        }
                    }
                }
                XLog.i(TAG, "hooked NotifImageUtil.getSmallIcon (dynamic color mode check)")
            } catch (e: NoSuchMethodException) {
                XLog.i(
                    TAG,
                    "NotifImageUtil.getSmallIcon unavailable; Android 17 uses getCustomAppIcon: ${e.message}",
                )
            } catch (e: Exception) {
                XLog.e(TAG, "Failed to hook NotifImageUtil.getSmallIcon", e)
            }

            try {
                // CN HyperOS substitutes multi-color app logos when miuiOptimization is on.
                // Block that substitution under monochrome so white-alpha BITMAP silhouettes stick.
                val notifImageUtilClass =
                    classLoader.findClass("com.android.systemui.statusbar.notification.utils.NotifImageUtil")
                notifImageUtilClass.hookAllMethods("shouldSubstituteSmallIcon") {
                    doBefore {
                        // Only override on the status-bar path (StatusBarIconView.updateIconColor).
                        // Notification-shade header/expanded rows also call this; forcing false there
                        // suppresses the colored app icon and turns expanded rows / group summaries
                        // white. Whitelist the StatusBarIconView stack frame to stay status-bar only.
                        if (!SystemUiNotificationPolicy.isStatusBarSubstitutionContext(
                                Thread.currentThread().stackTrace.map { it.className }
                            )
                        ) {
                            return@doBefore
                        }
                        val options = IslandPreferences.current()
                        val sbn = args.firstOrNull() as? StatusBarNotification ?: return@doBefore
                        val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(
                            sbn.notification?.extras
                        )
                        if (SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                                colorStatusBarIcon = options.colorStatusBarIcon,
                                forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
                                isMiPushManaged = isMiPushManaged,
                            )
                        ) {
                            result = false
                        }
                    }
                }
                XLog.i(TAG, "hooked NotifImageUtil.shouldSubstituteSmallIcon for monochrome")
            } catch (e: Exception) {
                XLog.e(TAG, "Failed to hook NotifImageUtil.shouldSubstituteSmallIcon", e)
            }

            try {
                classLoader.findClass("com.android.systemui.statusbar.notification.icon.IconManager")
                    .hookAllMethods("setIcon") {
                        doAfter {
                            runCatching {
                                val options = IslandPreferences.current()
                                val sbn = statusBarNotificationFromEntry(args.firstOrNull()) ?: return@runCatching
                                val notification = sbn.notification
                                val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(
                                    notification?.extras
                                )
                                val preLTag = SystemUiNotificationPolicy.statusBarIconPreLTagOverride(
                                    colorStatusBarIcon = options.colorStatusBarIcon,
                                    forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
                                    isMiPushManaged = isMiPushManaged,
                                )
                                val iconView = args[2] as? View ?: return@runCatching
                                preLTag?.let { iconView.setTag(idIconIsPreL, it) }
                                applyFrameworkAutogroupSummaryIcon(
                                    iconView = iconView,
                                    sbn = sbn,
                                    options = options,
                                )
                                val descriptor = args.getOrNull(1)
                                val hasSubstitutedGlyph = hasSubstitutedStatusBarGlyph(
                                    descriptor = descriptor,
                                    notificationSmallIcon = notification?.smallIcon,
                                )
                                val hasMonochromeResource = isPositivelyGrayscaleSmallIcon(
                                    iconView.context,
                                    notification?.smallIcon,
                                )
                                val shouldTint = SystemUiNotificationPolicy.shouldTintStatusBarGlyph(
                                    colorStatusBarIcon = options.colorStatusBarIcon,
                                    forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
                                    isMiPushManaged = isMiPushManaged,
                                    packageName = sbn.packageName,
                                    uid = sbn.uid,
                                    isSystemApp = isSystemApplication(iconView.context, sbn.packageName),
                                    canColorize = notification?.canColorize() ?: false,
                                    // Only a substituted monochrome glyph may be force-tinted;
                                    // the app's own opaque logo must keep the native rendering.
                                    hasSubstitutedGlyph = hasSubstitutedGlyph,
                                    hasMonochromeResource = hasMonochromeResource,
                                )
                                XLog.i(
                                    TAG,
                                    "tint decision pkg=${sbn.packageName} managed=$isMiPushManaged " +
                                        "color=${options.colorStatusBarIcon} " +
                                        "global=${options.colorStatusBarIconGlobal} " +
                                        "sub=$hasSubstitutedGlyph monoRes=$hasMonochromeResource " +
                                        "tint=$shouldTint",
                                )
                                applyNotificationStatusBarIconTint(iconView, shouldTint)
                            }
                        }
                    }
            } catch (e: Exception) {
                XLog.e(TAG, "Failed to hook IconManager", e)
            }
        } else {
            classLoader.findClass("com.android.systemui.statusbar.notification.collection.NotificationEntry")
                .hookMethod("setIconTag", Int::class.java, Any::class.java) {
                    doBefore {
                        runCatching {
                            if (args[0] != idIconIsPreL) return@runCatching
                            val options = IslandPreferences.current()
                            val sbn = statusBarNotificationFromEntry(thisObject) ?: return@runCatching
                            val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(
                                sbn.notification?.extras
                            )
                            args[1] = SystemUiNotificationPolicy.statusBarIconPreLTagOverride(
                                colorStatusBarIcon = options.colorStatusBarIcon,
                                forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
                                isMiPushManaged = isMiPushManaged,
                            ) ?: return@runCatching
                        }.onFailure {
                            XLog.e(TAG, "legacy setIconTag hook failed", it)
                        }
                    }
                }
        }

    }

    /**
     * Android 17 removed NotifImageUtil.getSmallIcon. IconManager now obtains the status-bar
     * candidate through getCustomAppIcon, which applies the MIUI custom-icon package whitelist.
     * Reuse the focus authorization switch for the same trusted XMSF producer boundary.
     */
    private fun hookNativeCustomAppIcon(classLoader: ClassLoader) {
        runCatching {
            val owner = classLoader.findClass(
                "com.android.systemui.statusbar.notification.utils.NotifImageUtil",
            )
            owner.hookMethod(
                "getCustomAppIcon",
                Notification::class.java,
                Context::class.java,
            ) {
                doAfter {
                    if (result != null || !FocusNotificationPermissionPolicy.isGlobalBypassEnabled()) {
                        return@doAfter
                    }
                    val notification = args.getOrNull(0) as? Notification ?: return@doAfter
                    val context = args.getOrNull(1) as? Context ?: return@doAfter
                    val extras = notification.extras ?: return@doAfter
                    if (extras.getString("miui.opPkg") != XMSF_PACKAGE_NAME) return@doAfter
                    val icon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        extras.getParcelable("miui.appIcon", Icon::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        extras.getParcelable("miui.appIcon") as? Icon
                    } ?: return@doAfter
                    result = runCatching { icon.loadDrawable(context) }
                        .onSuccess { drawable: Drawable? ->
                            if (drawable != null) {
                                XLog.d(TAG, "bypassed custom app icon whitelist for XMSF")
                            }
                        }
                        .getOrNull()
                }
            }
            XLog.i(TAG, "hooked NotifImageUtil.getCustomAppIcon for XMSF")
        }.onFailure {
            XLog.e(TAG, "Failed to hook NotifImageUtil.getCustomAppIcon", it)
        }
    }

    /**
     * Android 17's IconManager prefers the MIUI launcher/custom app icon for the status bar too.
     * Those launcher bitmaps commonly contain an opaque background and become white blocks when
     * the monochrome pipeline tints them. Keep the custom icon for notification headers, but use
     * the already-validated transparent MiPush smallIcon for the status bar descriptor.
     *
     * Synthetic AUTOGROUP_SUMMARY records are different: their framework smallIcon is a generic
     * two-block glyph (for example android:0x010805c7), not a real app status icon. The
     * replacement is gated by the monochrome switch: monochrome mode resolves through the
     * icon-pack first; color mode uses the owning package's colorful icon. The Android logo is
     * the last resort in both modes. Ordinary application group summaries never enter this branch.
     */
    private fun hookAndroid17StatusBarIconDescriptor(classLoader: ClassLoader) {
        runCatching {
            val entryClass = classLoader.findClass(
                "com.android.systemui.statusbar.notification.collection.NotificationEntry",
            )
            classLoader.findClass("com.android.systemui.statusbar.notification.icon.IconManager")
                .hookMethod("getIconDescriptor", entryClass, Boolean::class.javaPrimitiveType!!) {
                    doAfter {
                        val options = IslandPreferences.current()
                        val entry = args.getOrNull(0) ?: return@doAfter
                        val sbn = statusBarNotificationFromEntry(entry) ?: return@doAfter
                        val notification = sbn.notification ?: return@doAfter
                        val descriptor = result ?: return@doAfter
                        runCatching {
                            val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(notification.extras)
                            val systemUiContext = currentApplication() ?: return@runCatching
                            val userId = notificationUserId(sbn) ?: return@runCatching
                            val owner = NotificationOwnerResolver.resolve(
                                sbn.packageName,
                                notification.extras,
                            )
                            val (iconType, resId, resPackage) = readIconResourceFields(notification.smallIcon)
                            val isFrameworkAutogroupSummary =
                                SystemUiNotificationPolicy.isFrameworkAutogroupSummaryNotification(
                                    iconType = iconType,
                                    resId = resId,
                                    resPackage = resPackage,
                                    notificationFlags = notification.flags,
                                )
                            fun iconPackIcon(allowWhenNotGlobal: Boolean = false): Icon? {
                                if (!allowWhenNotGlobal && !options.colorStatusBarIconGlobal) return null
                                return StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(
                                    context = systemUiContext,
                                    packageName = owner,
                                    userId = userId,
                                ) ?: if (owner != sbn.packageName) {
                                    StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(
                                        context = systemUiContext,
                                        packageName = sbn.packageName,
                                        userId = userId,
                                    )
                                } else {
                                    null
                                }
                            }
                            // Icon-pack miss: the package is simply not configured (and new apps
                            // keep shipping), so synthesize a white silhouette from the app's
                            // shaped adaptive layer instead of giving up on the glyph.
                            fun monochromeSilhouette(): Icon? {
                                StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(
                                    context = systemUiContext,
                                    packageName = owner,
                                )?.let { return it }
                                if (owner == sbn.packageName) return null
                                return StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(
                                    context = systemUiContext,
                                    packageName = sbn.packageName,
                                )
                            }
                            if (isFrameworkAutogroupSummary) {
                                // [决策] 聚合通知替换受单色开关门控:单色用图标包,彩色用应用
                                // 彩色图标;Android 机器人 logo 是两种模式共用的最后兜底。
                                val summaryIcon = if (options.colorStatusBarIcon) {
                                    StatusBarMonochromeIconPolicy.applicationIconForPackageOrNull(
                                        context = systemUiContext,
                                        packageName = owner,
                                    ) ?: (if (owner != sbn.packageName) {
                                        StatusBarMonochromeIconPolicy.applicationIconForPackageOrNull(
                                            context = systemUiContext,
                                            packageName = sbn.packageName,
                                        )
                                    } else {
                                        null
                                    }) ?: StatusBarMonochromeIconPolicy.frameworkAndroidLogoIconOrNull()
                                } else {
                                    iconPackIcon(allowWhenNotGlobal = true)
                                        ?: StatusBarMonochromeIconPolicy.frameworkAndroidLogoIconOrNull()
                                }
                                if (summaryIcon != null) {
                                    // The Android logo / package icon replaces the generic
                                    // framework glyph, so the descriptor carries our glyph.
                                    markSubstitutedStatusBarGlyph(
                                        entry = entry,
                                        descriptor = descriptor,
                                        substituted = true,
                                    )
                                    setHookObjectField(descriptor, "icon", summaryIcon)
                                    XLog.d(
                                        TAG,
                                        "status bar icon source=autogroup owner=$owner " +
                                            "color=${options.colorStatusBarIcon}",
                                    )
                                    return@runCatching
                                }
                            }
                            if (options.colorStatusBarIcon) return@runCatching
                            // Fallback chain (managed and global monochrome alike):
                            // icon pack -> white silhouette -> posted glyph. The launcher
                            // icon is never substituted here: it can carry an opaque
                            // adaptive background, and tinting it produces a white block.
                            // The last step keeps the notification's own glyph, and a
                            // substituted glyph (pack/silhouette) makes the tint harmless.
                            val useSubstitution = isMiPushManaged || options.colorStatusBarIconGlobal
                            val icon = if (useSubstitution) {
                                val packIcon = iconPackIcon()
                                val silhouette = if (packIcon == null) monochromeSilhouette() else null
                                XLog.i(
                                    TAG,
                                    "descriptor source pkg=${sbn.packageName} managed=$isMiPushManaged " +
                                        "source=${if (packIcon != null) "iconpack" else if (silhouette != null) "silhouette" else "posted"}",
                                )
                                packIcon ?: silhouette ?: notification.smallIcon
                            } else {
                                null
                            } ?: return@runCatching
                            markSubstitutedStatusBarGlyph(
                                entry = entry,
                                descriptor = descriptor,
                                substituted = icon !== notification.smallIcon,
                            )
                            setHookObjectField(descriptor, "icon", icon)
                        }.onFailure {
                            XLog.e(TAG, "failed to restore Android 17 status bar smallIcon", it)
                        }
                    }
                }
            XLog.i(TAG, "hooked IconManager.getIconDescriptor for Android 17 monochrome")
        }.onFailure {
            XLog.e(TAG, "Failed to hook Android 17 IconManager.getIconDescriptor", it)
        }
    }

    private fun applyFrameworkAutogroupSummaryIcon(
        iconView: View,
        sbn: StatusBarNotification,
        options: IslandOptions,
    ) {
        val notification = sbn.notification ?: return
        val (iconType, resId, resPackage) = readIconResourceFields(notification.smallIcon)
        if (!SystemUiNotificationPolicy.isFrameworkAutogroupSummaryNotification(
                iconType = iconType,
                resId = resId,
                resPackage = resPackage,
                notificationFlags = notification.flags,
            )
        ) {
            return
        }
        val imageView = iconView as? ImageView ?: return
        val context = imageView.context
        val owner = NotificationOwnerResolver.resolve(
            sbn.packageName,
            notification.extras,
        )
        val userId = notificationUserId(sbn) ?: return
        fun iconPackIcon(packageName: String): Icon? =
            StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(
                context = context,
                packageName = packageName,
                userId = userId,
            )
        // [决策] 聚合通知替换受单色开关门控:单色用图标包,彩色用应用彩色图标。
        fun colorfulAppIcon(packageName: String): Icon? =
            StatusBarMonochromeIconPolicy.applicationIconForPackageOrNull(
                context = context,
                packageName = packageName,
            )
        var icon: Icon? = null
        if (options.colorStatusBarIcon) {
            icon = colorfulAppIcon(owner)
            if (icon == null && owner != sbn.packageName) {
                icon = colorfulAppIcon(sbn.packageName)
            }
        } else {
            icon = iconPackIcon(owner)
            if (icon == null && owner != sbn.packageName) {
                icon = iconPackIcon(sbn.packageName)
            }
        }
        if (icon == null) {
            icon = StatusBarMonochromeIconPolicy.frameworkAndroidLogoIconOrNull()
        }
        if (icon == null) return
        val drawable = runCatching { icon.loadDrawable(context) }.getOrNull() ?: return
        imageView.setImageDrawable(drawable)
        imageView.invalidate()
        XLog.d(TAG, "setIcon applied framework autogroup package icon owner=$owner")
    }

    private fun statusBarNotificationFromEntry(entry: Any?): StatusBarNotification? {
        if (entry == null) return null
        return runCatching {
            getHookObjectField(entry, "mSbn") as? StatusBarNotification
        }.getOrNull() ?: runCatching {
            entry.callMethod("getSbn") as? StatusBarNotification
        }.getOrNull()
    }

    /**
     * Extracts (type, resId, resPackage) from an [Icon] for the resource-loadability guard.
     *
     * `getType()` is public API; `getResId()`/`getResPackage()` are hidden, so they are read
     * reflectively. A null icon or any extraction failure yields a non-RESOURCE type marker so
     * the guard treats it as loadable and leaves the base intercept decision untouched.
     */
    private fun readIconResourceFields(icon: Icon?): Triple<Int, Int, String?> {
        if (icon == null) return Triple(-1, 0, null)
        val type = runCatching { icon.type }.getOrDefault(-1)
        if (type != SystemUiNotificationPolicy.ICON_TYPE_RESOURCE) return Triple(type, 0, null)
        val resId = runCatching { icon.callMethod("getResId") as? Int }.getOrNull() ?: 0
        val resPackage = runCatching { icon.callMethod("getResPackage") as? String }.getOrNull()
        return Triple(type, resId, resPackage)
    }

    private fun Notification.canColorize(): Boolean = flags and FLAG_CAN_COLORIZE != 0

    private fun hookGlobalStatusBarIconTint(classLoader: ClassLoader) {
        hookStatusIconTintClass(
            classLoader = classLoader,
            className = "com.android.systemui.statusbar.StatusBarIconView",
        )
        hookStatusIconTintClass(
            classLoader = classLoader,
            className = "com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView",
        )
    }

    private fun hookStatusIconTintClass(classLoader: ClassLoader, className: String) {
        runCatching {
            val iconClass = classLoader.findClass(className)
            iconClass.hookAllMethods("setIconColor") {
                doBefore {
                    forceGlobalMonochromeTintArg(0)
                }
            }
            iconClass.hookAllMethods("setStaticDrawableColor") {
                doBefore {
                    forceGlobalMonochromeTintArg(0)
                }
            }
            iconClass.hookAllMethods("setDecorColor") {
                doBefore {
                    forceGlobalMonochromeTintArg(0)
                }
            }
            iconClass.hookAllMethods("updateLightDarkTint") {
                doBefore {
                    forceGlobalMonochromeTintArg(2)
                }
            }
            XLog.i(TAG, "hooked $className status icon tint")
        }.onFailure {
            XLog.d(TAG, "skip $className status icon tint hook: ${it.message}")
        }
    }

    private fun MethodHookParam.forceGlobalMonochromeTintArg(argIndex: Int) {
        // Deferred views render the app's own glyph natively; forcing white here would
        // re-introduce the white block on the next tint update.
        if (systemTintDeferredViews.contains(thisObject)) return
        val options = IslandPreferences.current()
        if (!SystemUiNotificationPolicy.shouldForceGlobalMonochrome(
                colorStatusBarIcon = options.colorStatusBarIcon,
                forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
            )
        ) {
            return
        }
        val requestedColor = args.getOrNull(argIndex) as? Int ?: return
        val tint = SystemUiNotificationPolicy.globalMonochromeTint(
            requestedColor = requestedColor,
            fallbackColor = statusIconFallbackTint(thisObject),
        )
        if (tint != requestedColor) {
            args[argIndex] = tint
        }
    }

    private fun statusIconFallbackTint(iconView: Any?): Int {
        if (iconView == null) return 0
        val fields = arrayOf("mIconColor", "mCurrentSetColor", "mDrawableColor", "mDecorColor")
        return fields.firstNotNullOfOrNull { field ->
            runCatching {
                getHookIntField(iconView, field).takeIf { it != 0 }
            }.getOrNull()
        } ?: 0
    }

    private fun notificationUserId(sbn: StatusBarNotification): Int? = runCatching {
        sbn.callMethod("getUserId") as? Int
    }.getOrNull()?.takeIf { it >= 0 }

    private fun applyNotificationStatusBarIconTint(iconView: View, shouldTint: Boolean) {
        val imageView = iconView as? ImageView ?: return
        if (!shouldTint) {
            // No glyph of ours to tint: drop the filter we would have forced and remember the
            // view so later setIconColor/setStaticDrawableColor/setDecorColor/updateLightDarkTint
            // calls keep their native colors as well.
            systemTintDeferredViews.add(iconView)
            imageView.clearColorFilter()
            imageView.drawable?.clearColorFilter()
            return
        }
        systemTintDeferredViews.remove(iconView)
        val tint = SystemUiNotificationPolicy.globalMonochromeTint(
            requestedColor = 0,
            fallbackColor = statusIconFallbackTint(iconView),
        )
        imageView.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        imageView.drawable?.mutate()?.setTint(tint)
    }

    /**
     * Records whether the status-bar descriptor for [entry]/[descriptor] carries a glyph this
     * module substituted (icon-pack bitmap or white silhouette) instead of the app's own
     * posted smallIcon. Both keys are stored so IconManager.setIcon can look the decision up
     * from either argument.
     */
    private fun markSubstitutedStatusBarGlyph(entry: Any?, descriptor: Any?, substituted: Boolean) {
        entry?.let { substitutedGlyphKeys[it] = substituted }
        descriptor?.let { substitutedGlyphKeys[it] = substituted }
    }

    /**
     * True when the descriptor icon is a substituted monochrome glyph.
     *
     * Primary signal is the mark recorded by getIconDescriptor; the identity comparison is the
     * fallback for versions where that hook does not exist (Android <= 16), where the legacy
     * getSmallIcon hook writes the icon straight into the result.
     */
    private fun hasSubstitutedStatusBarGlyph(descriptor: Any?, notificationSmallIcon: Icon?): Boolean {
        if (descriptor == null) return false
        substitutedGlyphKeys[descriptor]?.let { return it }
        val descriptorIcon = runCatching { getHookObjectField(descriptor, "icon") as? Icon }.getOrNull()
        return descriptorIcon != null && descriptorIcon !== notificationSmallIcon
    }

    /**
     * True when [icon] is already a monochrome status glyph (or detection failed).
     * Fail-open to true so we keep the posted RESOURCE instead of swapping in a launcher
     * white square (SMS regression). Multi-color logos still get package silhouettes when
     * ContrastColorUtil reports false.
     */
    @SuppressLint("PrivateApi")
    private fun isGrayscaleSmallIcon(context: Context, icon: Icon?): Boolean {
        if (icon == null) return true
        // null/failure => keep original RESOURCE (safer than launcher white-block).
        return detectGrayscaleSmallIcon(context, icon) ?: true
    }

    /**
     * Strict variant for the tint decision: only a positive grayscale verdict proves the glyph
     * survives a white SRC_IN tint. Detection failures return false so the icon keeps the
     * system's native rendering instead of being force-tinted into a white block.
     */
    @SuppressLint("PrivateApi")
    private fun isPositivelyGrayscaleSmallIcon(context: Context, icon: Icon?): Boolean {
        if (icon == null) return false
        return detectGrayscaleSmallIcon(context, icon) ?: false
    }

    /** @return the grayscale verdict, or null when detection is impossible. */
    @SuppressLint("PrivateApi")
    private fun detectGrayscaleSmallIcon(context: Context, icon: Icon): Boolean? = runCatching {
        val utilClass = Class.forName("com.android.internal.util.ContrastColorUtil")
        val instance = utilClass.getMethod("getInstance", Context::class.java).invoke(null, context)
        utilClass
            .getMethod("isGrayscaleIcon", Context::class.java, Icon::class.java)
            .invoke(instance, context, icon) as? Boolean
    }.getOrNull()

    private fun isSystemApplication(context: Context?, packageName: String?): Boolean {
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
}
