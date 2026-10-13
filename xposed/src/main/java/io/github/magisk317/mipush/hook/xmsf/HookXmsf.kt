package io.github.magisk317.mipush.hook.xmsf

import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PROCESS_NAME
import io.github.magisk317.mipush.common.logging.LogRoute
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.logging.XLog
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
        XLog.i(tag = TAG, route = LogRoute.XMSF_HOOK.id, message = "hook start pkg=${param.packageName} proc=${param.processName} commit=${BuildConfig.GIT_COMMIT}")
        if (HookPushNC.canHook(param.classLoader)) {
            XLog.i(
                tag = TAG,
                route = LogRoute.XMSF_HOOK.id,
                message = "HookPushNC target class found commit=${BuildConfig.GIT_COMMIT}",
            )
            HookPushNC.hook(param.classLoader)
            XLog.i(
                tag = TAG,
                route = LogRoute.XMSF_HOOK.id,
                message = "HookPushNC installed for pkg=${param.packageName} proc=${param.processName} commit=${BuildConfig.GIT_COMMIT}",
            )
        } else {
            XLog.w(
                tag = TAG,
                route = LogRoute.XMSF_HOOK.id,
                message = "HookPushNC target class missing for pkg=${param.packageName} proc=${param.processName} commit=${BuildConfig.GIT_COMMIT}",
            )
        }
    }
}
