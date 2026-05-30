package com.xiaomi.push.service

class JobSchedulerTaskWrapper {
    @JvmField
    var cancelled: Boolean = false

    @JvmField
    var job: JobScheduler.Job? = null

    @JvmField
    val lock: Any = Any()

    private var scheduledTime: Long = 0L

    @JvmField
    var type: Int = 0

    @JvmField
    var `when`: Long = 0L

    fun cancel(): Boolean {
        synchronized(lock) {
            val shouldCancel = !cancelled && `when` > 0
            cancelled = true
            return shouldCancel
        }
    }

    fun getWhen(): Long {
        synchronized(lock) {
            return `when`
        }
    }

    fun scheduledExecutionTime(): Long {
        synchronized(lock) {
            return scheduledTime
        }
    }

    fun setScheduledTime(value: Long) {
        synchronized(lock) {
            scheduledTime = value
        }
    }
}
