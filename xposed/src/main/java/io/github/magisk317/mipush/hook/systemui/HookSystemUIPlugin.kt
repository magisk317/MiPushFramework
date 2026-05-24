package io.github.magisk317.mipush.hook.systemui

import android.content.ComponentName
import android.content.ContextWrapper
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.findHookClass
import io.github.magisk317.mipush.xposed.getField
import io.github.magisk317.mipush.xposed.hook

class HookSystemUIPlugin(
    private val pluginPackageName: String, private val hooker: ISystemUIPluginHooker
) {
    companion object {
        private const val TAG = "HookSystemUIPlugin"
    }

    fun hook(classLoader: ClassLoader) {
        try {
            val classPluginFactory = findHookClass(
                "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory",
                classLoader
            )
            classPluginFactory.declaredMethods.find { it.name == "createPluginContext" }!!.hook {
                doAfter {
                    val owner = thisObject ?: return@doAfter
                    val componentName =
                        owner.getField("mComponentName", ComponentName::class.java)
                    if (componentName!!.packageName == pluginPackageName) {
                        unhook()
                        val pluginContext = result as ContextWrapper
                        val pluginLoader = pluginContext.classLoader
                        XLog.d(TAG, "hook [$pluginPackageName] by Plugin ClassLoader: [$pluginLoader]")
                        hooker.hook(pluginLoader)
                    }
                }
            }
        } catch (e: Throwable) {
            XLog.e(
                TAG,
                "hook SystemUI Plugin [$pluginPackageName] with [${hooker.javaClass.name}] failure: " + e.message,
                e
            )
        }
    }

}
