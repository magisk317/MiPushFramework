package io.github.magisk317.mipush.hook.xmsf

import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.callMethod
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.hook
import io.github.magisk317.mipush.xposed.setHookIntField

class UnlockFocusAuthHook {
    fun hook(classLoader: ClassLoader) {
        runCatching {
            val authSessionClass = findClass(AUTH_SESSION_CLASS, classLoader)
            val method = authSessionClass.declaredMethods.firstOrNull {
                it.name == "b" && it.parameterCount == 1
            }
            if (method == null) {
                XLog.w(TAG, "AuthSession.b(error) not found")
                return
            }
            method.hook {
                doBefore {
                    val error = args.firstOrNull() ?: return@doBefore
                    runCatching {
                        setHookIntField(error, "a", 0)
                        result = thisObject?.callMethod("h")
                    }.onFailure {
                        XLog.e(TAG, "focus auth bypass failed: ${it.message}", it)
                    }
                }
            }
            XLog.i(TAG, "hooked AuthSession.b(error)")
        }.onFailure {
            XLog.e(TAG, "hook AuthSession failed: ${it.message}", it)
        }
    }

    private companion object {
        private const val TAG = "UnlockFocusAuthHook"
        private const val AUTH_SESSION_CLASS = "com.xiaomi.xms.auth.AuthSession"
    }
}
