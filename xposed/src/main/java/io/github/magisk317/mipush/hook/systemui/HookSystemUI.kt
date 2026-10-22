package io.github.magisk317.mipush.hook.systemui

import android.annotation.SuppressLint
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
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
        hookGlobalStatusBarIconTint()

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
                        val attemptsIconPack = SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                            thirdPartyIconPack = options.thirdPartyIconPack,
                            systemAppIconPack = options.systemAppIconPack,
                            isMiPushManaged = isMiPushManaged,
                            uid = sbn.uid,
                            isSystemApp = isSystemApp,
                        )
                        fun monochromeFallback(): Icon? {
                            // Product rule (2026-10): icon pack first, silhouette only for
                            // MiPush-managed posts, and NO posted-glyph fallback — a miss must
                            // return null so the notification stays on SystemUI's native
                            // rendering (the white-block-free baseline) instead of being forced.
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
                            if (isMiPushManaged) {
                                // Managed posts can carry an opaque launcher bitmap that would
                                // tint into a white block; the silhouette is white-alpha by
                                // construction and stays shape-safe under any tint.
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
                            return null
                        }
                        // Broken/generic icons (resId=0 summary, autogroup glyph) must be
                        // replaced with something renderable: icon pack (category-gated), then
                        // the package's own colorful icon, then the framework Android logo.
                        // Never null: a broken glyph would render as garbage.
                        fun brokenResourceFallback(): Icon? =
                            monochromeFallback()
                                ?: StatusBarMonochromeIconPolicy.applicationIconForPackageOrNull(
                                    context = context,
                                    packageName = owner,
                                )
                                ?: StatusBarMonochromeIconPolicy.frameworkAndroidLogoIconOrNull()
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
                        // Multi-color RESOURCE logos: swap for the icon-pack glyph when the
                        // category allows and the pack covers the app. Keep grayscale glyphs
                        // (Messaging stat_notify_sms) — they are already status-bar ready.
                        val isGrayscaleIcon = isGrayscaleSmallIcon(context, smallIcon)
                        if (SystemUiNotificationPolicy.shouldReplaceResourceWithPackageMonochrome(
                                attemptsIconPack = attemptsIconPack,
                                isMiPushManaged = isMiPushManaged,
                                iconType = iconType,
                                isGrayscaleIcon = isGrayscaleIcon,
                            )
                        ) {
                            val fallback = monochromeFallback()
                            if (fallback != null) {
                                result = fallback
                                return@doBefore
                            }
                        }
                        // BITMAP + managed + monochrome: intercept so MIUI cannot re-substitute
                        // a color launcher icon over the module-authored white asset.
                        val shouldIntercept =
                            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                                isMiPushManaged = isMiPushManaged,
                                iconPackHit = attemptsIconPack &&
                                    iconPackHitForStatus(context, owner, sbn.packageName, userId),
                                iconType = iconType,
                                resId = resId,
                                resPackage = resPackage,
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
                        val context = currentApplication() ?: return@doBefore
                        val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(
                            sbn.notification?.extras
                        )
                        val userId = notificationUserId(sbn) ?: 0
                        val iconPackHit = isMiPushManaged || iconPackHitForStatus(
                            context = context,
                            owner = NotificationOwnerResolver.resolve(
                                sbn.packageName,
                                sbn.notification?.extras,
                            ),
                            postedPackage = sbn.packageName,
                            userId = userId,
                        )
                        val isSyntheticAutogroupSummary =
                            SystemUiNotificationPolicy.isSyntheticAutogroupSummaryNotification(
                                sbn.notification?.flags ?: 0,
                            )
                        // A third-party app that already posts a standard monochrome glyph gets to
                        // keep it: SystemUI tints that glyph, while MIUI's substitution would put a
                        // multi-color launcher logo in the status bar.
                        val keepsOwnMonochromeGlyph = !iconPackHit &&
                            SystemUiNotificationPolicy.shouldInspectPostedGlyphForMonochrome(
                                colorStatusBarIcon = options.colorStatusBarIcon,
                                thirdPartyIconPack = options.thirdPartyIconPack,
                                isMiPushManaged = isMiPushManaged,
                                uid = sbn.uid,
                                isSystemApp = isSystemApplication(context, sbn.packageName),
                            ) && isPositivelyGrayscaleSmallIcon(context, sbn.notification?.smallIcon)
                        if (SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                                isMiPushManaged = isMiPushManaged,
                                iconPackHit = iconPackHit,
                                isSyntheticAutogroupSummary = isSyntheticAutogroupSummary,
                                colorStatusBarIcon = options.colorStatusBarIcon,
                                keepsOwnMonochromeGlyph = keepsOwnMonochromeGlyph,
                            )
                        ) {
                            if (keepsOwnMonochromeGlyph) {
                                XLog.d(TAG, "status bar glyph kept pkg=${sbn.packageName} source=posted-monochrome")
                            }
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
                                    attemptsIconPack = options.thirdPartyIconPack || options.systemAppIconPack,
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
                                // The module no longer authors tints: substituted glyphs are
                                // monochrome by construction, everything else is native. This
                                // hook now exists purely for the observability log.
                                XLog.i(
                                    TAG,
                                    "tint decision pkg=${sbn.packageName} managed=$isMiPushManaged " +
                                        "thirdParty=${options.thirdPartyIconPack} " +
                                        "systemApp=${options.systemAppIconPack} " +
                                        "sub=$hasSubstitutedGlyph monoRes=$hasMonochromeResource " +
                                        "tint=false",
                                )
                                applyNotificationStatusBarIconTint(iconView, false)
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
                                attemptsIconPack = options.thirdPartyIconPack || options.systemAppIconPack,
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
     * Synthetic AUTOGROUP_SUMMARY records are different: HyperOS either ships a generic
     * framework glyph (android:0x010805c7) or copies the representative child's colorful
     * launcher icon, and never inherits the child's target_package extras. The replacement is
     * gated by the monochrome switch: monochrome resolves icon pack -> white silhouette; color
     * mode uses the owning package's colorful icon. The Android logo is the last resort.
     * Ordinary application group summaries never enter this branch.
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
                            val isSyntheticAutogroupSummary =
                                SystemUiNotificationPolicy.isSyntheticAutogroupSummaryNotification(
                                    notification.flags,
                                )
                            fun iconPackIcon(): Icon? {
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
                            fun colorfulAppIcon(): Icon? {
                                StatusBarMonochromeIconPolicy.applicationIconForPackageOrNull(
                                    context = systemUiContext,
                                    packageName = owner,
                                )?.let { return it }
                                if (owner == sbn.packageName) return null
                                return StatusBarMonochromeIconPolicy.applicationIconForPackageOrNull(
                                    context = systemUiContext,
                                    packageName = sbn.packageName,
                                )
                            }
                            if (isSyntheticAutogroupSummary) {
                                // Synthetic summaries copy the child's smallIcon but not its
                                // target_package extras, so they never look MiPush-managed. On
                                // HyperOS that icon is often the app's colorful launcher resource.
                                // Monochrome resolves icon pack -> white silhouette -> Android logo;
                                // only color mode may use the package's colorful icon.
                                val summaryIcon = iconPackIcon()
                                    ?: (if (options.colorStatusBarIcon) {
                                        colorfulAppIcon()
                                    } else {
                                        monochromeSilhouette()
                                    })
                                    ?: StatusBarMonochromeIconPolicy.frameworkAndroidLogoIconOrNull()
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
                            // Managed posts with color mode enabled carry color assets from XMSF;
                            // leave them to the native renderer.
                            if (options.colorStatusBarIcon && isMiPushManaged) return@runCatching
                            // Product rule (2026-10): icon pack hit -> substitute; miss -> keep
                            // the posted glyph under SystemUI's native rendering. The silhouette
                            // exists only for MiPush-managed posts (opaque launcher bitmaps would
                            // tint into white blocks; their white-alpha silhouettes never do).
                            // No posted-glyph substitution, no forced tint: a miss must land on
                            // the native path, which never paints an opaque asset.
                            val attemptsIconPack = SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                                thirdPartyIconPack = options.thirdPartyIconPack,
                                systemAppIconPack = options.systemAppIconPack,
                                isMiPushManaged = isMiPushManaged,
                                uid = sbn.uid,
                                isSystemApp = isSystemApplication(systemUiContext, sbn.packageName),
                            )
                            val packIcon = if (attemptsIconPack) iconPackIcon()?.let {
                                // A null verdict means inspection failed, not that the glyph
                                // is unsafe: fall back to the pack bitmap as authored.
                                StatusBarMonochromeIconPolicy.statusSafeGlyphOrNull(systemUiContext, it) ?: it
                            } else null
                            val silhouette = if (packIcon == null && isMiPushManaged &&
                                !options.colorStatusBarIcon
                            ) {
                                monochromeSilhouette()
                            } else {
                                null
                            }
                            val icon = packIcon ?: silhouette
                            // Framework ids (0x01......) resolve through SystemUI resources;
                            // app ids stay opaque hex unless the owning package is loaded.
                            val smallIconResName = smallIconResourceName(
                                systemUiContext,
                                iconType,
                                resId,
                                resPackage ?: sbn.packageName,
                            )
                            XLog.i(
                                TAG,
                                "descriptor source pkg=${sbn.packageName} managed=$isMiPushManaged " +
                                    "smallIcon=$smallIconResName " +
                                    "attempt=$attemptsIconPack " +
                                    "source=${if (packIcon != null) "iconpack" else if (silhouette != null) "silhouette" else "posted-native"}",
                            )
                            if (icon == null) return@runCatching
                            markSubstitutedStatusBarGlyph(
                                entry = entry,
                                descriptor = descriptor,
                                substituted = true,
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
        if (!SystemUiNotificationPolicy.isSyntheticAutogroupSummaryNotification(notification.flags)) {
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
        // Autogroup summaries ship a broken generic glyph or a copied colorful launcher icon;
        // replace with icon pack (category-gated), then a monochrome silhouette or the package's
        // colorful icon depending on the mode, then the Android logo.
        fun colorfulAppIcon(packageName: String): Icon? =
            StatusBarMonochromeIconPolicy.applicationIconForPackageOrNull(
                context = context,
                packageName = packageName,
            )
        fun silhouetteIcon(packageName: String): Icon? =
            StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(
                context = context,
                packageName = packageName,
            )
        var icon: Icon? = iconPackIcon(owner)
        if (icon == null && owner != sbn.packageName) {
            icon = iconPackIcon(sbn.packageName)
        }
        if (icon == null) {
            icon = if (options.colorStatusBarIcon) {
                colorfulAppIcon(owner)
                    ?: if (owner != sbn.packageName) {
                        colorfulAppIcon(sbn.packageName)
                    } else {
                        null
                    }
            } else {
                silhouetteIcon(owner)
                    ?: if (owner != sbn.packageName) {
                        silhouetteIcon(sbn.packageName)
                    } else {
                        null
                    }
            }
        }
        if (icon == null) {
            icon = StatusBarMonochromeIconPolicy.frameworkAndroidLogoIconOrNull()
        }
        if (icon == null) return
        val drawable = runCatching { icon.loadDrawable(context) }.getOrNull() ?: return
        imageView.setImageDrawable(drawable)
        imageView.invalidate()
        XLog.d(
            TAG,
            "setIcon applied autogroup summary icon owner=$owner color=${options.colorStatusBarIcon}",
        )
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
     * Cache-only icon-pack hit probe for hot paths (substitution blocking). Reads the same
     * cache the descriptor hook populates; a MISS schedules a background prefetch and reports
     * false until the next icon rebuild.
     */
    private fun iconPackHitForStatus(
        context: Context,
        owner: String,
        postedPackage: String,
        userId: Int,
    ): Boolean {
        if (owner.isBlank() || userId < 0) return false
        if (StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(context, owner, userId) != null) {
            return true
        }
        return owner != postedPackage &&
            StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(context, postedPackage, userId) != null
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

    /**
     * Resolves the posted small icon's resource name against the package that owns it.
     *
     * SystemUI's own resources reinterpret a foreign resId as a SystemUI id and return an
     * unrelated name (a "$ic_device_camera_on_anim__0" style anim), which made the descriptor
     * log impossible to act on during triage. Foreign ids resolve through a package context.
     */
    private fun smallIconResourceName(
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

    private fun Notification.canColorize(): Boolean = flags and FLAG_CAN_COLORIZE != 0

    /**
     * Retired: the module no longer authors status-bar tints.
     *
     * Forcing an SRC_IN gray over SystemUI's own tint produced flat, off-color glyphs that did
     * not follow light/dark inversion. Status-bar icons now take exactly one of two paths:
     * an icon-pack bitmap substituted into the descriptor, or the app's own glyph left to
     * SystemUI's native rendering. Both keep the system's own tint.
     */
    private fun hookGlobalStatusBarIconTint() {
        XLog.i(TAG, "module-authored status bar tint retired (icon-pack or native system tint only)")
    }

    private fun notificationUserId(sbn: StatusBarNotification): Int? = runCatching {
        sbn.callMethod("getUserId") as? Int
    }.getOrNull()?.takeIf { it >= 0 }

    /**
     * Kept as a no-op: the decision is still logged so the icon-pack vs. native split stays
     * observable, but the module no longer sets or clears any color filter. SystemUI owns the
     * status-bar tint in every case.
     */
    private fun applyNotificationStatusBarIconTint(iconView: View, shouldTint: Boolean) {
        if (iconView !is ImageView) return
        XLog.d(TAG, "tint skipped (native) pkgView=${iconView.javaClass.simpleName} wouldTint=$shouldTint")
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
        if (icon.type == Icon.TYPE_RESOURCE) {
            // The (Context, Icon) overload resolves the bare resId against SystemUI's
            // resources, losing the owning package and misreading app icons. Load through
            // the Icon so the correct package drawable is inspected.
            val drawable = icon.loadDrawable(context) ?: return@runCatching null
            utilClass
                .getMethod("isGrayscaleIcon", Drawable::class.java)
                .invoke(instance, drawable) as? Boolean
        } else {
            utilClass
                .getMethod("isGrayscaleIcon", Context::class.java, Icon::class.java)
                .invoke(instance, context, icon) as? Boolean
        }
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
