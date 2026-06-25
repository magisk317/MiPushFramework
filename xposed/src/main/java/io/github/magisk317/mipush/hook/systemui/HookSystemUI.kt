package io.github.magisk317.mipush.hook.systemui

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.mipush.xposed.callMethod
import io.github.magisk317.mipush.xposed.currentApplication
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.get
import io.github.magisk317.mipush.xposed.hook
import io.github.magisk317.mipush.xposed.hookAllMethods
import io.github.magisk317.mipush.xposed.hookMethod

class HookSystemUI {
    companion object {
        private const val TAG = "HookSystemUI"
    }

    private val ID_ICON_IS_PRE_L: Int by lazy {
        val app = currentApplication() ?: return@lazy 0
        app.resources.getIdentifier("icon_is_pre_L", "id", app.packageName)
    }

    fun hook(classLoader: ClassLoader) {
        MiuiHeaderAppIconHook().hook(classLoader)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                classLoader.findClass("com.android.systemui.statusbar.notification.icon.IconManager")
                    .hookAllMethods("setIcon") {
                        doAfter {
                            runCatching {
                                val iconView = args[2] as? View ?: return@doAfter
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

        Notification.Builder::class.java.hookAllMethods("processSmallIconColor") {
            doBefore {
                runCatching {
                    val builder = thisObject ?: return@doBefore
                    val context: Context = builder["mContext"] ?: return@doBefore
                    val smallIcon = args[0] as? Icon ?: return@doBefore
                    val contentView = args[1] as? RemoteViews ?: return@doBefore
                    val p = args[2]

                    if (!IslandPreferences.current().colorStatusBarIcon) {
                        // Toggle OFF: force monochrome, prevent MIUI from preserving color
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            contentView.setInt(android.R.id.icon, "setOriginalIconColor", 0)
                        }
                        result = true
                        return@doBefore
                    }

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
    }

}
