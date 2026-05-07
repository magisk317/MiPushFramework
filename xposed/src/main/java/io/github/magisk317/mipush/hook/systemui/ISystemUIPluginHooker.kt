package io.github.magisk317.mipush.hook.systemui

fun interface ISystemUIPluginHooker {
    fun hook(pluginLoader: ClassLoader)
}