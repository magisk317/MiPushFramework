package com.xiaomi.push.service

import android.util.Log
import com.xiaomi.channel.commonutils.logger.MyLog

object ReconnectDebugLog {
    private const val TAG = "XMPushReconnect"

    fun w(message: String) {
        runCatching { Log.w(TAG, message) }
        MyLog.w(message)
    }

    fun e(message: String) {
        runCatching { Log.e(TAG, message) }
        MyLog.e(message)
    }
}
