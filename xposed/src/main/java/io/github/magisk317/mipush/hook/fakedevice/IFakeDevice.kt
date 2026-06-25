package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.xposed.LoadParam

interface IFakeDevice {
    fun fake(lpparam: LoadParam): Boolean
}