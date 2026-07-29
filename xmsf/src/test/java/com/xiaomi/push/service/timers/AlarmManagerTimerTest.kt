package com.xiaomi.push.service.timers

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.xiaomi.smack.SmackConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [35], application = Application::class)
class AlarmManagerTimerTest {
    @Test
    fun `forced schedule aligns to the next elapsed realtime interval`() {
        assertEquals(
            1_500L,
            StockAlarmManagerTimer.calculateNextTrigger(
                nowElapsedRealtime = 1_250L,
                currentTriggerElapsedRealtime = 0L,
                intervalMs = 500L,
                force = true,
            ),
        )
    }

    @Test
    fun `live future schedule is retained without force`() {
        assertEquals(
            1_600L,
            StockAlarmManagerTimer.calculateNextTrigger(
                nowElapsedRealtime = 1_250L,
                currentTriggerElapsedRealtime = 1_600L,
                intervalMs = 500L,
                force = false,
            ),
        )
    }

    @Test
    fun `expired schedule advances once or falls back to a full interval`() {
        assertEquals(
            1_500L,
            StockAlarmManagerTimer.calculateNextTrigger(
                nowElapsedRealtime = 1_250L,
                currentTriggerElapsedRealtime = 1_000L,
                intervalMs = 500L,
                force = false,
            ),
        )
        assertEquals(
            1_750L,
            StockAlarmManagerTimer.calculateNextTrigger(
                nowElapsedRealtime = 1_250L,
                currentTriggerElapsedRealtime = 500L,
                intervalMs = 500L,
                force = false,
            ),
        )
    }

    @Test
    fun `non-forced register does not resurrect a stopped alarm`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val timer = AlarmManagerTimer(context)

        timer.registerPing(force = false)

        assertFalse(timer.isAlive())
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    @Suppress("DEPRECATION")
    fun `forced register schedules exactly in the elapsed realtime clock domain`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val previousCapability = alarmManager.canScheduleExactAlarms()
        val timer = AlarmManagerTimer(context)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        try {
            val before = SystemClock.elapsedRealtime()

            timer.registerPing(force = true)

            val after = SystemClock.elapsedRealtime()
            val scheduled = requireNotNull(shadowOf(alarmManager).peekNextScheduledAlarm())
            assertTrue(timer.isAlive())
            assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, scheduled.type)
            assertTrue(scheduled.isAllowWhileIdle)
            assertTrue(scheduled.triggerAtMs > before)
            assertTrue(
                scheduled.triggerAtMs <= after + SmackConfiguration.getPingInteval().toLong(),
            )
        } finally {
            ShadowAlarmManager.setCanScheduleExactAlarms(previousCapability)
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `android 12 without exact capability falls back to an elapsed alarm`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val previousCapability = alarmManager.canScheduleExactAlarms()
        try {
            ShadowAlarmManager.setCanScheduleExactAlarms(false)
            val timer = AlarmManagerTimer(context)

            timer.registerPing(force = true)

            val scheduled = requireNotNull(shadowOf(alarmManager).peekNextScheduledAlarm())
            assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, scheduled.type)
            assertFalse(scheduled.isAllowWhileIdle)
        } finally {
            ShadowAlarmManager.setCanScheduleExactAlarms(previousCapability)
        }
    }
}
