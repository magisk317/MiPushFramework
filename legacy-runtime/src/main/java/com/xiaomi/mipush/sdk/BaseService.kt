package com.xiaomi.mipush.sdk

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import com.xiaomi.channel.commonutils.logger.MyLog
import java.lang.ref.WeakReference

abstract class BaseService : Service() {
    private var mHandler: TimeoutHandler? = null

    class TimeoutHandler(weakReference: WeakReference<BaseService>) : Handler(Looper.getMainLooper()) {
        companion object {
            private const val TIME_OUT = 1000L
            private const val TIME_OUT_KILL_SELF = 1001
        }

        private var mWRService: WeakReference<BaseService> = weakReference

        override fun handleMessage(message: Message) {
            if (message.what == TIME_OUT_KILL_SELF) {
                val baseService = mWRService.get()
                if (baseService != null) {
                    MyLog.v("TimeoutHandler${baseService}  kill self")
                    if (!baseService.hasJob()) {
                        baseService.stopSelf()
                    } else {
                        MyLog.v("TimeoutHandler has job")
                        sendEmptyMessageDelayed(TIME_OUT_KILL_SELF, TIME_OUT)
                    }
                }
            }
        }

        fun reSendTimeoutMessage() {
            if (hasMessages(TIME_OUT_KILL_SELF)) {
                removeMessages(TIME_OUT_KILL_SELF)
            }
            sendEmptyMessageDelayed(TIME_OUT_KILL_SELF, TIME_OUT)
        }
    }

    protected abstract fun hasJob(): Boolean

    override fun onBind(intent: Intent): IBinder? = null

    private fun handleStart(intent: Intent?) {
        if (mHandler == null) {
            mHandler = TimeoutHandler(WeakReference(this))
        }
        mHandler!!.reSendTimeoutMessage()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleStart(intent)
        return START_NOT_STICKY
    }
}
