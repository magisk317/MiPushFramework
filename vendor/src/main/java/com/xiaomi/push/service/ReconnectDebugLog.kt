package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.xmsf.vendor.runtime.BuildConfig

/**
 * Routine lifecycle lines for the reconnect state machine: policy decisions, job and timer
 * bookkeeping, connection milestones.
 *
 * Only internal diagnostic builds emit them at all, and the text goes through `MyLog` into the
 * kit `XLog`, which owns both the shared 80-character budget and the release short-circuit.
 * `Hooker.initMiSdkLogger` pins the release floor at `MyLog.WARN`, so these levels are absent
 * from shipped logs by design; `w` stays reserved for reconnect failures and timeouts.
 */
object ReconnectDebugLog {
    private val BUILD_LOGGING_ENABLED = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD

    fun i(message: String) {
        if (BUILD_LOGGING_ENABLED) MyLog.i(message)
    }

    fun w(message: String) {
        if (BUILD_LOGGING_ENABLED) MyLog.w(message)
    }

    fun e(message: String) {
        if (BUILD_LOGGING_ENABLED) MyLog.e(message)
    }
}
