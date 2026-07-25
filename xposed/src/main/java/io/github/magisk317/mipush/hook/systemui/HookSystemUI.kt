package io.github.magisk317.mipush.hook.systemui

import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.MethodHookParam

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.PorterDuff
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import io.github.magisk317.mipush.common.notification.SinglePackageNotificationGroupPolicy
import io.github.magisk317.mipush.common.notification.StatusBarMonochromeIconPolicy
import io.github.magisk317.mipush.hook.XLog
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

class HookSystemUI : BaseHook() {
    companion object {
        private const val TAG = "HookSystemUI"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        // Hidden framework flag: Notification.FLAG_CAN_COLORIZE == 0x00000800.
        private const val FLAG_CAN_COLORIZE = 0x00000800
    }

    private val ID_ICON_IS_PRE_L: Int by lazy {
        val app = currentApplication() ?: return@lazy 0
        app.resources.getIdentifier("icon_is_pre_L", "id", app.packageName)
    }

    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        val classLoader = param.classLoader
        XLog.i(TAG, "HookSystemUI.hook() called")
        MiuiHeaderAppIconHook().hook(classLoader)
        MiPushFocusStatusBarIconHook().hook(classLoader)
        GroupCoalescerRankingSafetyHook().hook(classLoader)
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
                        // AUTOGROUP/system summaries often use RESOURCE resId=0 → white status-bar
                        // block. Replace with a package monochrome silhouette before the normal
                        // intercept path (which would either force the broken icon or decline).
                        if (SystemUiNotificationPolicy.shouldReplaceBrokenResourceSmallIcon(
                                iconType = iconType,
                                resId = resId,
                            )
                        ) {
                            val owner = SinglePackageNotificationGroupPolicy.resolveGroupOwnerPackage(
                                sbn.packageName,
                                notification.extras,
                            )
                            val fallback =
                                StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(context, owner)
                                    ?: StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(
                                        context,
                                        sbn.packageName,
                                    )
                            if (fallback != null) {
                                result = fallback
                                return@doBefore
                            }
                        }
                        if (SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                                colorStatusBarIcon = options.colorStatusBarIcon,
                                forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
                                isMiPushManaged = isMiPushManaged,
                                iconType = iconType,
                                resId = resId,
                                resPackage = resPackage,
                                packageName = sbn.packageName,
                                uid = sbn.uid,
                                isSystemApp = isSystemApplication(context, sbn.packageName),
                                canColorize = notification.canColorize(),
                            )
                        ) {
                            result = smallIcon
                            return@doBefore
                        }
                    }
                }
                XLog.i(TAG, "hooked NotifImageUtil.getSmallIcon (dynamic color mode check)")
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
                                preLTag?.let { iconView.setTag(ID_ICON_IS_PRE_L, it) }
                                val shouldTint = SystemUiNotificationPolicy.shouldApplyMonochromeTintToNotification(
                                    colorStatusBarIcon = options.colorStatusBarIcon,
                                    forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal,
                                    isMiPushManaged = isMiPushManaged,
                                    packageName = sbn.packageName,
                                    uid = sbn.uid,
                                    isSystemApp = isSystemApplication(iconView.context, sbn.packageName),
                                    canColorize = notification?.canColorize() ?: false,
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
                            if (args[0] != ID_ICON_IS_PRE_L) return@runCatching
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

        try {
            Notification.Builder::class.java.hookAllMethods("processSmallIconColor") {
                doBefore {
                    runCatching {
                        val builder = thisObject ?: return@doBefore
                        val context: Context = builder["mContext"] ?: return@doBefore
                        val smallIcon = args[0] as? Icon ?: return@doBefore
                        val contentView = args[1] as? RemoteViews ?: return@doBefore
                        val p = args[2]

                        val options = IslandPreferences.current()
                        val colorStatusBarIcon = options.colorStatusBarIcon
                        val forceGlobalStatusBarIcons = options.colorStatusBarIconGlobal
                        val notification = runCatching {
                            builder.callMethod("build") as? Notification
                                ?: builder["mN"] as? Notification
                        }.getOrNull()
                        val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(
                            notification?.extras
                        )
                        // Toggle OFF: force monochrome for MiPush (and global strong mode).
                        // Letting MIUI native run keeps TYPE_BITMAP app icons colored.
                        if (!colorStatusBarIcon) {
                            if (SystemUiNotificationPolicy.shouldForceMonochromeProcessSmallIcon(
                                    colorStatusBarIcon = colorStatusBarIcon,
                                    forceGlobalStatusBarIcons = forceGlobalStatusBarIcons,
                                    isMiPushManaged = isMiPushManaged,
                                )
                            ) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    contentView.setInt(android.R.id.icon, "setOriginalIconColor", 0)
                                }
                                result = true
                            }
                            return@doBefore
                        }

                        if (!SystemUiNotificationPolicy.shouldProcessSmallIconColor(
                                colorStatusBarIcon = colorStatusBarIcon,
                                forceGlobalStatusBarIcons = forceGlobalStatusBarIcons,
                                isMiPushManaged = isMiPushManaged,
                            )
                        ) {
                            return@doBefore
                        }

                        val colorUtil = builder.callMethod("getColorUtil") ?: return@doBefore
                        val isGrayscaleIcon = colorUtil.callMethod("isGrayscaleIcon", context, smallIcon) as? Boolean ?: return@doBefore

                        if (SystemUiNotificationPolicy.shouldApplySmallIconColor(
                                colorStatusBarIcon = colorStatusBarIcon,
                                forceGlobalStatusBarIcons = forceGlobalStatusBarIcons,
                                isMiPushManaged = isMiPushManaged,
                                isGrayscaleIcon = isGrayscaleIcon,
                            )
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val bgColor = builder.callMethod("getBackgroundColor", p) as? Int ?: 0
                                contentView.setInt(android.R.id.icon, "setBackgroundColor", bgColor)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                contentView.setInt(android.R.id.icon, "setOriginalIconColor", 1)
                            }
                            result = true
                        }
                    }.onFailure {
                        XLog.e(TAG, "processSmallIconColor hook failed", it)
                    }
                }
            }
            XLog.i(TAG, "processSmallIconColor hook installed successfully")
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to install processSmallIconColor hook", e)
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
