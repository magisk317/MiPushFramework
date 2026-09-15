package io.github.magisk317.mipush.hook.systemui

import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.findHookClass
import io.github.magisk317.xposed.hook
import java.lang.reflect.Method

/**
 * Applies the user's focus-authorization bypass only to MiPush-generated or MiPush-managed
 * notifications at HyperOS 3's authoritative boundary.
 *
 * FocusNotificationController normally sends a notification through SignatureChecker and then
 * AuthManager, which binds the XMSF auth service. Our replacement XMSF exposes an allow-all auth
 * service for native focus notifications (the commercial cloud gate is gone; per-app control stays
 * with the system-side canShowFocus/canCustomFocus gates). MiPush-managed notifications bypass
 * the bind entirely when the explicit focus switch is enabled; with the setting off this hook is
 * inert and the module never attaches focus payloads in the first place.
 */
class HookFocusAuthorization : ISystemUIPluginHooker {
    override fun hook(pluginLoader: ClassLoader) {
        val controller = runCatching {
            findHookClass(FOCUS_NOTIFICATION_CONTROLLER, pluginLoader)
        }.onFailure {
            XLog.d(TAG, "skip HyperOS focus authorization hook: ${it.message}")
        }.getOrNull() ?: return

        val methods = controller.declaredMethods.filter(::isFocusAuthorizationBoundary)
        if (methods.isEmpty()) {
            XLog.w(TAG, "skip HyperOS focus authorization hook: fetchAuthResult signature not found")
            return
        }
        methods.forEach { method ->
            XLog.i(TAG, "hook ${method.declaringClass.name}#${method.name}")
            method.hook {
                doBefore {
                    val statusBarNotification = args[1] as? StatusBarNotification ?: return@doBefore
                    val packageName = args[2] as? String ?: statusBarNotification.packageName
                    val extras = statusBarNotification.notification?.extras

                    // MiPush-generated/managed notifications bypass the missing stock auth
                    // service only when the explicit focus switch is enabled.
                    if (FocusNotificationPermissionPolicy.isGlobalBypassEnabled() &&
                        FocusNotificationPermissionPolicy.isMiPushFocusNotification(packageName, extras)
                    ) {
                        val callback = args[4] ?: return@doBefore
                        val key = statusBarNotification.key
                        runCatching {
                            callback.callMethod("onAuthSuccess", key, packageName)
                            // fetchAuthResult is void. A null result stops the original method
                            // after the controller has received its standard success signal.
                            result = null
                            XLog.i(TAG, "bypassed MiPush focus authorization pkg=$packageName key=$key")
                        }.onFailure { throwable ->
                            XLog.e(TAG, "focus authorization callback failed: ${throwable.message}", throwable)
                        }
                    }
                }
            }
        }
    }

    private fun isFocusAuthorizationBoundary(method: Method): Boolean {
        val parameterTypes = method.parameterTypes
        return method.name == "fetchAuthResult" &&
            method.returnType == Void.TYPE &&
            parameterTypes.size == 5 &&
            Context::class.java.isAssignableFrom(parameterTypes[0]) &&
            parameterTypes[1] == StatusBarNotification::class.java &&
            parameterTypes[2] == String::class.java &&
            parameterTypes[3] == Bundle::class.java &&
            parameterTypes[4].name == INFLATE_AND_AUTH_CALLBACK
    }

    private companion object {
        private const val TAG = "HookFocusAuthorization"
        private const val FOCUS_NOTIFICATION_CONTROLLER =
            "miui.systemui.notification.focus.FocusNotificationController"
        private const val INFLATE_AND_AUTH_CALLBACK =
            "miui.systemui.notification.focus.InflateAndAuthCallBack"
    }
}
