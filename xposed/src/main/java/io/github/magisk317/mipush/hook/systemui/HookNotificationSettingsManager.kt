package io.github.magisk317.mipush.hook.systemui

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
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
            hookPackageFocusMethod(owner, "canShowFocusState")
            hookPackageFocusMethod(owner, "canShowFocusStateApp")
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
                method.returnsFocusState() &&
                method.packageNameArgIndex() >= 0
        }
        if (methods.isEmpty()) {
            XLog.d(TAG, "skip $methodName: no supported package-name signature found")
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
                    "returnType=${method.returnType.simpleName} pkgArgIndex=$pkgArgIndex"
            )
            method.hook {
                doAfter {
                    val packageName = args[pkgArgIndex] as? String
                    val miPushAllowed = FocusNotificationPermissionPolicy.miPushPreferenceAllows(packageName)
                    when (val original = result) {
                        is Boolean -> {
                            val allowed = FocusNotificationPermissionPolicy.merge(
                                systemAllowed = original,
                                miPushAllowed = miPushAllowed,
                            )
                            if (allowed != original) {
                                XLog.d(
                                    TAG,
                                    "$methodName pkg=$packageName system=$original mipush=$miPushAllowed -> $allowed"
                                )
                                result = allowed
                            }
                        }
                        is Int -> {
                            val state = FocusNotificationPermissionPolicy.mergeState(original, miPushAllowed)
                            if (state != original) {
                                XLog.d(
                                    TAG,
                                    "$methodName pkg=$packageName system=$original mipush=$miPushAllowed -> $state"
                                )
                                result = state
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Method.packageNameArgIndex(): Int {
        return parameterTypes.indexOfFirst { it == String::class.java }
    }

    private fun Method.returnsFocusState(): Boolean {
        return returnsBoolean() || returnType == Int::class.javaPrimitiveType || returnType == Int::class.java
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

    fun mergeState(systemState: Int, miPushAllowed: Boolean): Int {
        return if (miPushAllowed) 1 else systemState
    }

    fun isGlobalBypassEnabled(): Boolean = IslandPreferences.current().focusNotification

    /**
     * The focus-authorization switch enables this module's generated/managed notifications, not
     * every application's native Dynamic Island. The dispatcher posts from SystemUI, while some
     * legacy managed notifications still arrive from XMSF.
     *
     * Keep this package gate narrow: cf8105902 established scoped island hooks, while the later
     * global implementation caused native SMS/weather islands to enter the MiPush auth path.
     */
    fun miPushPreferenceAllows(packageName: String?): Boolean =
        isGlobalBypassEnabled() &&
            // SystemUI is the trusted proxy publisher; XMSF is the legacy direct publisher.
            (packageName == XMSF_PACKAGE_NAME || packageName == SYSTEM_UI_PACKAGE)

    /**
     * Identifies a notification for which MiPush may use the focus authorization bypass. The
     * explicit extras markers cover the SystemUI-posted proxy; the XMSF package covers legacy
     * direct posts. Native notifications from SMS, weather, and other apps must remain on the
     * stock authorization path even when the global MiPush focus switch is enabled.
     */
    fun isMiPushFocusNotification(packageName: String?, extras: Bundle?): Boolean {
        if (packageName == XMSF_PACKAGE_NAME) return true
        return extras != null && SystemUiNotificationPolicy.isMiPushManagedNotification(extras)
    }

    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
}
