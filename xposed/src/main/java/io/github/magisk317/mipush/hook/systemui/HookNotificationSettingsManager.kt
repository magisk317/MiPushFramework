package io.github.magisk317.mipush.hook.systemui

import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.xposed.findHookClass
import io.github.magisk317.xposed.hook
import java.lang.reflect.Method
import java.util.Collections

class HookNotificationSettingsManager : ISystemUIPluginHooker {
    companion object {
        private const val TAG = "FocusNotification"
        private val hookedMethods = Collections.synchronizedSet(mutableSetOf<String>())
        private val CLASS_NAMES = listOf(
            "com.miui.systemui.notification.NotificationSettingsManager",
            "miui.systemui.notification.NotificationSettingsManager",
        )
    }

    override fun hook(pluginLoader: ClassLoader) {
        XLog.d(TAG, "hook start")
        val owners = CLASS_NAMES.mapNotNull { className ->
            runCatching {
                findHookClass(className, pluginLoader)
            }.onFailure {
                XLog.d(TAG, "skip $className: ${it.message}")
            }.getOrNull()
        }.distinct()
        if (owners.isEmpty()) {
            XLog.w(TAG, "skip NotificationSettingsManager: no known class found")
            return
        }
        owners.forEach { owner ->
            hookPackageFocusMethod(owner, "canShowFocus")
            hookPackageFocusMethod(owner, "canCustomFocus")
        }
        XLog.d(TAG, "hook end")
    }

    private fun hookPackageFocusMethod(owner: Class<*>, methodName: String) {
        val methods = owner.declaredMethods.filter { method ->
            method.name == methodName &&
                method.returnsBoolean() &&
                method.packageNameArgIndex() >= 0
        }
        if (methods.isEmpty()) {
            XLog.w(TAG, "skip $methodName: no package-name signature found")
            return
        }
        methods.forEach { method ->
            val hookKey = method.hookKey()
            if (!hookedMethods.add(hookKey)) {
                XLog.d(TAG, "skip duplicate hook $hookKey")
                return@forEach
            }
            val pkgArgIndex = method.packageNameArgIndex()
            XLog.d(
                TAG,
                "hook ${owner.name}#$methodName paramTypes=${method.parameterTypes.map { it.simpleName }} " +
                    "pkgArgIndex=$pkgArgIndex"
            )
            method.hook {
                doAfter {
                    val originalAllowed = result as? Boolean ?: return@doAfter
                    val packageName = args[pkgArgIndex] as? String
                    val miPushAllowed = FocusNotificationPermissionPolicy.miPushPreferenceAllows(packageName)
                    val allowed = FocusNotificationPermissionPolicy.merge(
                        systemAllowed = originalAllowed,
                        miPushAllowed = miPushAllowed,
                    )
                    if (allowed != originalAllowed) {
                        XLog.d(
                            TAG,
                            "$methodName pkg=$packageName system=$originalAllowed mipush=$miPushAllowed -> $allowed"
                        )
                        result = allowed
                    }
                }
            }
        }
    }

    private fun Method.packageNameArgIndex(): Int {
        return parameterTypes.indexOfFirst { it == String::class.java }
    }

    private fun Method.returnsBoolean(): Boolean {
        return returnType == Boolean::class.javaPrimitiveType || returnType == Boolean::class.java
    }

    private fun Method.hookKey(): String {
        return "${declaringClass.name}#$name(${parameterTypes.joinToString(",") { it.name }})@" +
            System.identityHashCode(declaringClass.classLoader)
    }
}

internal object FocusNotificationPermissionPolicy {
    fun merge(systemAllowed: Boolean, miPushAllowed: Boolean): Boolean {
        return systemAllowed || miPushAllowed
    }

    fun miPushPreferenceAllows(packageName: String?): Boolean {
        return if (packageName.isNullOrBlank()) {
            IslandPreferences.current().canInjectFocusPayload
        } else {
            IslandPreferences.current(packageName).canInjectFocusPayload
        }
    }
}
