package com.xiaomi.push.service.timers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushConstants
import com.xiaomi.smack.SmackConfiguration

import com.xiaomi.push.service.heartbeat.HeartbeatStrategyManager

/**
 * AlarmManager heartbeat implementation from stock XMSF 7.4.67-C `ia.a`.
 *
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256
 * 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/ia/a.java
 * Older baseline: MiPush SDK 3.7.9 `com.xiaomi.push.service.timers.AlarmManagerTimer`.
 *
 * The 3.7.9 implementation combined an epoch timestamp with an elapsed-realtime alarm. Stock
 * 7.4.67-C keeps both trigger calculation and expiry checks in the elapsed-realtime clock domain.
 * Platform capability adaptation is supplied by the product subclass; this class retains the
 * stock transport state machine and stock privileged-package exact-alarm rule.
 */
open class StockAlarmManagerTimer(
    protected val context: Context,
) : Alarm.IAlarm {
    private var pendingIntent: PendingIntent? = null

    @Volatile
    private var nextPingElapsedRealtime: Long = 0L


    /** Last interval used by registerPing; stock ia.a field f9207d. */
    @Volatile
    private var registeredIntervalMs: Long = 0L

    open fun getPingInteval(): Long {
        return try {
            HeartbeatStrategyManager.getInstance(context).pingIntervalMs()
        } catch (_: Throwable) {
            SmackConfiguration.getPingInteval().toLong()
        }
    }

    override fun isAlive(): Boolean = nextPingElapsedRealtime != 0L

    /**
     * Stock ia.a.a / ia.b.e: if the strategy interval changed, force-restart the alarm.
     */
    override fun refreshPingInterval() {
        val newInterval = getPingInteval()
        MyLog.w(
            "refreshPingInterval, newInterval=" + newInterval +
                ", former mHBInterval= " + registeredIntervalMs,
        )
        if (registeredIntervalMs != 0L && registeredIntervalMs != newInterval) {
            MyLog.w(
                "HB interval change from " + registeredIntervalMs + " to " + newInterval +
                    ", force restart",
            )
            registerPing(force = true)
        }
    }

    override fun registerPing(force: Boolean) {
        val intervalMs = getPingInteval()
        if (!force && nextPingElapsedRealtime == 0L) return
        if (force) {
            stop()
        }

        registeredIntervalMs = intervalMs
        nextPingElapsedRealtime = calculateNextTrigger(
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            currentTriggerElapsedRealtime = nextPingElapsedRealtime,
            intervalMs = intervalMs,
            force = force,
        )
        val intent = Intent(PushConstants.ACTION_PING_TIMER).setPackage(context.packageName)
        register(intent, nextPingElapsedRealtime)
    }

    override fun stop() {
        val currentPendingIntent = pendingIntent
        if (currentPendingIntent != null) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(currentPendingIntent)
            } catch (_: Exception) {
            } finally {
                pendingIntent = null
                MyLog.v("unregister timer")
            }
        }
        nextPingElapsedRealtime = 0L
    }

    protected open fun scheduleAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.packageName == PushConstants.PUSH_SERVICE_PACKAGE_NAME
        ) {
            try {
                scheduleExactAlarm(alarmManager, triggerAtMillis, operation)
            } catch (error: Exception) {
                // Stock calls setExactAndAllowWhileIdle through JavaCalls, which logs and swallows
                // reflection/invocation failures instead of crashing the service.
                MyLog.e(error)
            }
        } else {
            scheduleInexactAlarm(alarmManager, triggerAtMillis, operation)
        }
    }

    protected fun scheduleExactAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent,
    ) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtMillis,
            operation,
        )
    }

    protected fun scheduleInexactAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent,
    ) {
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtMillis,
            operation,
        )
    }

    private fun register(intent: Intent, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val operation = PendingIntent.getBroadcast(context, 0, intent, flags)
        pendingIntent = operation
        scheduleAlarm(alarmManager, triggerAtMillis, operation)
        MyLog.v(
            "register timer $triggerAtMillis, delta=" +
                "${triggerAtMillis - SystemClock.elapsedRealtime()}ms",
        )
    }

    companion object {
        @JvmStatic
        fun calculateNextTrigger(
            nowElapsedRealtime: Long,
            currentTriggerElapsedRealtime: Long,
            intervalMs: Long,
            force: Boolean,
        ): Long {
            require(intervalMs > 0L)
            if (force || currentTriggerElapsedRealtime == 0L) {
                return nowElapsedRealtime + intervalMs - (nowElapsedRealtime % intervalMs)
            }
            if (currentTriggerElapsedRealtime > nowElapsedRealtime) {
                return currentTriggerElapsedRealtime
            }
            val advanced = currentTriggerElapsedRealtime + intervalMs
            return if (advanced < nowElapsedRealtime) {
                nowElapsedRealtime + intervalMs
            } else {
                advanced
            }
        }
    }
}
