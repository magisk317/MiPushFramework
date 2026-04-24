package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.logger.MyLog
import java.util.concurrent.RejectedExecutionException

class JobScheduler {
    private val impl: JobSchedulerThread
    private val finalizer: FinalizerHelper

    private class FinalizerHelper(
        private val impl: JobSchedulerThread,
    ) {
        @Throws(Throwable::class)
        protected fun finalize() {
            impl.finish()
        }
    }

    abstract class Job(
        @JvmField val type: Int,
    ) : Runnable

    constructor() : this(false)

    constructor(name: String) : this(name, false)

    constructor(name: String, daemon: Boolean) {
        impl = JobSchedulerThread(name, daemon)
        finalizer = FinalizerHelper(impl)
    }

    constructor(daemon: Boolean) : this("Timer-${JobSchedulerClock.nextId()}", daemon)

    private fun scheduleImpl(job: Job, delayMs: Long) {
        synchronized(impl) {
            if (impl.isCancelled()) {
                throw IllegalStateException("Timer was canceled")
            }
            val currentTime = JobSchedulerClock.getCurrentTime() + delayMs
            if (currentTime < 0) {
                throw IllegalArgumentException("Illegal delay to start the TimerTask: $currentTime")
            }
            val taskWrapper = JobSchedulerTaskWrapper().apply {
                type = job.type
                this.job = job
                `when` = currentTime
            }
            impl.insertTask(taskWrapper)
        }
    }

    fun executeJobDelayed(job: Job, delayMs: Long) {
        require(delayMs >= 0) { "delay < 0: $delayMs" }
        scheduleImpl(job, delayMs)
    }

    fun executeJobNow(job: Job) {
        if (MyLog.getLogLevel() >= 1 || Thread.currentThread() == impl) {
            job.run()
            return
        }
        MyLog.e("run job outside job job thread")
        throw RejectedExecutionException("Run job outside job thread")
    }

    fun hasJob(type: Int): Boolean {
        synchronized(impl) {
            return impl.tasks().hasJob(type)
        }
    }

    fun hasJob(type: Int, job: Job): Boolean {
        synchronized(impl) {
            return impl.tasks().hasJob(type, job)
        }
    }

    fun isBlocked(): Boolean = impl.isBlocked()

    fun purge(): Int {
        synchronized(impl) {
            return impl.purge()
        }
    }

    fun quit() {
        MyLog.w("quit. finalizer:$finalizer")
        impl.cancelScheduler()
    }

    fun removeAllJobs() {
        synchronized(impl) {
            impl.tasks().reset()
        }
    }

    fun removeJobs(type: Int) {
        synchronized(impl) {
            impl.tasks().removeJobs(type)
        }
    }

    fun removeJobs(type: Int, job: Job) {
        synchronized(impl) {
            impl.tasks().removeJobs(type, job)
        }
    }
}
