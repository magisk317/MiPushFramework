package com.xiaomi.channel.commonutils.misc

import android.content.Context
import android.content.SharedPreferences
import com.xiaomi.channel.commonutils.logger.MyLog
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/misc/ScheduledJobManager.java
 */
class ScheduledJobManager private constructor(context: Context) {
    private val executor = ScheduledThreadPoolExecutor(CORE_THREAD_POOL_SIZE)
    private val jobFutureMap: MutableMap<String, ScheduledFuture<*>> = HashMap()
    private val mapLock = Any()
    private val preferences: SharedPreferences = context.getSharedPreferences(SP_NAME, 0)

    abstract class Job : Runnable {
        abstract fun getJobId(): String
    }

    open class JobWrapper(@JvmField val job: Job) : Runnable {
        open fun onJobDone() {
        }

        open fun onJobStart() {
        }

        override fun run() {
            onJobStart()
            job.run()
            onJobDone()
        }
    }

    private fun getFutureByJobId(job: Job): ScheduledFuture<*>? = synchronized(mapLock) {
        jobFutureMap[job.getJobId()]
    }

    fun addOneShootJob(runnable: Runnable) {
        addOneShootJob(runnable, 0)
    }

    fun addOneShootJob(runnable: Runnable, delaySeconds: Int) {
        executor.schedule(runnable, delaySeconds.toLong(), TimeUnit.SECONDS)
    }

    fun addOneShootJob(job: Job): Boolean = addOneShootJob(job as Job?, 0)

    fun addOneShootJob(job: Job?, delaySeconds: Int): Boolean {
        if (job == null || getFutureByJobId(job) != null) {
            return false
        }
        val future = executor.schedule(
            object : JobWrapper(job) {
                override fun onJobDone() {
                    synchronized(mapLock) {
                        jobFutureMap.remove(this.job.getJobId())
                    }
                }
            },
            delaySeconds.toLong(),
            TimeUnit.SECONDS
        )
        synchronized(mapLock) {
            jobFutureMap[job.getJobId()] = future
        }
        return true
    }

    fun addRepeatJob(job: Job, periodSeconds: Int): Boolean = addRepeatJob(job, periodSeconds, 0)

    fun addRepeatJob(job: Job, periodSeconds: Int, initialDelaySeconds: Int): Boolean =
        addRepeatJob(job, periodSeconds, initialDelaySeconds, false)

    fun addRepeatJob(job: Job?, periodSeconds: Int, initialDelaySeconds: Int, fixedDelay: Boolean): Boolean {
        if (job == null || getFutureByJobId(job) != null) {
            return false
        }
        val jobKey = getJobKey(job.getJobId())
        val wrapper = object : JobWrapper(job) {
            override fun onJobDone() {
                if (!fixedDelay) {
                    preferences.edit().putLong(jobKey, System.currentTimeMillis()).commit()
                }
            }
        }
        var delay = initialDelaySeconds
        if (!fixedDelay) {
            val elapsed = abs(System.currentTimeMillis() - preferences.getLong(jobKey, 0L)) / 1000
            if (elapsed < periodSeconds - delay) {
                delay = (periodSeconds.toLong() - elapsed).toInt()
            }
        }
        return try {
            val future = executor.scheduleAtFixedRate(wrapper, delay.toLong(), periodSeconds.toLong(), TimeUnit.SECONDS)
            synchronized(mapLock) {
                jobFutureMap[job.getJobId()] = future
            }
            true
        } catch (e: Exception) {
            MyLog.e(e)
            true
        }
    }

    fun cancelJob(str: String): Boolean = synchronized(mapLock) {
        val scheduledFuture = jobFutureMap[str] ?: return@synchronized false
        jobFutureMap.remove(str)
        scheduledFuture.cancel(false)
    }

    companion object {
        private const val CORE_THREAD_POOL_SIZE = 1
        private const val SP_KEY_PREFIX = "last_job_time"
        private const val SP_NAME = "mipush_extra"

        @Volatile
        private var instance: ScheduledJobManager? = null

        @JvmStatic
        fun getInstance(context: Context): ScheduledJobManager {
            if (instance == null) {
                synchronized(ScheduledJobManager::class.java) {
                    if (instance == null) {
                        instance = ScheduledJobManager(context)
                    }
                }
            }
            return instance!!
        }

        @JvmStatic
        private fun getJobKey(str: String): String = SP_KEY_PREFIX + str
    }
}
