package io.github.magisk317.mipush.hook.systemui

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
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
        hookCustomAppIcon(pluginLoader)
        XLog.d(TAG, "hook end")
    }

    /**
     * Android 17 gates miui.appIcon behind config_canCustomNotificationAppIcon. Reuse the
     * existing global focus-auth bypass switch as an explicit compatibility escape hatch, but
     * only for notifications whose trusted producer is our XMSF package.
     */
    private fun hookCustomAppIcon(pluginLoader: ClassLoader) {
        val owner = runCatching {
            findHookClass(
                "com.android.systemui.statusbar.notification.utils.NotifImageUtil",
                pluginLoader,
            )
        }.onFailure {
            XLog.d(TAG, "skip custom app icon bypass: ${it.message}")
        }.getOrNull() ?: return
        owner.declaredMethods.filter { method ->
            method.name == "getCustomAppIcon" &&
                method.parameterTypes.contentEquals(arrayOf(Notification::class.java, Context::class.java)) &&
                Drawable::class.java.isAssignableFrom(method.returnType)
        }.forEach { method ->
            method.hook {
                doAfter {
                    if (result != null || !FocusNotificationPermissionPolicy.isGlobalBypassEnabled()) return@doAfter
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
                    result = runCatching { icon.loadDrawable(context) }.onSuccess {
                        if (it != null) XLog.d(TAG, "bypassed custom app icon whitelist for XMSF")
                    }.getOrNull()
                }
            }
        }
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

    /**
     * The focus-authorization switch is deliberately global. It controls SystemUI's focus
     * authorization boundary, whereas per-package island options only control this project's
     * generated payloads.
     */
    fun isGlobalBypassEnabled(): Boolean = IslandPreferences.current().focusNotification

    fun miPushPreferenceAllows(@Suppress("UNUSED_PARAMETER") packageName: String?): Boolean =
        isGlobalBypassEnabled()
}
