package io.github.magisk317.mipush.hook.documentsui

import android.os.Process
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.findHookClass
import io.github.magisk317.mipush.xposed.hookMethod

class DocumentsUiXSpaceHook {
    fun hook(classLoader: ClassLoader) {
        runCatching {
            val userIdClass = findHookClass("com.android.documentsui.base.UserId", classLoader)
            val stateClass = findHookClass("com.android.documentsui.base.State", classLoader)
            val getIdentifier = userIdClass.getDeclaredMethod("getIdentifier").apply { isAccessible = true }
            stateClass.hookMethod("canInteractWith", userIdClass) {
                doAfter {
                    if (result == true) return@doAfter
                    val userId = args.firstOrNull() ?: return@doAfter
                    val targetUserId = runCatching { getIdentifier.invoke(userId) as? Int }.getOrNull()
                        ?: return@doAfter
                    val currentUserId = Process.myUid() / PER_USER_RANGE
                    if (targetUserId == currentUserId) {
                        result = true
                    }
                }
            }
            XLog.i(TAG, "installed current-user canInteractWith hook")
        }.onFailure {
            XLog.e(TAG, "install hook failed", it)
        }
    }

    companion object {
        private const val TAG = "DocumentsUiXSpaceHook"
        private const val PER_USER_RANGE = 100000
    }
}
