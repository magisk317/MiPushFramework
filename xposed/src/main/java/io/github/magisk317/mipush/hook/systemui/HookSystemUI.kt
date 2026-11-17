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
import android.view.View
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

    private val autogroupSummaryIconResolver: AutogroupSummaryIconResolver by lazy {
        AutogroupSummaryIconResolver(
            statusBarNotificationFromEntry = ::statusBarNotificationFromEntry,
            notificationUserId = ::notificationUserId,
            isSystemApplication = ::isSystemApplication,
        )
    }

    @SuppressLint("DiscouragedApi")
    private fun resolveIconIsPreL(): Int {
        val app = currentApplication() ?: return 0
        return app.resources.getIdentifier("icon_is_pre_L", "id", app.packageName)
    }

    override fun onHotReloading() {
        // SystemUI owns the island dispatcher receiver. Drop it before hot reload so the old
        // module ClassLoader (and its DEX mappings) is not pinned by the registered receiver.
        IslandDispatcher.unregister()
        autogroupSummaryIconResolver.clearAll()
    }

    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        val classLoader = param.classLoader
        XLog.i(TAG, "HookSystemUI.hook() called commit=${io.github.magisk317.mipush.common.BuildConfig.GIT_COMMIT}")
        MiuiHeaderAppIconHook().hook(classLoader)
        hookNativeCustomAppIcon(classLoader)
        hookAndroid17StatusBarIconDescriptor(classLoader)

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
                        if (!SystemUiNotificationPolicy.isMonochromeTakeoverEnabled(options.colorStatusBarIcon)) {
                            return@doBefore
                        }
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
                            )?.let {
                                XLog.d(TAG, "status bar icon source=icon-pack owner=$owner")
                                return it
                            }
                            if (owner != sbn.packageName) {
                                StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(
                                    context = context,
                                    packageName = sbn.packageName,
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
                        // Broken/generic icons from a managed post may need a safe replacement.
                        // For an unmanaged synthetic summary, a pack miss must remain native;
                        // do not turn the fallback into a launcher/silhouette substitution.
                        val isSyntheticAutogroupSummary =
                            SystemUiNotificationPolicy.isSyntheticAutogroupSummaryNotification(
                                notification.flags,
                            )
                        fun brokenResourceFallback(): Icon? =
                            monochromeFallback()
                                ?: if (!isSyntheticAutogroupSummary || isMiPushManaged) {
                                    StatusBarMonochromeIconPolicy.applicationIconForPackageOrNull(
                                        context = context,
                                        packageName = owner,
                                    ) ?: StatusBarMonochromeIconPolicy.frameworkAndroidLogoIconOrNull()
                                } else {
                                    null
                                }
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

            hookShouldSubstituteSmallIcon(classLoader)

            try {
                classLoader.findClass("com.android.systemui.statusbar.notification.icon.IconManager")
                    .hookAllMethods("setIcon") {
                        doAfter {
                            runCatching {
                                val entry = args.firstOrNull() ?: return@runCatching
                                val sbn = statusBarNotificationFromEntry(entry) ?: return@runCatching
                                val options = IslandPreferences.current()
                                if (!SystemUiNotificationPolicy.isMonochromeTakeoverEnabled(
                                        options.colorStatusBarIcon
                                    )
                                ) {
                                    autogroupSummaryIconResolver.clear(sbn)
                                    return@runCatching
                                }
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
                                val descriptor = args.getOrNull(1)
                                val isSyntheticAutogroupSummary =
                                    SystemUiNotificationPolicy.isSyntheticAutogroupSummaryNotification(
                                        notification?.flags ?: 0,
                                    )
                                if (isSyntheticAutogroupSummary) {
                                    val summaryContext = iconView.context
                                    val summaryGlyphSafety = autogroupSummaryIconResolver.postedMonochromeGlyphSafety(
                                        summaryContext,
                                        notification?.smallIcon,
                                    )
                                    val childResolution = autogroupSummaryIconResolver.resolveChildGlyph(
                                        entry = entry,
                                        summarySbn = sbn,
                                        context = summaryContext,
                                        thirdPartyIconPack = options.thirdPartyIconPack,
                                        systemAppIconPack = options.systemAppIconPack,
                                    )
                                    val replaceWithChildMonochrome =
                                        SystemUiNotificationPolicy.shouldReplaceUnmanagedAutogroupSummaryWithChildMonochrome(
                                            isSyntheticAutogroupSummary = true,
                                            isMiPushManagedSummary = isMiPushManaged,
                                            summaryGlyphStateKnown = summaryGlyphSafety != null,
                                            summaryHasSafeOwnMonochromeGlyph = summaryGlyphSafety == true,
                                            childStateKnown = childResolution.childStateKnown,
                                            hasMonochromeChild = childResolution.hasMonochromeChild,
                                            safeReplacementAvailable = childResolution.icon != null,
                                        )
                                    if (replaceWithChildMonochrome) {
                                        val overrideKey = autogroupSummaryIconResolver.overrideKeyOrNull(sbn)
                                        if (overrideKey != null && childResolution.icon != null) {
                                            autogroupSummaryIconResolver.rememberOverride(
                                                overrideKey,
                                                childResolution.icon,
                                            )
                                        }
                                    }
                                    XLog.i(
                                        TAG,
                                        "autogroup summary glyph decision childrenKnown=${childResolution.childStateKnown} " +
                                            "monoChild=${childResolution.hasMonochromeChild} " +
                                            "source=${childResolution.source} replace=$replaceWithChildMonochrome",
                                    )
                                }
                                autogroupSummaryIconResolver.applyFrameworkAutogroupSummaryIcon(
                                    iconView = iconView,
                                    sbn = sbn,
                                    thirdPartyIconPack = options.thirdPartyIconPack,
                                    systemAppIconPack = options.systemAppIconPack,
                                )
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
                            }.onFailure {
                                // setIcon runs once per notification: a failed island override is a visual miss, not a
// reason to flood the log or let an exception escape into SystemUI's render path.
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
                            if (!SystemUiNotificationPolicy.isMonochromeTakeoverEnabled(
                                    options.colorStatusBarIcon
                                )
                            ) {
                                return@runCatching
                            }
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
     * Install the CN HyperOS shouldSubstituteSmallIcon hook. Split out of
     * onLoadPackage so the entry point stays under the cyclomatic budget.
     */
    private fun hookShouldSubstituteSmallIcon(classLoader: ClassLoader) {
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
                    val sbn = args.firstOrNull() as? StatusBarNotification ?: return@doBefore
                    val isSyntheticAutogroupSummary =
                        SystemUiNotificationPolicy.isSyntheticAutogroupSummaryNotification(
                            sbn.notification?.flags ?: 0,
                        )
                    val options = IslandPreferences.current()
                    if (!SystemUiNotificationPolicy.isMonochromeTakeoverEnabled(options.colorStatusBarIcon)) {
                        if (isSyntheticAutogroupSummary) autogroupSummaryIconResolver.clear(sbn)
                        return@doBefore
                    }
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
                    // An app that already posts a standard monochrome glyph gets to keep it:
                    // SystemUI tints that glyph, while MIUI's substitution would put a
                    // multi-color launcher logo in the status bar. System apps answer to their own
                    // icon-pack switch, so a store's download notice and Play's look the same.
                    val postedGlyphIsMonochrome = if (isSyntheticAutogroupSummary) {
                        autogroupSummaryIconResolver.postedMonochromeGlyphSafety(context, sbn.notification?.smallIcon) == true
                    } else {
                        isPositivelyGrayscaleSmallIcon(context, sbn.notification?.smallIcon)
                    }
                    val keepsOwnMonochromeGlyph = !iconPackHit &&
                        SystemUiNotificationPolicy.shouldInspectPostedGlyphForMonochrome(
                            thirdPartyIconPack = options.thirdPartyIconPack,
                            systemAppIconPack = options.systemAppIconPack,
                            isMiPushManaged = isMiPushManaged,
                            uid = sbn.uid,
                            isSystemApp = isSystemApplication(context, sbn.packageName),
                            postedPackageName = sbn.packageName,
                        ) && postedGlyphIsMonochrome
                    if (SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                            isMiPushManaged = isMiPushManaged,
                            iconPackHit = iconPackHit,
                            isSyntheticAutogroupSummary = isSyntheticAutogroupSummary,
                            keepsOwnMonochromeGlyph = keepsOwnMonochromeGlyph,
                            monochromeAutogroupSummaryOverrideApplied =
                                isSyntheticAutogroupSummary &&
                                    autogroupSummaryIconResolver.overrideIconOrNull(sbn) != null,
                        )
                    ) {
                        if (keepsOwnMonochromeGlyph) {
                            XLog.d(TAG, "status bar glyph kept pkg=${sbn.packageName} source=posted-monochrome")
                        }
                        result = false
                    }
                    if (isSyntheticAutogroupSummary) {
                        XLog.i(
                            TAG,
                            "autogroup summary substitution managed=$isMiPushManaged " +
                                "overrideApplied=${autogroupSummaryIconResolver.overrideIconOrNull(sbn) != null} " +
                                "keepsOwn=$keepsOwnMonochromeGlyph blocked=${result == false}",
                        )
                    }
                }
            }
            XLog.i(TAG, "hooked NotifImageUtil.shouldSubstituteSmallIcon for monochrome")
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to hook NotifImageUtil.shouldSubstituteSmallIcon", e)
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to hook NotifImageUtil.shouldSubstituteSmallIcon", e)
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
     * launcher icon, and never inherits the child's target_package extras. A configured icon-pack
     * entry remains authoritative. On an unmanaged pack miss, a summary with an abnormal own glyph
     * may inherit a verified monochrome glyph from a direct child; an unreadable group or an
     * unready safe glyph leaves SystemUI's native rendering intact. Managed summaries retain their
     * existing silhouette/Android-logo fallback. Nothing here runs in color mode --
     * [SystemUiNotificationPolicy.isMonochromeTakeoverEnabled] returns the descriptor to MIUI.
     */
    private fun hookAndroid17StatusBarIconDescriptor(classLoader: ClassLoader) {
        runCatching {
            val entryClass = classLoader.findClass(
                "com.android.systemui.statusbar.notification.collection.NotificationEntry",
            )
            classLoader.findClass("com.android.systemui.statusbar.notification.icon.IconManager")
                .hookMethod("getIconDescriptor", entryClass, Boolean::class.javaPrimitiveType!!) {
                    doAfter {
                        val entry = args.getOrNull(0) ?: return@doAfter
                        val sbn = statusBarNotificationFromEntry(entry) ?: return@doAfter
                        val notification = sbn.notification ?: return@doAfter
                        val isSyntheticAutogroupSummary =
                            SystemUiNotificationPolicy.isSyntheticAutogroupSummaryNotification(
                                notification.flags,
                            )
                        val options = IslandPreferences.current()
                        if (!SystemUiNotificationPolicy.isMonochromeTakeoverEnabled(options.colorStatusBarIcon)) {
                            if (isSyntheticAutogroupSummary) autogroupSummaryIconResolver.clear(sbn)
                            return@doAfter
                        }
                        val descriptor = result ?: return@doAfter
                        runCatching {
                            val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(
                                notification.extras,
                            )
                            val systemUiContext = currentApplication() ?: return@runCatching
                            val userId = notificationUserId(sbn) ?: return@runCatching
                            val owner = NotificationOwnerResolver.resolve(
                                sbn.packageName,
                                notification.extras,
                            )
                            val (iconType, resId, resPackage) = readIconResourceFields(notification.smallIcon)
                            var summaryGlyphSafety: Boolean? = null
                            fun iconPackIcon(): Icon? {
                                return StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(
                                    context = systemUiContext,
                                    packageName = owner,
                                ) ?: if (owner != sbn.packageName) {
                                    StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(
                                        context = systemUiContext,
                                        packageName = sbn.packageName,
                                    )
                                } else {
                                    null
                                }
                            }
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
                            if (isSyntheticAutogroupSummary) {
                                autogroupSummaryIconResolver.clear(sbn)
                                // Keep the established explicit icon-pack and managed fallback
                                // behavior. Child inheritance is only for an unmanaged pack miss.
                                val summaryIcon = iconPackIcon()
                                    ?: if (isMiPushManaged) {
                                        monochromeSilhouette()
                                            ?: StatusBarMonochromeIconPolicy.frameworkAndroidLogoIconOrNull()
                                    } else {
                                        null
                                    }
                                if (summaryIcon != null) {
                                    markSubstitutedStatusBarGlyph(
                                        entry = entry,
                                        descriptor = descriptor,
                                        substituted = true,
                                    )
                                    setHookObjectField(descriptor, "icon", summaryIcon)
                                    XLog.d(
                                        TAG,
                                        "status bar icon source=autogroup owner=$owner " +
                                            "managed=$isMiPushManaged",
                                    )
                                    return@runCatching
                                }
                                summaryGlyphSafety = autogroupSummaryIconResolver.postedMonochromeGlyphSafety(
                                    systemUiContext,
                                    notification.smallIcon,
                                )
                                if (!isMiPushManaged) {
                                    val shouldInspectSummaryGlyph =
                                        SystemUiNotificationPolicy.shouldInspectPostedGlyphForMonochrome(
                                            thirdPartyIconPack = options.thirdPartyIconPack,
                                            systemAppIconPack = options.systemAppIconPack,
                                            isMiPushManaged = false,
                                            uid = sbn.uid,
                                            isSystemApp = isSystemApplication(systemUiContext, sbn.packageName),
                                            postedPackageName = sbn.packageName,
                                        )
                                    if (summaryGlyphSafety == true && shouldInspectSummaryGlyph) {
                                        val postedGlyph = notification.smallIcon ?: return@runCatching
                                        setHookObjectField(descriptor, "icon", postedGlyph)
                                        markSubstitutedStatusBarGlyph(
                                            entry = entry,
                                            descriptor = descriptor,
                                            substituted = true,
                                        )
                                        XLog.d(
                                            TAG,
                                            "status bar icon source=autogroup-posted-monochrome pkg=${sbn.packageName}",
                                        )
                                        return@runCatching
                                    }
                                    if (summaryGlyphSafety == false) {
                                        val childResolution = autogroupSummaryIconResolver.resolveChildGlyph(
                                            entry = entry,
                                            summarySbn = sbn,
                                            context = systemUiContext,
                                            thirdPartyIconPack = options.thirdPartyIconPack,
                                            systemAppIconPack = options.systemAppIconPack,
                                        )
                                        val shouldUseChildGlyph =
                                            SystemUiNotificationPolicy.shouldReplaceUnmanagedAutogroupSummaryWithChildMonochrome(
                                                isSyntheticAutogroupSummary = true,
                                                isMiPushManagedSummary = false,
                                                summaryGlyphStateKnown = true,
                                                summaryHasSafeOwnMonochromeGlyph = false,
                                                childStateKnown = childResolution.childStateKnown,
                                                hasMonochromeChild = childResolution.hasMonochromeChild,
                                                safeReplacementAvailable = childResolution.icon != null,
                                            )
                                        if (shouldUseChildGlyph) {
                                            val childIcon = childResolution.icon ?: return@runCatching
                                            val summaryKey = autogroupSummaryIconResolver.overrideKeyOrNull(sbn) ?: return@runCatching
                                            setHookObjectField(descriptor, "icon", childIcon)
                                            autogroupSummaryIconResolver.rememberOverride(summaryKey, childIcon)
                                            markSubstitutedStatusBarGlyph(
                                                entry = entry,
                                                descriptor = descriptor,
                                                substituted = true,
                                            )
                                            XLog.i(TAG, "autogroup child glyph applied source=${childResolution.source}")
                                            return@runCatching
                                        }
                                        XLog.d(
                                            TAG,
                                            "autogroup child fallback known=${childResolution.childStateKnown} " +
                                                "mono=${childResolution.hasMonochromeChild} " +
                                                "ready=${childResolution.icon != null}",
                                        )
                                        val cachedGroupGlyph = sbn.groupKey?.takeIf { it.isNotBlank() }
                                            ?.let { autogroupSummaryIconResolver.groupGlyphOrNull(it) }
                                        if (cachedGroupGlyph != null) {
                                            setHookObjectField(descriptor, "icon", cachedGroupGlyph)
                                            markSubstitutedStatusBarGlyph(
                                                entry = entry,
                                                descriptor = descriptor,
                                                substituted = true,
                                            )
                                            autogroupSummaryIconResolver.overrideKeyOrNull(sbn)?.let { key ->
                                                autogroupSummaryIconResolver.rememberOverride(key, cachedGroupGlyph)
                                            }
                                            XLog.i(TAG, "autogroup summary glyph applied source=group-cache")
                                            return@runCatching
                                        }
                                    } else if (summaryGlyphSafety == null) {
                                        XLog.d(
                                            TAG,
                                            "autogroup summary glyph unknown",
                                        )
                                    }
                                }
                            }
                            // Product rule: icon-pack hit -> substitute; miss -> keep the posted glyph
                            // under SystemUI's native rendering. Managed posts may use the existing
                            // silhouette fallback; ordinary unmanaged misses stay native.
                            val attemptsIconPack = SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                                thirdPartyIconPack = options.thirdPartyIconPack,
                                systemAppIconPack = options.systemAppIconPack,
                                isMiPushManaged = isMiPushManaged,
                                uid = sbn.uid,
                                isSystemApp = isSystemApplication(systemUiContext, sbn.packageName),
                            )
                            val packIcon = if (attemptsIconPack) iconPackIcon()?.let {
                                // A null verdict means inspection failed, not that the glyph
                                // is unsafe: preserve the established pack-asset behavior here.
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
                            if (icon == null) {
                                val postedGlyph = notification.smallIcon ?: return@runCatching
                                val keepsPostedGlyph =
                                    SystemUiNotificationPolicy.shouldInspectPostedGlyphForMonochrome(
                                        thirdPartyIconPack = options.thirdPartyIconPack,
                                        systemAppIconPack = options.systemAppIconPack,
                                        isMiPushManaged = isMiPushManaged,
                                        uid = sbn.uid,
                                        isSystemApp = isSystemApplication(systemUiContext, sbn.packageName),
                                        postedPackageName = sbn.packageName,
                                    ) && if (isSyntheticAutogroupSummary) {
                                        summaryGlyphSafety == true
                                    } else {
                                        isPositivelyGrayscaleSmallIcon(systemUiContext, postedGlyph)
                                    }
                                if (!keepsPostedGlyph) return@runCatching
                                val restoredGlyph = StatusBarMonochromeIconPolicy.statusSafeGlyphOrNull(
                                    systemUiContext,
                                    postedGlyph,
                                ) ?: postedGlyph
                                markSubstitutedStatusBarGlyph(
                                    entry = entry,
                                    descriptor = descriptor,
                                    substituted = true,
                                )
                                setHookObjectField(descriptor, "icon", restoredGlyph)
                                XLog.i(
                                    TAG,
                                    "descriptor restored pkg=${sbn.packageName} " +
                                        "smallIcon=$smallIconResName source=posted-monochrome",
                                )
                                return@runCatching
                            }
                            markSubstitutedStatusBarGlyph(
                                entry = entry,
                                descriptor = descriptor,
                                substituted = true,
                            )
                            setHookObjectField(descriptor, "icon", icon)
                            sbn.groupKey?.takeIf { it.isNotBlank() }?.let { groupKey ->
                                autogroupSummaryIconResolver.rememberGroupGlyph(groupKey, icon)
                            }
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
        if (StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(context, owner) != null) {
            return true
        }
        return owner != postedPackage &&
            StatusBarMonochromeIconPolicy.iconPackIconForPackageOrNull(context, postedPackage) != null
    }

    /**
     * Extracts (type, resId, resPackage) from an [Icon] for the resource-loadability guard.
     *
     * `getType()` is public API; `getResId()`/`getResPackage()` are hidden, so they are read
     * reflectively. A null icon or any extraction failure yields a non-RESOURCE type marker so
     * the guard treats it as loadable and leaves the base intercept decision untouched.
     */
    private fun readIconResourceFields(icon: Icon?): Triple<Int, Int, String?> =
        PostedGlyphMonochromeProbe.resourceFields(icon)

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

    private fun notificationUserId(sbn: StatusBarNotification): Int? = runCatching {
        sbn.callMethod("getUserId") as? Int
    }.getOrNull()?.takeIf { it >= 0 }

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
    private fun isGrayscaleSmallIcon(context: Context, icon: Icon?): Boolean =
        PostedGlyphMonochromeProbe.isGrayscale(context, icon)

    /**
     * Strict variant for the tint decision: only a positive grayscale verdict proves the glyph
     * survives a white SRC_IN tint. Detection failures return false so the icon keeps the
     * system's native rendering instead of being force-tinted into a white block.
     */
    @SuppressLint("PrivateApi")
    private fun isPositivelyGrayscaleSmallIcon(context: Context, icon: Icon?): Boolean =
        PostedGlyphMonochromeProbe.isPositivelyGrayscale(context, icon)



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
