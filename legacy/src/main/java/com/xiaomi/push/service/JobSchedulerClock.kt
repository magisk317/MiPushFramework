package com.xiaomi.push.service

import android.os.SystemClock

object JobSchedulerClock {
    private var currentTime: Long
    private var lastTime: Long
    private var timerId = 0L

    init {
        val elapsedRealtime = SystemClock.elapsedRealtime().takeIf { it > 0 } ?: 0L
        currentTime = elapsedRealtime
        lastTime = elapsedRealtime
    }

    @JvmStatic
    @Synchronized
    fun getCurrentTime(): Long {
        val elapsedRealtime = SystemClock.elapsedRealtime()
        if (elapsedRealtime > lastTime) {
            currentTime += elapsedRealtime - lastTime
        }
        lastTime = elapsedRealtime
        return currentTime
    }

    @JvmStatic
    @Synchronized
    fun nextId(): Long {
        val next = timerId
        timerId += 1
        return next
    }
}
