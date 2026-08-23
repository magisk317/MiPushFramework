package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TopNotificationCoordinatorPureTest {

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
