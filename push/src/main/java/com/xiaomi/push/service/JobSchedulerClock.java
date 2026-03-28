package com.xiaomi.push.service;

import android.os.SystemClock;

final class JobSchedulerClock {
    private static long currentTime;
    private static long lastTime;
    private static long timerId;

    static {
        long jElapsedRealtime = 0;
        if (SystemClock.elapsedRealtime() > 0) {
            jElapsedRealtime = SystemClock.elapsedRealtime();
        }
        currentTime = jElapsedRealtime;
        lastTime = jElapsedRealtime;
    }

    private JobSchedulerClock() {
    }

    static synchronized long getCurrentTime() {
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        long j = lastTime;
        if (jElapsedRealtime > j) {
            currentTime += jElapsedRealtime - j;
        }
        lastTime = jElapsedRealtime;
        return currentTime;
    }

    static synchronized long nextId() {
        long j = timerId;
        timerId = 1 + j;
        return j;
    }
}
