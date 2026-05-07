package io.github.magisk317.mipush.hook.fakedevice

import de.robv.android.xposed.callbacks.XC_LoadPackage

class FakeMiuiOnly : IFakeDevice {
    override fun fake(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        // 清空华为 EMUI、魅族 Flyme、OPPO ColorOS 特征，防止与小米身份冲突
        fakeProperty(Property.EMUI_API)
        fakeProperty(Property.EMUI_VERSION)
        fakeProperty(Property.FLYME_VERSION_NAME)
        fakeProperty(Property.FLYME_VERSION_CODE)
        fakeProperty(Property.COLOROS_BUILD_VERSION_OLD)
        fakeProperty(Property.COLOROS_BUILD_VERSION)
        return true
    }
}