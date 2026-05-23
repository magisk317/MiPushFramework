package io.github.magisk317.mipush.hook.fakedevice

import android.app.Application
import io.github.magisk317.mipush.xposed.LoadParam
import io.github.magisk317.mipush.xposed.hookMethod

class CoolApk : XGPush() {
    override fun fake(lpparam: LoadParam): Boolean {
        Application::class.java.hookMethod("onCreate") {
            doAfter { super.fake(lpparam) }
        }
        return true
    }
}