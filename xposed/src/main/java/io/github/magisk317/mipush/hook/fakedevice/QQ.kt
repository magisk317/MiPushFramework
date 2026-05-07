package io.github.magisk317.mipush.hook.fakedevice

import de.robv.android.xposed.callbacks.XC_LoadPackage

class QQ : Common() {

    override fun fake(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        if (lpparam.packageName == lpparam.processName || lpparam.processName.endsWith(":MSF")) {
            return super.fake(lpparam)
        }
        return false
    }
}