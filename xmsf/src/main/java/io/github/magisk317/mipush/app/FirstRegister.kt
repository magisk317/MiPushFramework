package io.github.magisk317.mipush.app

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
    private val logger = object {
        fun i(msg: String) = Napier.i(msg, tag = TAG)
        fun e(msg: String, t: Throwable? = null) = Napier.e(msg, t, tag = TAG)
    }

    override fun run() {
        Objects.requireNonNull(context)
        if (isRegistered(context)) {
            PushRuntime.observeRegistrationResult(
                packageName = context.packageName,
                success = true,
                source = "FirstRegister.run",
                reason = "reg_id_present"
            )
            logger.i("register successed")
            return
        }
        requestRegistration("FirstRegister.run", "initial_register")
        if (isRegistered(context)) {
            logger.i("register successed")
        } else {
            scheduleRetry(context, 0)
        }
        try {
            Thread.sleep(100L)
        } catch (e: InterruptedException) {
            logger.e("register push interrupted error", e)
        }
    }
}
