package io.github.magisk317.mipush.notification

import android.app.Notification
import android.os.Bundle
import androidx.core.app.NotificationCompat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
@Suppress("DEPRECATION")
class TopNotificationCoordinatorTest {

    @Test
    fun `stock top spec requires explicit repeat and valid period`() {
        assertNull(TopNotificationCoordinator.resolveSpec(emptyMap()))
        assertNull(
            TopNotificationCoordinator.resolveSpec(
                mapOf(
                    TopNotificationCoordinator.EXTRA_REPEAT to "false",
                    TopNotificationCoordinator.EXTRA_PERIOD to "60",
                ),
            ),
        )
        assertNull(
            TopNotificationCoordinator.resolveSpec(
                mapOf(
                    TopNotificationCoordinator.EXTRA_REPEAT to "true",
                    TopNotificationCoordinator.EXTRA_PERIOD to "0",
                ),
            ),
        )
        assertNull(
            TopNotificationCoordinator.resolveSpec(
                mapOf(
                    TopNotificationCoordinator.EXTRA_REPEAT to "true",
                    TopNotificationCoordinator.EXTRA_PERIOD to "10",
                    TopNotificationCoordinator.EXTRA_FREQUENCY to "11",
                ),
            ),
        )

        assertEquals(
            TopNotificationCoordinator.Spec(periodSeconds = 60, frequencySeconds = 15),
            TopNotificationCoordinator.resolveSpec(
                mapOf(
                    TopNotificationCoordinator.EXTRA_REPEAT to "true",
                    TopNotificationCoordinator.EXTRA_PERIOD to "60",
                    TopNotificationCoordinator.EXTRA_FREQUENCY to "15",
                ),
            ),
        )
    }

    @Test
    fun `initial state uses stock priority alert behavior and typed markers`() {
        val context = RuntimeEnvironment.getApplication()
        val builder = NotificationCompat.Builder(context, "top-test")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
        TopNotificationCoordinator.applyInitialState(
            builder = builder,
            spec = TopNotificationCoordinator.Spec(periodSeconds = 60, frequencySeconds = 15),
            originalWhenMs = 123_456L,
        )

        val notification = builder.build()

        assertEquals(Notification.PRIORITY_MAX, notification.priority)
        assertEquals(Notification.GROUP_ALERT_SUMMARY, notification.groupAlertBehavior)
        assertEquals(123_456L, notification.extras.getLong(TopNotificationCoordinator.LOCAL_ORIGINAL_WHEN))
        assertTrue(notification.extras.getBoolean(TopNotificationCoordinator.LOCAL_FLAG))
        assertEquals(15, notification.extras.getInt(TopNotificationCoordinator.LOCAL_FREQUENCY))
        assertEquals(60, notification.extras.getInt(TopNotificationCoordinator.LOCAL_PERIOD))
    }

    @Test
    fun `lifecycle ownership repeats stock environment marker and message checks`() {
        val extras = Bundle().apply {
            putBoolean(TopNotificationCoordinator.LOCAL_FLAG, true)
            putString("message_id", "message")
        }

        assertEquals(
            "message",
            TopNotificationCoordinator.resolveLifecycleMessageId(extras, "message", true),
        )
        assertNull(TopNotificationCoordinator.resolveLifecycleMessageId(extras, "message", false))
        assertNull(TopNotificationCoordinator.resolveLifecycleMessageId(extras, "replacement", true))
        extras.putBoolean(TopNotificationCoordinator.LOCAL_FLAG, false)
        assertNull(TopNotificationCoordinator.resolveLifecycleMessageId(extras, "message", true))
    }

    @Test
    fun `zero frequency schedules only the final downgrade and omits frequency marker`() {
        val context = RuntimeEnvironment.getApplication()
        val builder = NotificationCompat.Builder(context, "top-test")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
        TopNotificationCoordinator.applyInitialState(
            builder = builder,
            spec = TopNotificationCoordinator.Spec(periodSeconds = 30, frequencySeconds = 0),
            originalWhenMs = 10_000L,
        )

        val notification = builder.build()
        val initial = TopNotificationCoordinator.calculateUpdatePlan(
            originalWhenMs = 10_000L,
            periodSeconds = 30,
            frequencySeconds = 0,
            nowMs = 10_100L,
            justPosted = true,
        )
        val expired = TopNotificationCoordinator.calculateUpdatePlan(
            originalWhenMs = 10_000L,
            periodSeconds = 30,
            frequencySeconds = 0,
            nowMs = 40_000L,
            justPosted = false,
        )

        assertFalse(notification.extras.containsKey(TopNotificationCoordinator.LOCAL_FREQUENCY))
        assertEquals(TopNotificationCoordinator.UpdatePlan(TopNotificationCoordinator.UpdateAction.NONE, 30), initial)
        assertEquals(TopNotificationCoordinator.UpdatePlan(TopNotificationCoordinator.UpdateAction.DOWNGRADE, 0), expired)
    }

    @Test
    fun `frequency plan reposts until stock period expires`() {
        val initial = TopNotificationCoordinator.calculateUpdatePlan(
            originalWhenMs = 100_000L,
            periodSeconds = 10,
            frequencySeconds = 3,
            nowMs = 100_500L,
            justPosted = true,
        )
        val middle = TopNotificationCoordinator.calculateUpdatePlan(
            originalWhenMs = 100_000L,
            periodSeconds = 10,
            frequencySeconds = 3,
            nowMs = 106_000L,
            justPosted = false,
        )
        val expired = TopNotificationCoordinator.calculateUpdatePlan(
            originalWhenMs = 100_000L,
            periodSeconds = 10,
            frequencySeconds = 3,
            nowMs = 110_000L,
            justPosted = false,
        )

        assertEquals(TopNotificationCoordinator.UpdatePlan(TopNotificationCoordinator.UpdateAction.NONE, 3), initial)
        assertEquals(TopNotificationCoordinator.UpdatePlan(TopNotificationCoordinator.UpdateAction.REPOST, 3), middle)
        assertEquals(TopNotificationCoordinator.UpdatePlan(TopNotificationCoordinator.UpdateAction.DOWNGRADE, 0), expired)
    }

    @Test
    fun `repost mutates stock timing without rebuilding notification`() {
        val context = RuntimeEnvironment.getApplication()
        val notification = NotificationCompat.Builder(context, "top-test")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .addExtras(Bundle().apply { putString("miui.hidden.contract", "kept") })
            .build()

        val reposted = TopNotificationCoordinator.prepareTopRepost(notification, 987_654L)

        assertSame(notification, reposted)
        assertEquals(987_654L, reposted.`when`)
        assertEquals(Notification.GROUP_ALERT_SUMMARY, reposted.groupAlertBehavior)
        assertEquals("kept", reposted.extras.getString("miui.hidden.contract"))
    }

    @Test
    fun `downgrade removes only local top state`() {
        val extras = Bundle().apply {
            putString("message_id", "message")
            putBoolean(TopNotificationCoordinator.LOCAL_FLAG, true)
            putLong(TopNotificationCoordinator.LOCAL_ORIGINAL_WHEN, 1L)
            putInt(TopNotificationCoordinator.LOCAL_FREQUENCY, 2)
            putInt(TopNotificationCoordinator.LOCAL_PERIOD, 3)
        }

        TopNotificationCoordinator.clearLocalState(extras)

        assertEquals("message", extras.getString("message_id"))
        assertFalse(extras.containsKey(TopNotificationCoordinator.LOCAL_FLAG))
        assertFalse(extras.containsKey(TopNotificationCoordinator.LOCAL_ORIGINAL_WHEN))
        assertFalse(extras.containsKey(TopNotificationCoordinator.LOCAL_FREQUENCY))
        assertFalse(extras.containsKey(TopNotificationCoordinator.LOCAL_PERIOD))
    }

    @Test
    fun `job id matches stock m2 contract`() {
        assertEquals("n_top_update_42_message", TopNotificationCoordinator.jobId(42, "message"))
        assertEquals("n_top_update_999_42_message", TopNotificationCoordinator.jobId(42, "message", 999))
    }

    @Test
    fun `runtime job id isolates target packages`() {
        val first = TopNotificationCoordinator.scopedJobId("com.example.first", 42, "message", 0)
        val second = TopNotificationCoordinator.scopedJobId("com.example.second", 42, "message", 0)

        assertNotEquals(first, second)
    }
}
