package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.mipush.xposed.LoadParam

class QQ : Common() {

    override fun fake(lpparam: LoadParam): Boolean {
        if (lpparam.packageName == lpparam.processName || lpparam.processName.endsWith(":MSF")) {
            return super.fake(lpparam)
        }
        return false
    }
}