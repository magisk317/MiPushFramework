package io.github.magisk317.mipush.hook.fakedevice

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.github.magisk317.mipush.xposed.LoadParam
import io.github.magisk317.mipush.xposed.hookMethod

class PinDuoDuo : Common() {
    companion object {
        private const val TAG = "PddCommon"
    }

    override fun fake(lpparam: LoadParam): Boolean {
        super.fake(lpparam)
        Application::class.java.hookMethod("attach", Context::class.java) {
            doAfter {
                val context: Context = thisObject as Context
                val hwPushReceiver = ComponentName(context, "com.aimi.android.common.push.huawei.HwPushReceiver")
                context.packageManager.setComponentEnabledSetting(hwPushReceiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0)
            }
        }
        return true
    }
}