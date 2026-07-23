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
 * Applies the user's global focus-authorization bypass at HyperOS 3's authoritative boundary.
 *
 * FocusNotificationController normally sends a notification through SignatureChecker and then
 * AuthManager, which binds the stock XMSF auth service. The replacement XMSF runtime intentionally
 * does not expose that auth service. When the explicit bypass setting is enabled, reuse the
 * controller's success callback and skip both checks. With the setting off, this hook is inert.
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
                    if (!FocusNotificationPermissionPolicy.isGlobalBypassEnabled()) return@doBefore

                    val statusBarNotification = args[1] as? StatusBarNotification ?: return@doBefore
                    val packageName = args[2] as? String ?: statusBarNotification.packageName
                    val callback = args[4] ?: return@doBefore
                    val key = statusBarNotification.key

                    runCatching {
                        callback.callMethod("onAuthSuccess", key, packageName)
                        // fetchAuthResult is void. Setting a null result stops the original
                        // method after the controller has received its standard success signal.
                        result = null
                        XLog.i(TAG, "globally bypassed focus authorization pkg=$packageName key=$key")
                    }.onFailure { throwable ->
                        XLog.e(TAG, "focus authorization callback failed: ${throwable.message}", throwable)
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
