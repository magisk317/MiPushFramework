package com.xiaomi.push.service.timers
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import io.github.magisk317.mipush.common.compat.AlarmManagerTimerCompat

open class AlarmManagerTimer(context: Context) : Alarm.IAlarm {
    private val delegate = AlarmManagerTimerCompat(context)

    open fun getPingInteval(): Long = 0L

    override fun isAlive(): Boolean = delegate.isAlive()

    override fun registerPing(force: Boolean) = delegate.registerPing(force)

    override fun stop() = delegate.stop()
}
