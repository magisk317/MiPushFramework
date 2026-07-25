package io.github.magisk317.mipush.hook.xmsf
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam

import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.xposed.HookClassNotFoundError
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.setHookIntField
import io.github.magisk317.xposed.logging.MagiskOtel

class UnlockFocusAuthHook : BaseHook() {
    override fun onLoadPackage(param: LoadParam) {
        val startedAt = System.nanoTime()
        val classLoader = param.classLoader
        IslandPreferences.startRefreshLoop()
        runCatching {
            val authSessionClass = try {
                findClass(AUTH_SESSION_CLASS, classLoader)
            } catch (_: HookClassNotFoundError) {
                XLog.d(TAG, "AuthSession class not found; focus auth hook skipped")
                emit(result = "skip", reason = "class_missing", durationMs = elapsedMs(startedAt))
                return
            } catch (_: ClassNotFoundException) {
                XLog.d(TAG, "AuthSession class not found; focus auth hook skipped")
                emit(result = "skip", reason = "class_missing", durationMs = elapsedMs(startedAt))
                return
            }
            val method = authSessionClass.declaredMethods.firstOrNull {
                it.name == "b" && it.parameterCount == 1
            }
            if (method == null) {
                XLog.w(TAG, "AuthSession.b(error) not found")
                emit(result = "skip", reason = "method_missing", durationMs = elapsedMs(startedAt))
                return
            }
            method.hook {
                doBefore {
                    if (!IslandPreferences.current().focusNotification) return@doBefore
                    val error = args.firstOrNull() ?: return@doBefore
                    runCatching {
                        setHookIntField(error, "a", 0)
                        result = thisObject?.callMethod("h")
                        emit(result = "ok", reason = "bypass", durationMs = 0L, stage = "focus_auth_bypass")
                    }.onFailure {
                        XLog.e(TAG, "focus auth bypass failed: ${it.message}", it)
                        emit(
                            result = "error",
                            reason = "bypass_failed",
                            durationMs = 0L,
                            stage = "focus_auth_bypass",
                            statusOk = false,
                            errorClass = it.javaClass.simpleName,
                        )
                    }
                }
            }
            XLog.i(TAG, "hooked AuthSession.b(error)")
            emit(result = "ok", reason = "hooked", durationMs = elapsedMs(startedAt))
        }.onFailure {
            XLog.e(TAG, "hook AuthSession failed: ${it.message}", it)
            emit(
                result = "error",
                reason = "hook_failed",
                durationMs = elapsedMs(startedAt),
                statusOk = false,
                errorClass = it.javaClass.simpleName,
            )
        }
    }

    private fun emit(
        result: String,
        reason: String,
        durationMs: Long,
        stage: String = "focus_auth_hook",
        statusOk: Boolean = true,
        errorClass: String? = null,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "hook",
            "stage" to stage,
            "reason" to reason,
        )
        if (errorClass != null) {
            attrs["error_class"] = errorClass
        }
        MagiskOtel.event(name = "hook.load", attributes = attrs, statusOk = statusOk)
    }

    private fun elapsedMs(startedAt: Long): Long {
        return ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
    }

    private companion object {
        private const val TAG = "UnlockFocusAuthHook"
        private const val AUTH_SESSION_CLASS = "com.xiaomi.xms.auth.AuthSession"
    }
}
