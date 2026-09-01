package io.github.magisk317.mipush.hook.xmsf

import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PROCESS_NAME
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.*

class HookXmsf : BaseHook() {
    companion object {
        private const val TAG = "HookXmsf"
    }

    override fun onHotReloading() {
        HookPushNC.stopReadyRetry()
    }

    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != XMSF_PACKAGE_NAME) return
        if (param.processName != XMSF_PROCESS_NAME) return
        XLog.i(TAG, "hook start pkg=${param.packageName} proc=${param.processName}")
        if (HookPushNC.canHook(param.classLoader)) {
            HookPushNC.hook(param.classLoader)
            XLog.i(TAG, "HookPushNC installed for pkg=${param.packageName} proc=${param.processName}")
        } else {
            XLog.w(TAG, "HookPushNC target class missing for pkg=${param.packageName} proc=${param.processName}")
        }
    }
}
