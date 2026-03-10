
package com.magisk317.compat

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.PushConstants
import com.xiaomi.smack.SmackConfiguration

class AlarmManagerTimerCompat(private val context: Context) {
    @Volatile
    private var nextPingTs: Long = 0
    private var pi: PendingIntent? = null

    fun isAlive(): Boolean = nextPingTs != 0L

    fun registerPing(force: Boolean) {
        val pingInterval = SmackConfiguration.getPingInteval().toLong()
        if (!force && nextPingTs == 0L) {
            return
        }

        if (force) {
            stop()
        }

        if (!force && nextPingTs != 0L) {
            nextPingTs += pingInterval
            if (nextPingTs < System.currentTimeMillis()) {
                nextPingTs = System.currentTimeMillis() + pingInterval
            }
        } else {
            val elapsedRealtime = SystemClock.elapsedRealtime()
            nextPingTs = System.currentTimeMillis() + pingInterval - elapsedRealtime % pingInterval
        }

        val pingIntent = Intent(PushConstants.ACTION_PING_TIMER).setPackage(context.packageName)
        register(pingIntent, nextPingTs)
    }

    fun stop() {
        val pendingIntent = pi
        if (pendingIntent != null) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent)
            } catch (_: Exception) {
            } finally {
                pi = null
                MyLog.v("unregister timer")
                nextPingTs = 0
            }
        }
        nextPingTs = 0
    }

    private fun register(intent: Intent, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        pi = PendingIntent.getBroadcast(context, 0, intent, pendingIntentFlags)
        val pendingIntent = pi ?: return

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                JavaCalls.callMethod(
                    alarmManager,
                    "setExactAndAllowWhileIdle",
                    0,
                    triggerAtMillis,
                    pendingIntent
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                setExact(alarmManager, triggerAtMillis, pendingIntent)
            }

            else -> {
                alarmManager.set(0, triggerAtMillis, pendingIntent)
            }
        }

        MyLog.v("register timer$triggerAtMillis")
    }

    private fun setExact(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            val method = AlarmManager::class.java.getMethod(
                "setExact",
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                PendingIntent::class.java
            )
            method.invoke(alarmManager, 0, triggerAtMillis, pendingIntent)
        } catch (e: Exception) {
            MyLog.e(e)
        }
    }
}
