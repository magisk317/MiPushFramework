package com.xiaomi.push.service

import android.os.SystemClock

class JobSchedulerThread(
    name: String,
    daemon: Boolean,
) : Thread() {
    companion object {
        private const val MAX_SLEEP_TIME = 500L
        private const val SLEEP_TIME_STEP = 50L
        private const val BLOCKED_THRESHOLD_MS = 600000L
    }

    private var cancelled = false
    private var finished = false

    @Volatile
    private var lastJob = 0L

    @Volatile
    private var executing = false

    private var currentSleepDuration = SLEEP_TIME_STEP
    private val taskQueue = JobSchedulerTaskQueue()

    private fun signalLocked() {
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        (this as java.lang.Object).notify()
    }

    private fun waitLocked(timeoutMs: Long? = null) {
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        val objectMonitor = this as java.lang.Object
        if (timeoutMs == null) {
            objectMonitor.wait()
        } else {
            objectMonitor.wait(timeoutMs)
        }
    }

    init {
        setName(name)
        isDaemon = daemon
        start()
    }

    fun insertTask(taskWrapper: JobSchedulerTaskWrapper) {
        taskQueue.insert(taskWrapper)
        signalLocked()
    }

    fun cancelScheduler() {
        synchronized(this) {
            cancelled = true
            taskQueue.reset()
            signalLocked()
        }
    }

    fun isBlocked(): Boolean {
        return executing && SystemClock.uptimeMillis() - lastJob > BLOCKED_THRESHOLD_MS
    }

    fun purge(): Int = taskQueue.purge()

    fun tasks(): JobSchedulerTaskQueue = taskQueue

    fun finish() {
        synchronized(this) {
            finished = true
            signalLocked()
        }
    }

    fun isCancelled(): Boolean = cancelled

    override fun run() {
        while (true) {
            try {
                var taskWrapper: JobSchedulerTaskWrapper? = null
                var shouldContinue = false

                synchronized(this) {
                    if (cancelled) {
                        return
                    }
                    if (taskQueue.isEmpty()) {
                        if (finished) {
                            return
                        }
                        try {
                            waitLocked()
                        } catch (_: InterruptedException) {
                        }
                        shouldContinue = true
                    } else {
                        val currentTime = JobSchedulerClock.getCurrentTime()
                        val task = taskQueue.minimum()
                        taskWrapper = task
                        synchronized(task.lock) {
                            if (task.cancelled) {
                                taskQueue.delete(0)
                                shouldContinue = true
                            } else {
                                var sleepDuration = task.`when` - currentTime
                                if (sleepDuration > 0) {
                                    if (sleepDuration > currentSleepDuration) {
                                        sleepDuration = currentSleepDuration
                                    }
                                    currentSleepDuration =
                                        minOf(currentSleepDuration + SLEEP_TIME_STEP, MAX_SLEEP_TIME)
                                    try {
                                        waitLocked(sleepDuration)
                                    } catch (_: InterruptedException) {
                                    }
                                    shouldContinue = true
                                } else {
                                    currentSleepDuration = SLEEP_TIME_STEP
                                    var taskIndex = 0
                                    if (taskQueue.minimum().`when` != task.`when`) {
                                        taskIndex = taskQueue.getTask(task)
                                    }
                                    if (task.cancelled) {
                                        taskQueue.delete(taskQueue.getTask(task))
                                        shouldContinue = true
                                    } else {
                                        task.setScheduledTime(task.`when`)
                                        taskQueue.delete(taskIndex)
                                        task.`when` = 0L
                                    }
                                }
                            }
                        }
                    }
                }

                if (shouldContinue) {
                    continue
                }

                lastJob = SystemClock.uptimeMillis()
                executing = true
                try {
                    checkNotNull(taskWrapper?.job).run()
                } finally {
                    executing = false
                }
            } catch (throwable: Throwable) {
                synchronized(this) {
                    cancelled = true
                }
                throw throwable
            }
        }
    }
}
