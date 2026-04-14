package com.xiaomi.push.service.timers

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.XMJobService

object Alarm {
    const val SYSTEM_ALARM = 0
    const val HYBRID_ALARM = 2

    private const val XMSERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE"
    private val XMSERVICE = XMJobService::class.java.canonicalName
    private var sLevel = 0
    private var sAlarmInstance: IAlarm? = null

    interface IAlarm {
        fun isAlive(): Boolean
        fun registerPing(force: Boolean)
        fun stop()
    }

    @JvmStatic
    fun changePolicy(context: Context, level: Int) {
        synchronized(this) {
            val oldLevel = sLevel
            if (!PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(context.packageName)) {
                sLevel = if (level == 2) 2 else 0
            }
            if (oldLevel != sLevel && sLevel == 2) {
                stop()
            }
        }
    }

    @JvmStatic
    fun setIAlarm(alarm: IAlarm?) {
        sAlarmInstance = alarm
    }

    @JvmStatic
    fun initialize(context: Context) {
        // Implementation set by product layer
    }

    @JvmStatic
    fun isAlive(): Boolean = synchronized(this) {
        sAlarmInstance?.isAlive() ?: false
    }

    @JvmStatic
    fun registerPing(force: Boolean) {
        synchronized(this) {
            val alarm = sAlarmInstance
            if (alarm == null) {
                MyLog.w("timer is not initialized")
                return
            }
            MyLog.v("register alarm. ($force)")
            alarm.registerPing(force)
        }
    }

    @JvmStatic
    fun stop() {
        synchronized(this) {
            sAlarmInstance?.let {
                MyLog.v("stop alarm.")
                it.stop()
            }
        }
    }
}
