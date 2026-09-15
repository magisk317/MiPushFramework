package com.xiaomi.channel.commonutils.misc

import android.os.Looper
import com.xiaomi.channel.commonutils.logger.MyLog

/*
 */
object ThreadUtils {
    @JvmStatic
    fun checkNotUIThread() {
        if (Looper.getMainLooper().thread == Thread.currentThread()) {
            throw RuntimeException("can't do this on ui thread")
        }
    }

    @JvmStatic
    fun checkNotUIThread(debugSwitch: Boolean) {
        if (Looper.getMainLooper().thread == Thread.currentThread() && debugSwitch) {
            throw RuntimeException("can't do this on ui thread when debug_switch:$debugSwitch")
        }
        if (Looper.getMainLooper().thread == Thread.currentThread() && !debugSwitch) {
            MyLog.w("can't do this on ui thread when debug_switch:$debugSwitch")
        }
    }

    @JvmStatic
    fun checkUIThread() {
        if (Looper.getMainLooper().thread != Thread.currentThread()) {
            throw RuntimeException("must do this on ui thread")
        }
    }
}
