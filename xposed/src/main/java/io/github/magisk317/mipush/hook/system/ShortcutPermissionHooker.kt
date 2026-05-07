package io.github.magisk317.mipush.hook.system

import android.app.AndroidAppHelper
import android.content.pm.ShortcutInfo
import android.os.Binder
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findMethodExact
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.xposed.HookCallback
import io.github.magisk317.mipush.xposed.getMiPushExtra
import io.github.magisk317.mipush.xposed.hook
import io.github.magisk317.mipush.xposed.setMiPushExtra

object ShortcutPermissionHooker {
    private var xmsfUid = -1
    private fun getXmsfUid(): Int {
        if (xmsfUid == -1) {
            val context = AndroidAppHelper.currentApplication()
            if (context != null) {
                runCatching {
                    xmsfUid = context.packageManager.getPackageUid(XMSF_PACKAGE_NAME, 0)
                }
            }
        }
        return xmsfUid
    }

    private fun fromXmsf(): Boolean {
        val uid = getXmsfUid()
        if (uid == -1) return false
        return try {
            Binder.getCallingUid() == uid
        } catch (e: Throwable) {
            false
        }
    }

    private fun hookPermission(targetPackageNameParamIndex: Int, hookExtra: (XC_MethodHook.MethodHookParam.() -> Unit)? = null): HookCallback = {
        replace {
            var token: Long? = null
            if (fromXmsf()) {
                token = Binder.clearCallingIdentity()
                hookExtra?.invoke(this)
            }
            try {
                XposedBridge.invokeOriginalMethod(method, thisObject, args)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException ?: e.cause ?: e
            } finally {
                if (token != null) {
                    Binder.restoreCallingIdentity(token)
                }
            }
        }
    }

    fun hook(classShortcutService: Class<*>) {
        //    void pushDynamicShortcut(String packageName, in ShortcutInfo shortcut, int userId);
        findMethodExact(classShortcutService, "pushDynamicShortcut", String::class.java, ShortcutInfo::class.java, Int::class.java)
            .hook(hookPermission(0))

        //    int getMaxShortcutCountPerActivity(String packageName, int userId);
        findMethodExact(classShortcutService, "getMaxShortcutCountPerActivity", String::class.java, Int::class.java)
            .hook(hookPermission(0))

        // verifyCaller 已被移除：该方法会在 securitymanager 进程未初始化时被触发，
        // 导致 b0.a<clinit> 里 Context 为 null，引发 NoClassDefFoundError，
        // 进而导致 xmsf 反复 ANR 死循环。
    }
}
