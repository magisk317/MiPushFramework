package com.xiaomi.push.mpcd

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.LoggerInterface
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.push.mpcd.job.AppIsInstalledCollectionJob
import com.xiaomi.push.mpcd.job.BroadcastActionCollectionjob
import com.xiaomi.push.mpcd.job.StorageCollectionJob
import com.xiaomi.push.mpcd.job.UploadJob
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ConfigKey

class JobController private constructor(private val context: Context) {
    private fun makeSurePeriodNotTooSmall(period: Int): Int = maxOf(60, period)

    private fun scheduleActivityTSJob(): Boolean {
        if (Build.VERSION.SDK_INT < 14) return false
        return try {
            val app = if (context is Application) context else context.applicationContext as Application
            app.registerActivityLifecycleCallbacks(ActivityLifecycleCallbacksImpl(context, (System.currentTimeMillis() / 1000).toString()))
            true
        } catch (e: Exception) {
            MyLog.e(e)
            false
        }
    }

    private fun trySchedulerCollectJob() {
        val scheduledJobManager = ScheduledJobManager.getInstance(context)
        val onlineConfig = OnlineConfig.getInstance(context)
        val sharedPreferences = context.getSharedPreferences("mipush_extra", 0)
        val currentTime = System.currentTimeMillis()
        var firstTryTs = sharedPreferences.getLong(KEY_FIRST_TRY_COLLECT_TIMESTAMP, currentTime)
        if (firstTryTs == currentTime) {
            sharedPreferences.edit().putLong(KEY_FIRST_TRY_COLLECT_TIMESTAMP, currentTime).commit()
        }
        if (kotlin.math.abs(currentTime - firstTryTs) < FIRST_COLLECT_JOB_DELAY) return

        uploadDC(onlineConfig, scheduledJobManager, false)

        if (onlineConfig.getBooleanValue(ConfigKey.StorageCollectionSwitch.value, true)) {
            val period = makeSurePeriodNotTooSmall(
                onlineConfig.getIntValue(ConfigKey.StorageCollectionFrequency.value, 86400)
            )
            scheduledJobManager.addRepeatJob(StorageCollectionJob(context, period), period, 0)
        }

        val appInstalledSwitch = onlineConfig.getBooleanValue(ConfigKey.AppIsInstalledCollectionSwitch.value, false)
        val appInstalledList = onlineConfig.getStringValue(ConfigKey.AppIsInstalledList.value, "") ?: ""
        if (appInstalledSwitch && !TextUtils.isEmpty(appInstalledList)) {
            val period = makeSurePeriodNotTooSmall(
                onlineConfig.getIntValue(ConfigKey.AppIsInstalledCollectionFrequency.value, 86400)
            )
            scheduledJobManager.addRepeatJob(AppIsInstalledCollectionJob(context, period, appInstalledList), period, 0)
        }

        if (onlineConfig.getBooleanValue(ConfigKey.BroadcastActionCollectionSwitch.value, true)) {
            val period = makeSurePeriodNotTooSmall(
                onlineConfig.getIntValue(ConfigKey.BroadcastActionCollectionFrequency.value, 900)
            )
            scheduledJobManager.addRepeatJob(BroadcastActionCollectionjob(context, period), period, 0)
        }

        if (onlineConfig.getBooleanValue(ConfigKey.ActivityTSSwitch.value, false)) {
            scheduleActivityTSJob()
        }

        uploadDC(onlineConfig, scheduledJobManager, true)
    }

    private fun uploadDC(onlineConfig: OnlineConfig, scheduledJobManager: ScheduledJobManager, isRepeat: Boolean) {
        if (onlineConfig.getBooleanValue(ConfigKey.UploadSwitch.value, true)) {
            val uploadJob = UploadJob(context)
            if (isRepeat) {
                scheduledJobManager.addRepeatJob(
                    uploadJob,
                    makeSurePeriodNotTooSmall(onlineConfig.getIntValue(ConfigKey.UploadFrequency.value, 86400))
                )
            } else {
                scheduledJobManager.addOneShootJob(uploadJob as ScheduledJobManager.Job)
            }
        }
    }

    fun schedulerJob() {
        ScheduledJobManager.getInstance(context).addOneShootJob { trySchedulerCollectJob() }
    }

    companion object {
        private const val FIRST_COLLECT_JOB_DELAY = 172800000
        private const val KEY_FIRST_TRY_COLLECT_TIMESTAMP = "first_try_ts"
        private const val MIN_DELAY = 0

        @Volatile
        private var instance: JobController? = null

        fun getInstance(context: Context): JobController {
            if (instance == null) {
                synchronized(JobController::class.java) {
                    if (instance == null) {
                        instance = JobController(context)
                    }
                }
            }
            return instance!!
        }
    }
}
