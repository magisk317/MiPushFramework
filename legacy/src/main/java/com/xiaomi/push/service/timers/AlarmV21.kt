package com.xiaomi.push.service.timers
import io.github.magisk317.mipush.protocol.model.*

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.XMJobService
import com.xiaomi.smack.SmackConfiguration

class AlarmV21(private val mContext: Context) : Alarm.IAlarm {
    private val mJobScheduler: JobScheduler = mContext.getSystemService("jobscheduler") as JobScheduler
    private var mStarted = false

    override fun isAlive(): Boolean = mStarted

    private fun register(delayMillis: Long) {
        val builder = JobInfo.Builder(1, ComponentName(mContext.packageName, XMJobService::class.java.name))
        builder.setMinimumLatency(delayMillis)
        builder.setOverrideDeadline(delayMillis)
        builder.setRequiredNetworkType(1)
        builder.setPersisted(false)
        MyLog.v("schedule Job = ${builder.build().id} in $delayMillis")
        mJobScheduler.schedule(builder.build())
    }

    override fun registerPing(force: Boolean) {
        if (force || mStarted) {
            val pingInterval = SmackConfiguration.pingInterval.toLong()
            val elapsedRealtime = if (force) {
                stop()
                pingInterval - (SystemClock.elapsedRealtime() % pingInterval)
            } else {
                pingInterval
            }
            mStarted = true
            register(elapsedRealtime)
        }
    }

    override fun stop() {
        mStarted = false
        mJobScheduler.cancel(1)
    }
}
