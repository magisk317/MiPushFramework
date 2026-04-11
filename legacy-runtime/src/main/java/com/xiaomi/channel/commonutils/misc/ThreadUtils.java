package com.xiaomi.channel.commonutils.misc;

import android.os.Looper;
import com.xiaomi.channel.commonutils.logger.MyLog;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/ThreadUtils.class */
public class ThreadUtils {
    private ThreadUtils() {
    }

    public static void checkNotUIThread() {
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            throw new RuntimeException("can't do this on ui thread");
        }
    }

    public static void checkNotUIThread(boolean z) {
        if (Looper.getMainLooper().getThread() == Thread.currentThread() && z) {
            throw new RuntimeException("can't do this on ui thread when debug_switch:" + z);
        }
        if (Looper.getMainLooper().getThread() != Thread.currentThread() || z) {
            return;
        }
        MyLog.w("can't do this on ui thread when debug_switch:" + z);
    }

    public static void checkUIThread() {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            throw new RuntimeException("must do this on ui thread");
        }
    }
}
