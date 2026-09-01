package io.github.magisk317.mipush.app

import android.content.Context
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.control.PushControllerUtils
import io.github.magisk317.mipush.control.PushControllerUtils.pushRegistered
import io.github.magisk317.xposed.logging.MagiskOtel

class RetryRegister(
    private val context: Context,
    private val tryRegisterCount: Int,
    private val frameworkSelfRegistrationEnabled: (Context) -> Boolean =
        PushControllerUtils::isFrameworkSelfRegistrationEnabled,
) : Runnable {
    override fun run() {
        if (pushRegistered(context)) {
            PushRuntime.observeRegistrationResult(
                packageName = context.packageName,
                success = true,
                source = "RetryRegister.run",
                reason = "reg_id_present"
            )
            logI("register successed, stop retry")
            MagiskOtel.event(
                name = "push.register",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "retry",
                    "reason" to "reg_id_present",
                    "retry_index" to tryRegisterCount.toString(),
                ),
                statusOk = true,
            )
            return
        }
        if (!frameworkSelfRegistrationEnabled(context)) {
            logI("framework self-registration disabled; skip retry index=$tryRegisterCount")
            MagiskOtel.event(
                name = "push.register",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "retry",
                    "reason" to "framework_self_registration_disabled",
                    "retry_index" to tryRegisterCount.toString(),
                ),
                statusOk = true,
            )
            return
        }
        PushRuntime.requestFrameworkRegistration(
            source = "RetryRegister.run",
            reason = "retry_$tryRegisterCount"
        )
        val retry = tryRegisterCount + 1
        if (retry <= 10) {
            logI("register not successed, register again, retryIndex: $retry")
            MagiskOtel.event(
                name = "push.register",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "retry",
                    "reason" to "retry_again",
                    "retry_index" to retry.toString(),
                ),
                statusOk = true,
            )
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
        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to "error",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "retry",
                "reason" to "retry_exhausted",
                "retry_index" to tryRegisterCount.toString(),
            ),
            statusOk = false,
        )
    }
}
