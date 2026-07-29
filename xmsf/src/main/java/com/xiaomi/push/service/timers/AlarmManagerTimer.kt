package com.xiaomi.push.service.timers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/**
 * Product exact-alarm adaptation layered over stock 7.4.67-C heartbeat behavior.
 *
 * Stock `ia.a` assumes package `com.xiaomi.xmsf` is privileged and may always request an exact
 * alarm on Android 12+. MiPushFramework can carry the same package name without that privilege, so
 * use the public capability check and preserve the heartbeat with an inexact elapsed alarm when the
 * permission is unavailable or revoked between the check and scheduling call.
 */
open class AlarmManagerTimer(context: Context) : StockAlarmManagerTimer(context) {
    override fun scheduleAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent,
    ) {
        val exactAlarmAvailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (exactAlarmAvailable) {
            try {
                scheduleExactAlarm(alarmManager, triggerAtMillis, operation)
                return
            } catch (_: SecurityException) {
                // Capability can change between the check and the scheduling call.
            }
        }
        scheduleInexactAlarm(alarmManager, triggerAtMillis, operation)
    }
}
