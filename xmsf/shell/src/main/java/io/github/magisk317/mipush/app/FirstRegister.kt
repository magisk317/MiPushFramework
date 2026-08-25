package io.github.magisk317.mipush.app

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.control.PushControllerUtils
import io.github.magisk317.mipush.control.PushControllerUtils.pushRegistered
import java.util.Objects
import io.github.magisk317.xposed.logging.MagiskOtel

class FirstRegister(
    private val context: Context,
    private val isRegistered: (Context) -> Boolean = ::pushRegistered,
    private val requestRegistration: (String, String) -> Boolean = { source, reason ->
        PushRuntime.requestFrameworkRegistration(source = source, reason = reason)
    },
    private val scheduleRetry: (Context, Int) -> Unit = PushControllerUtils::registerPush,
) : Runnable {
    private val tag = "FirstRegister"

    override fun run() {
        Objects.requireNonNull(context)
        if (isRegistered(context)) {
            PushRuntime.observeRegistrationResult(
                packageName = context.packageName,
                success = true,
                source = "FirstRegister.run",
                reason = "reg_id_present"
            )
            logI("register successed")
            MagiskOtel.event(
                name = "push.register",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "first",
                    "reason" to "reg_id_present",
                ),
                statusOk = true,
            )
            return
        }
        requestRegistration("FirstRegister.run", "initial_register")
        if (isRegistered(context)) {
            logI("register successed")
            MagiskOtel.event(
                name = "push.register",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "first",
                    "reason" to "registered",
                ),
                statusOk = true,
            )
        } else {
            scheduleRetry(context, 0)
            MagiskOtel.event(
                name = "push.register",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "first",
                    "reason" to "schedule_retry",
                    "retry_index" to "0",
                ),
                statusOk = true,
            )
        }
        try {
            Thread.sleep(100L)
        } catch (e: InterruptedException) {
            logE("register push interrupted error", e)
            MagiskOtel.event(
                name = "push.register",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "first",
                    "reason" to "interrupted",
                    "error_class" to e.javaClass.simpleName,
                ),
                statusOk = false,
            )
        }
    }
}
