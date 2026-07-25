package io.github.magisk317.mipush.hook.island

import android.app.Application
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.hookMethod
import io.github.magisk317.xposed.logging.MagiskOtel

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
            MagiskOtel.event(
                name = "hook.load",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "island_dispatch",
                    "reason" to "installed",
                ),
                statusOk = true,
            )
        }.onFailure {
            XLog.e(TAG, "hook Application.onCreate failed: ${it.message}", it)
            MagiskOtel.event(
                name = "hook.load",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "island_dispatch",
                    "reason" to it.javaClass.simpleName,
                ),
                statusOk = false,
            )
        }
    }

    private companion object {
        private const val TAG = "IslandDispatcherHook"
    }
}
