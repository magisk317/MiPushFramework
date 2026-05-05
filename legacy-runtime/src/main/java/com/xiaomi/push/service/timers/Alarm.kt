package com.xiaomi.push.service.timers

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.XMJobService

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/ia/b.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/service/timers/Alarm.java
 * Stock class name is obfuscated as ia.b; this file keeps the deobfuscated com.xiaomi.push.service.timers.Alarm API.
 */
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
                sAlarmInstance = HybridTimer(context)
            }
        }
    }

    @JvmStatic
    fun setIAlarm(alarm: IAlarm?) {
        sAlarmInstance = alarm
    }

    @JvmStatic
    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        sAlarmInstance = createProductAlarm(applicationContext) ?: AlarmV21(applicationContext)
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

    private fun createProductAlarm(context: Context): IAlarm? {
        return runCatching {
            val clazz = Class.forName("com.xiaomi.push.service.timers.AlarmManagerTimer")
            clazz.getConstructor(Context::class.java).newInstance(context) as? IAlarm
        }.getOrNull()
    }
}
