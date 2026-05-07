package io.github.magisk317.mipush.hook.xmsf

import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.*

class HookXmsf {
    companion object {
        private const val TAG = "HookXmsf"
    }

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        XLog.i(TAG, "hook start pkg=${lpparam.packageName} proc=${lpparam.processName}")
        if (HookPushNC.canHook(lpparam.classLoader)) {
            HookPushNC.hook(lpparam.classLoader)
            XLog.i(TAG, "HookPushNC installed for pkg=${lpparam.packageName} proc=${lpparam.processName}")
        } else {
            XLog.w(TAG, "HookPushNC target class missing for pkg=${lpparam.packageName} proc=${lpparam.processName}")
        }
    }
}
