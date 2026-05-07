package com.xiaomi.push.service.timers

import android.content.Context
import com.xiaomi.channel.commonutils.misc.DateTimeHelper

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/ia/j.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/service/timers/HybridTimer.java
 * Stock class name is obfuscated as ia.j; this file keeps the deobfuscated HybridTimer API.
 */
/**
 * HybridTimer implements Alarm.IAlarm directly since AlarmManagerTimer
 * is in the push module which is not a dependency of legacy-runtime.
 */
class HybridTimer(context: Context) : Alarm.IAlarm {
    private val delegate = AlarmV21(context)

    override fun isAlive(): Boolean = delegate.isAlive()

    override fun registerPing(force: Boolean) = delegate.registerPing(force)

    override fun stop() = delegate.stop()

    fun getPingInteval(): Long = pingInterval

    companion object {
        private var pingInterval: Long = DateTimeHelper.HOUR_IN_MS.toLong()
    }
}
