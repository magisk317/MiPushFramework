package com.xiaomi.push.service.timers

import android.content.Context
import com.magisk317.compat.AlarmManagerTimerCompat

class AlarmManagerTimer(context: Context) : Alarm.IAlarm {
    private val delegate = AlarmManagerTimerCompat(context)

    override fun isAlive(): Boolean = delegate.isAlive()

    override fun registerPing(force: Boolean) = delegate.registerPing(force)

    override fun stop() = delegate.stop()
}
