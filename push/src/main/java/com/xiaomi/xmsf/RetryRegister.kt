package com.xiaomi.xmsf

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.xmsf.runtime.PushRuntime
import com.xiaomi.xmsf.push.control.PushControllerUtils
import com.xiaomi.xmsf.push.control.PushControllerUtils.pushRegistered

class RetryRegister(private val context: Context, private val tryRegisterCount: Int) : Runnable {
    override fun run() {
        if (pushRegistered(context)) {
            PushRuntime.observeRegistrationResult(
                packageName = context.packageName,
                success = true,
                source = "RetryRegister.run",
                reason = "reg_id_present"
            )
            MyLog.i("register successed, stop retry")
            return
        }
        PushRuntime.requestFrameworkRegistration(
            source = "RetryRegister.run",
            reason = "retry_$tryRegisterCount"
        )
        val retry = tryRegisterCount + 1
        if (retry <= 10) {
            MyLog.i("register not successed, register again, retryIndex: $retry")
            PushControllerUtils.registerPush(context, retry)
            return
        }
        PushRuntime.observeRegistrationResult(
            packageName = context.packageName,
            success = false,
            source = "RetryRegister.run",
            reason = "retry_exhausted"
        )
        MyLog.i("register not successed, but retry to many times, stop retry")
    }
}
