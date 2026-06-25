package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.xposed.LoadParam

class FakeMiuiOnly : IFakeDevice {
    override fun fake(lpparam: LoadParam): Boolean {
        // 清空其他厂商特征，防止与小米身份冲突。
        fakeVendorFeatureProperties()
        return true
    }
}
