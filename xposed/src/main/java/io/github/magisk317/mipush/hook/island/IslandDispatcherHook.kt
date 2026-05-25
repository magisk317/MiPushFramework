package io.github.magisk317.mipush.hook.island

import android.app.Application
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.currentApplication
import io.github.magisk317.mipush.xposed.hookMethod

class IslandDispatcherHook {
    fun hook() {
        currentApplication()?.let {
            IslandDispatcher.register(it)
        }
        runCatching {
            Application::class.java.hookMethod("onCreate") {
                doAfter {
                    val application = thisObject as? Application ?: return@doAfter
                    IslandDispatcher.register(application)
                }
            }
        }.onFailure {
            XLog.e(TAG, "hook Application.onCreate failed: ${it.message}", it)
        }
    }

    private companion object {
        private const val TAG = "IslandDispatcherHook"
    }
}
