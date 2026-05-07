package io.github.magisk317.mipush.hook.fakedevice

import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.magisk317.mipush.hook.XLog

class Alipay : Common() {
    companion object {
        private const val TAG = "Alipay"
    }

    override fun fake(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        XLog.d(TAG, "fake() called for ${lpparam.packageName}")
        super.fake(lpparam)
        return true
    }
}
