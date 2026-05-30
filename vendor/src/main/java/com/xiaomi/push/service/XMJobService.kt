package com.xiaomi.push.service

import android.app.Service
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Message
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.timers.Alarm

class XMJobService : Service() {
    private var jobBinder: IBinder? = null

    override fun onBind(intent: Intent): IBinder {
        return jobBinder ?: Binder()
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 21) {
            jobBinder = JobServiceImpl(this).binder
        }
        serviceInstance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
    }

    class JobServiceImpl(service: Service) : JobService() {
        val binder: Binder = JavaCalls.callMethod(this, "onBind", Intent()) as Binder
        private var handler: Handler? = null

        init {
            JavaCalls.callMethod(this, "attachBaseContext", service)
        }

        override fun onStartJob(params: JobParameters): Boolean {
            MyLog.w("Job started ${params.jobId}")
            startService(
                Intent(this, XMPushService::class.java).apply {
                    action = PushServiceConstants.ACTION_TIMER
                    `package` = packageName
                },
            )
            if (handler == null) {
                handler = JobHandler(this)
            }
            handler?.sendMessage(Message.obtain(handler, MSG_JOB_STARTED, params))
            return true
        }

        override fun onStopJob(params: JobParameters): Boolean {
            MyLog.w("Job stop ${params.jobId}")
            return false
        }

        private class JobHandler(
            private val service: JobService,
        ) : Handler(service.mainLooper) {
            override fun handleMessage(message: Message) {
                when (message.what) {
                    MSG_JOB_STARTED -> {
                        val params = message.obj as JobParameters
                        MyLog.w("Job finished ${params.jobId}")
                        service.jobFinished(params, false)
                        if (params.jobId == 1) {
                            Alarm.registerPing(false)
                        }
                    }
                }
            }
        }

        companion object {
            private const val MSG_JOB_STARTED = 1
        }
    }

    companion object {
        @JvmStatic
        var serviceInstance: Service? = null
            private set

        @JvmStatic
        fun getRunningService(): Service? = serviceInstance
    }
}
