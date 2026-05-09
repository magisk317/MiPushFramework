
package io.github.magisk317.mipush.compat

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
        pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val pendingIntent = pi ?: return

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )

        MyLog.v("register timer$triggerAtMillis")
    }


}
