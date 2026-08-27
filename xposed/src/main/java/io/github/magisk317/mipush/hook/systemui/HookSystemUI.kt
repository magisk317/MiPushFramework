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
import android.view.View
import android.widget.ImageView
import io.github.magisk317.mipush.common.notification.NotificationOwnerResolver
import io.github.magisk317.mipush.common.notification.StatusBarMonochromeIconPolicy
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.hook.XLog
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

    @get:SuppressLint("DiscouragedApi")
    private val idIconIsPreL: Int by lazy {
        val app = currentApplication() ?: return@lazy 0
        app.resources.getIdentifier("icon_is_pre_L", "id", app.packageName)
    }

    override fun onHotReloading() {
        // SystemUI owns the island dispatcher receiver. Drop it before hot reload so the old
        // module ClassLoader (and its DEX mappings) is not pinned by the registered receiver.
        IslandDispatcher.unregister()
    }

    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        val classLoader = param.classLoader
        XLog.i(TAG, "HookSystemUI.hook() called")
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
                        val userId = runCatching {
                            sbn.callMethod("getUserId") as? Int
                        }.getOrNull() ?: 0
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
                            }
                            // When no configured icon exists, keep the notification's own
                            // status glyph. Converting a launcher icon here can turn adaptive
                            // backgrounds into white blocks; SystemUI will apply the monochrome
                            // tint to the original notification icon.
                            return notification.smallIcon
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
                            val fallback = monochromeFallback()
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
                                val shouldTint = SystemUiNotificationPolicy.shouldApplyMonochromeTintToNotification(
                                    colorStatusBarIcon = options.colorStatusBarIcon,
                                    forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
                                    isMiPushManaged = isMiPushManaged,
                                    packageName = sbn.packageName,
                                    uid = sbn.uid,
                                    isSystemApp = isSystemApplication(iconView.context, sbn.packageName),
                                    canColorize = notification?.canColorize() ?: false,
                                    hasMonochromeResource = isGrayscaleSmallIcon(
                                        iconView.context,
                                        notification?.smallIcon,
                                    ),
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
                        if (options.colorStatusBarIcon) return@doAfter
                        val entry = args.getOrNull(0) ?: return@doAfter
                        val sbn = statusBarNotificationFromEntry(entry) ?: return@doAfter
                        val notification = sbn.notification ?: return@doAfter
                        val descriptor = result ?: return@doAfter
                        runCatching {
                            val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(notification.extras)
                            val systemUiContext = currentApplication() ?: return@runCatching
                            val userId = runCatching {
                                sbn.callMethod("getUserId") as? Int
                            }.getOrNull() ?: 0
                            val owner = NotificationOwnerResolver.resolve(
                                sbn.packageName,
                                notification.extras,
                            )
                            fun iconPackIcon(): Icon? {
                                if (!options.colorStatusBarIconGlobal) return null
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
                            val icon = if (isMiPushManaged) {
                                iconPackIcon() ?: notification.smallIcon
                            } else if (options.colorStatusBarIconGlobal) {
                                // The notification smallIcon is the status glyph. The launcher
                                // icon fallback can contain an opaque adaptive background and
                                // turns into a white block when SystemUI applies monochrome tint.
                                iconPackIcon() ?: notification.smallIcon
                            } else {
                                null
                            } ?: return@runCatching
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

    private fun applyNotificationStatusBarIconTint(iconView: View, shouldTint: Boolean) {
        val imageView = iconView as? ImageView ?: return
        if (!shouldTint) {
            imageView.clearColorFilter()
            imageView.drawable?.clearColorFilter()
            return
        }
        val tint = SystemUiNotificationPolicy.globalMonochromeTint(
            requestedColor = 0,
            fallbackColor = statusIconFallbackTint(iconView),
        )
        imageView.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        imageView.drawable?.mutate()?.setTint(tint)
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
        val detected = runCatching {
            val utilClass = Class.forName("com.android.internal.util.ContrastColorUtil")
            val instance = utilClass.getMethod("getInstance", Context::class.java).invoke(null, context)
            utilClass
                .getMethod("isGrayscaleIcon", Context::class.java, Icon::class.java)
                .invoke(instance, context, icon) as? Boolean
        }.getOrNull()
        // null/failure => keep original RESOURCE (safer than launcher white-block).
        return detected ?: true
    }

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
