package io.github.magisk317.mipush.app

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.control.PushControllerUtils
import io.github.magisk317.mipush.control.PushControllerUtils.pushRegistered
import java.util.Objects

class FirstRegister(
    private val context: Context,
    private val isRegistered: (Context) -> Boolean = ::pushRegistered,
    private val requestRegistration: (String, String) -> Boolean = { source, reason ->
        PushRuntime.requestFrameworkRegistration(source = source, reason = reason)
    },
    private val scheduleRetry: (Context, Int) -> Unit = PushControllerUtils::registerPush,
) : Runnable {
    private val TAG = "FirstRegister"

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
            return
        }
        requestRegistration("FirstRegister.run", "initial_register")
        if (isRegistered(context)) {
            logI("register successed")
        } else {
            scheduleRetry(context, 0)
        }
        try {
            Thread.sleep(100L)
        } catch (e: InterruptedException) {
            logE("register push interrupted error", e)
        }
    }
}
