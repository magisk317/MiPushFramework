package io.github.magisk317.mipush.hook.systemui

import android.content.ComponentName
import android.content.ContextWrapper
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.findHookClass
import io.github.magisk317.xposed.getHookObjectField
import io.github.magisk317.xposed.hook

class HookSystemUIPlugin(
    private val pluginPackageName: String,
    private vararg val hookers: ISystemUIPluginHooker,
) {
    private var pluginHooksDispatched = false

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
                        io.github.magisk317.xposed.getHookObjectField(owner, "mComponentName") as? ComponentName
                    if (componentName?.packageName != pluginPackageName) return@doAfter
                    val pluginContext = result as? ContextWrapper ?: return@doAfter
                    val pluginLoader = pluginContext.classLoader ?: return@doAfter
                    val shouldDispatch = synchronized(this@HookSystemUIPlugin) {
                        if (pluginHooksDispatched) {
                            false
                        } else {
                            pluginHooksDispatched = true
                            true
                        }
                    }
                    if (!shouldDispatch) return@doAfter
                    unhook()
                    XLog.d(TAG, "hook [$pluginPackageName] by Plugin ClassLoader: [$pluginLoader]")
                    dispatchHookers(pluginLoader)
                }
            }
        } catch (e: Throwable) {
            XLog.e(
                TAG,
                "hook SystemUI Plugin [$pluginPackageName] observer setup failure: " + e.message,
                e
            )
        }
    }

    internal fun dispatchHookers(pluginLoader: ClassLoader) {
        hookers.forEach { hooker ->
            runCatching { hooker.hook(pluginLoader) }
                .onFailure {
                    XLog.e(
                        TAG,
                        "hook SystemUI Plugin [$pluginPackageName] with [${hooker::class.java.name}] failure: ${it.message}",
                        it,
                    )
                }
        }
    }

}
