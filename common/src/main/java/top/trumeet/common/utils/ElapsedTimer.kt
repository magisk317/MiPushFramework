package top.trumeet.common.utils

import android.os.SystemClock

class ElapsedTimer {
    private var start = time()

    fun start(): Long {
        start = time()
        return start
    }

    fun restart(): Long {
        val old = start
        start = time()
        return start - old
    }

    fun elapsed(): Long {
        return time() - start
    }

    private fun time(): Long {
        return SystemClock.elapsedRealtime()
    }
}
