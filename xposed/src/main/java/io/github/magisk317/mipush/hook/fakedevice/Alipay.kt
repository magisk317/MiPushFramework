package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.mipush.xposed.LoadParam
import io.github.magisk317.mipush.hook.XLog

class Alipay : Common() {
    companion object {
        private const val TAG = "Alipay"
    }

    override fun fake(lpparam: LoadParam): Boolean {
        XLog.d(TAG, "fake() called for ${lpparam.packageName}")
        super.fake(lpparam)
        return true
    }
}
