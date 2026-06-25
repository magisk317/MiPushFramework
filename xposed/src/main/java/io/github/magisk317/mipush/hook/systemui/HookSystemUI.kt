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
import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.get
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.hookAllMethods
import io.github.magisk317.xposed.hookMethod

class HookSystemUI : BaseHook() {
    companion object {
        private const val TAG = "HookSystemUI"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val EXTRA_TARGET_PACKAGE = "target_package"
        private const val EXTRA_MIUI_TARGET_PACKAGE = "miui.targetPkg"
        private const val EXTRA_XMSF_TARGET_PACKAGE = "xmsf_target_package"
        private const val EXTRA_MOCK_REPLAY_RECEIPT = "mipush_mock_replay_receipt"
        private const val EXTRA_MOCK_REPLAY_SOURCE_PACKAGE = "mipush_mock_replay_source_package"
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // getSmallIcon hook disabled for monochrome investigation
            // When colorStatusBarIcon is true (color mode), we need this hook to intercept
            // and return notification.smallIcon. For now, only install when color mode is on.
            if (IslandPreferences.current().colorStatusBarIcon) {
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
                            val sbn = args[1] as? StatusBarNotification ?: return@doBefore
                            val notification = sbn.notification ?: return@doBefore
                            if (isMiPushManagedNotification(sbn)) {
                                result = notification.smallIcon
                                return@doBefore
                            }
                        }
                    }
                    XLog.i(TAG, "hooked NotifImageUtil.getSmallIcon (color mode active)")
                } catch (e: Exception) {
                    XLog.e(TAG, "Failed to hook NotifImageUtil.getSmallIcon", e)
                }
            } else {
                XLog.i(TAG, "skipped NotifImageUtil.getSmallIcon hook (monochrome mode)")
            }

            try {
                classLoader.findClass("com.android.systemui.statusbar.notification.icon.IconManager")
                    .hookAllMethods("setIcon") {
                        doAfter {
                            runCatching {
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
                        if (args[0] == ID_ICON_IS_PRE_L) {
                            args[1] = true
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

                        // processSmallIconColor: always run (no toggle check)
                        // This ensures MIUI renders bitmap icons correctly via icon_is_pre_L

                        val colorUtil = builder.callMethod("getColorUtil") ?: return@doBefore
                        val isGrayscaleIcon = colorUtil.callMethod("isGrayscaleIcon", context, smallIcon) as? Boolean ?: return@doBefore

                        if (!isGrayscaleIcon) {
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

    private fun isMiPushManagedNotification(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification?.extras ?: return false
        return extras.containsKey(EXTRA_TARGET_PACKAGE) ||
            extras.containsKey(EXTRA_MIUI_TARGET_PACKAGE) ||
            extras.containsKey(EXTRA_XMSF_TARGET_PACKAGE) ||
            extras.getBoolean(EXTRA_MOCK_REPLAY_RECEIPT, false) ||
            extras.getString(EXTRA_MOCK_REPLAY_SOURCE_PACKAGE)?.isNotBlank() == true ||
            extras.getString(IslandDispatchContract.SOURCE_PACKAGE)?.isNotBlank() == true ||
            extras.getString(IslandDispatchContract.OWNER) == IslandDispatchContract.OWNER_MARKER
    }

}
