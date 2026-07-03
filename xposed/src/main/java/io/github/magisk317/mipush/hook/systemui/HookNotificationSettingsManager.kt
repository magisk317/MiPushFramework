package io.github.magisk317.mipush.hook.systemui

import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.xposed.findHookClass
import io.github.magisk317.xposed.hook
import java.lang.reflect.Method

class HookNotificationSettingsManager : ISystemUIPluginHooker {
    companion object {
        private const val TAG = "FocusNotification"
    }

    override fun hook(pluginLoader: ClassLoader) {
        try {
            XLog.d(TAG, "hook start")
            val classNotificationSettingsManager = findHookClass(
                "miui.systemui.notification.NotificationSettingsManager",
                pluginLoader
            )

            hookPackageFocusMethod(classNotificationSettingsManager, "canShowFocus")
            hookPackageFocusMethod(classNotificationSettingsManager, "canCustomFocus")

            XLog.d(TAG, "hook end")
        } catch (e: Throwable) {
            XLog.e(
                TAG,
                "hook NotificationSettingsManager failure: " + e.message,
                e
            )
        }
    }

    private fun hookPackageFocusMethod(owner: Class<*>, methodName: String) {
        val methods = owner.declaredMethods.filter { method ->
            method.name == methodName && method.packageNameArgIndex() >= 0
        }
        if (methods.isEmpty()) {
            XLog.w(TAG, "skip $methodName: no package-name signature found")
            return
        }
        methods.forEach { method ->
            val pkgArgIndex = method.packageNameArgIndex()
            XLog.d(
                TAG,
                "hook $methodName paramTypes=${method.parameterTypes.map { it.simpleName }} pkgArgIndex=$pkgArgIndex"
            )
            method.hook {
                replace {
                    val packageName = args[pkgArgIndex] as? String
                    val allowed = if (packageName.isNullOrBlank()) {
                        IslandPreferences.current().canInjectFocusPayload
                    } else {
                        IslandPreferences.current(packageName).canInjectFocusPayload
                    }
                    XLog.d(TAG, "$methodName pkg=$packageName -> $allowed")
                    allowed
                }
            }
        }
    }

    private fun Method.packageNameArgIndex(): Int {
        return parameterTypes.indexOfFirst { it == String::class.java }
    }
}
