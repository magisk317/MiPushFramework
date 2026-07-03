package io.github.magisk317.mipush.app

import android.content.Context
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.control.PushControllerUtils
import io.github.magisk317.mipush.control.PushControllerUtils.pushRegistered

class RetryRegister(private val context: Context, private val tryRegisterCount: Int) : Runnable {
    override fun run() {
        if (pushRegistered(context)) {
            PushRuntime.observeRegistrationResult(
                packageName = context.packageName,
                success = true,
                source = "RetryRegister.run",
                reason = "reg_id_present"
            )
            logI("register successed, stop retry")
            return
        }
        PushRuntime.requestFrameworkRegistration(
            source = "RetryRegister.run",
            reason = "retry_$tryRegisterCount"
        )
        val retry = tryRegisterCount + 1
        if (retry <= 10) {
            logI("register not successed, register again, retryIndex: $retry")
            PushControllerUtils.registerPush(context, retry)
            return
        }
        PushRuntime.observeRegistrationResult(
            packageName = context.packageName,
            success = false,
            source = "RetryRegister.run",
            reason = "retry_exhausted"
        )
        logI("register not successed, but retry to many times, stop retry")
    }
}
