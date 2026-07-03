package io.github.magisk317.mipush.hook.systemui
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.RemoteViews
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.get
import io.github.magisk317.xposed.getHookObjectField
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.hookAllMethods
import io.github.magisk317.xposed.hookMethod

class HookSystemUI : BaseHook() {
    companion object {
        private const val TAG = "HookSystemUI"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
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
                        val colorStatusBarIcon = IslandPreferences.current().colorStatusBarIcon
                        val sbn = args[1] as? StatusBarNotification ?: return@doBefore
                        val notification = sbn.notification ?: return@doBefore
                        val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(
                            notification.extras
                        )
                        if (SystemUiNotificationPolicy.shouldInterceptSmallIcon(colorStatusBarIcon, isMiPushManaged)) {
                            result = notification.smallIcon
                            return@doBefore
                        }
                    }
                }
                XLog.i(TAG, "hooked NotifImageUtil.getSmallIcon (dynamic color mode check)")
            } catch (e: Exception) {
                XLog.e(TAG, "Failed to hook NotifImageUtil.getSmallIcon", e)
            }

            try {
                classLoader.findClass("com.android.systemui.statusbar.notification.icon.IconManager")
                    .hookAllMethods("setIcon") {
                        doAfter {
                            runCatching {
                                val colorStatusBarIcon = IslandPreferences.current().colorStatusBarIcon
                                val sbn = statusBarNotificationFromEntry(args.firstOrNull()) ?: return@runCatching
                                val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(
                                    sbn.notification?.extras
                                )
                                if (!SystemUiNotificationPolicy.shouldForcePreLIconTag(colorStatusBarIcon, isMiPushManaged)) {
                                    return@runCatching
                                }
                                val iconView = args[2] as? View ?: return@runCatching
                                iconView.setTag(ID_ICON_IS_PRE_L, true)
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
                            val colorStatusBarIcon = IslandPreferences.current().colorStatusBarIcon
                            val sbn = statusBarNotificationFromEntry(thisObject) ?: return@runCatching
                            val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(
                                sbn.notification?.extras
                            )
                            if (!SystemUiNotificationPolicy.shouldForcePreLIconTag(colorStatusBarIcon, isMiPushManaged)) {
                                return@runCatching
                            }
                            args[1] = true
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

                        val colorStatusBarIcon = IslandPreferences.current().colorStatusBarIcon
                        val notification = runCatching {
                            builder.callMethod("build") as? Notification
                                ?: builder["mN"] as? Notification
                        }.getOrNull()
                        val isMiPushManaged = SystemUiNotificationPolicy.isMiPushManagedNotification(
                            notification?.extras
                        )
                        if (!colorStatusBarIcon || !isMiPushManaged) return@doBefore

                        val colorUtil = builder.callMethod("getColorUtil") ?: return@doBefore
                        val isGrayscaleIcon = colorUtil.callMethod("isGrayscaleIcon", context, smallIcon) as? Boolean ?: return@doBefore

                        if (SystemUiNotificationPolicy.shouldApplySmallIconColor(
                                colorStatusBarIcon = colorStatusBarIcon,
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
}
