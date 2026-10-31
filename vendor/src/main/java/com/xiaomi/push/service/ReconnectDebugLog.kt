package com.xiaomi.push.service

import android.util.Log
import com.xiaomi.channel.commonutils.logger.MyLog

object ReconnectDebugLog {
    private const val TAG = "XMPushReconnect"

    /**
     * Routine lifecycle: policy decisions, job and timer bookkeeping, connection milestones.
     *
     * `Hooker.initMiSdkLogger` pins the release floor at `MyLog.WARN`, so these lines are dropped
     * from the shipped runtime log on purpose and only appear in debug/CI builds. `w` stays
     * reserved for reconnect failures and timeouts.
     */
    fun i(message: String) {
        runCatching { Log.i(TAG, message) }.onFailure { }
        MyLog.i(message)
    }

    fun w(message: String) {
        runCatching { Log.w(TAG, message) }.onFailure { }
        MyLog.w(message)
    }

    fun e(message: String) {
        runCatching { Log.e(TAG, message) }.onFailure { }
        MyLog.e(message)
    }
}
