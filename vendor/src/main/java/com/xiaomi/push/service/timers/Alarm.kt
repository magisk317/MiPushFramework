package com.xiaomi.push.service.timers

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.XMJobService
import com.xiaomi.push.service.heartbeat.HeartbeatStrategyManager
import com.xiaomi.push.service.heartbeat.StableIntelligentHeartbeatStrategy

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

    data class DiagnosticSnapshot(
        val timerClassName: String?,
        val alarmAlive: Boolean,
        val alarmMode: String?,
        val alarmFallbackReason: String?,
        val alarmRegisteredAtMs: Long,
        val nextTriggerAtMs: Long,
        val lastTimerCallbackAtMs: Long,
        val lastTimerCallbackDelayMs: Long,
    )

    interface IAlarm {
        fun isAlive(): Boolean
        fun registerPing(force: Boolean)
        fun stop()
        /** Stock ia.a.a: re-register when the heartbeat interval provider changes. */
        fun refreshPingInterval() {}
        fun markTimerCallback(nowElapsedRealtime: Long, nowWallClockMs: Long) {}
        fun diagnosticSnapshot(nowElapsedRealtime: Long, nowWallClockMs: Long): DiagnosticSnapshot =
            DiagnosticSnapshot(
                timerClassName = javaClass.name,
                alarmAlive = isAlive(),
                alarmMode = null,
                alarmFallbackReason = null,
                alarmRegisteredAtMs = 0L,
                nextTriggerAtMs = 0L,
                lastTimerCallbackAtMs = 0L,
                lastTimerCallbackDelayMs = 0L,
            )
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
        // Stock 7.4.67-C ia.b selects the AlarmManager-backed ia.c for com.xiaomi.xmsf. The older
        // fallback selected JobScheduler when the product adapter could not be resolved, changing
        // heartbeat timing and alarm identity after shrinking or class-loading failures.
        sAlarmInstance = createProductAlarm(applicationContext)
            ?: StockAlarmManagerTimer(applicationContext)
        // Stock ia.c interval provider is v, which prepares the current network identity up front.
        runCatching {
            HeartbeatStrategyManager.getInstance(applicationContext).onNetworkChanged(
                StableIntelligentHeartbeatStrategy.currentNetworkSnapshot(applicationContext),
            )
        }
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

    /**
     * Stock ia.b.e: refresh the registered ping when the heartbeat strategy interval changes.
     * Older code had no equivalent, so learned short intervals only applied after a full stop/start.
     */
    @JvmStatic
    fun refreshPingInterval() {
        synchronized(this) {
            sAlarmInstance?.refreshPingInterval()
        }
    }

    @JvmStatic
    fun timerClassName(): String? = synchronized(this) {
        sAlarmInstance?.javaClass?.name
    }

    @JvmStatic
    fun markTimerCallback(nowElapsedRealtime: Long, nowWallClockMs: Long) = synchronized(this) {
        sAlarmInstance?.markTimerCallback(nowElapsedRealtime, nowWallClockMs)
    }

    @JvmStatic
    fun diagnosticSnapshot(nowElapsedRealtime: Long, nowWallClockMs: Long): DiagnosticSnapshot =
        synchronized(this) {
            sAlarmInstance?.diagnosticSnapshot(nowElapsedRealtime, nowWallClockMs)
                ?: DiagnosticSnapshot(null, false, null, null, 0L, 0L, 0L, 0L)
        }

    private fun createProductAlarm(context: Context): IAlarm? {
        return runCatching {
            val clazz = Class.forName("com.xiaomi.push.service.timers.AlarmManagerTimer")
            clazz.getConstructor(Context::class.java).newInstance(context) as? IAlarm
        }.getOrNull()
    }
}
