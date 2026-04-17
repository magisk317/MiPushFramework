package com.xiaomi.push.service.timers

import android.content.Context
import com.xiaomi.channel.commonutils.misc.DateTimeHelper

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
