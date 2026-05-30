package com.xiaomi.channel.commonutils.misc

import android.os.Looper
import com.xiaomi.channel.commonutils.logger.MyLog

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/misc/ThreadUtils.java
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
