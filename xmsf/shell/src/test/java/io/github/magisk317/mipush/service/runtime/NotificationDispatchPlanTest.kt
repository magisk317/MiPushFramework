package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.mipush.utils.PackageConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationDispatchPlanTest {

    /** Mirrors a malformed custom Set, which must not turn a push into a lost notification. */
    private val malformedOperations: Set<String> = object : AbstractSet<String>() {
        override val size: Int get() = 1
        override fun iterator(): Iterator<String> = listOf(PackageConfig.OPERATION_WAKE).iterator()
        override fun contains(element: String): Boolean = throw IllegalStateException("malformed")
    }

    @Test
    fun `a failed configuration evaluation degrades to notify only`() {
        val plan = MIPushNotificationPolicy.resolveDispatchPlan(null)

        assertFalse(plan.wake)
        assertTrue(plan.notify)
        assertFalse(plan.open)
    }

    @Test
    fun `an unreadable operation set degrades to notify only`() {
        val plan = MIPushNotificationPolicy.resolveDispatchPlan(malformedOperations)

        assertFalse(plan.wake)
        assertTrue(plan.notify)
        assertFalse(plan.open)
    }

    @Test
    fun `an empty operation set still notifies`() {
        val plan = MIPushNotificationPolicy.resolveDispatchPlan(emptySet())

        assertFalse(plan.wake)
        assertTrue(plan.notify)
        assertFalse(plan.open)
    }

    @Test
    fun `each operation maps to its own phase`() {
        val wake = MIPushNotificationPolicy.resolveDispatchPlan(setOf(PackageConfig.OPERATION_WAKE))
        assertTrue(wake.wake)
        assertTrue(wake.notify)
        assertFalse(wake.open)

        val open = MIPushNotificationPolicy.resolveDispatchPlan(setOf(PackageConfig.OPERATION_OPEN))
        assertFalse(open.wake)
        assertTrue(open.notify)
        assertTrue(open.open)
    }

    @Test
    fun `ignore suppresses only the notify phase`() {
        val plan = MIPushNotificationPolicy.resolveDispatchPlan(
            setOf(PackageConfig.OPERATION_IGNORE, PackageConfig.OPERATION_OPEN),
        )

        assertTrue(plan.open)
        assertFalse(plan.notify)
    }

    @Test
    fun `all operations resolve together`() {
        val plan = MIPushNotificationPolicy.resolveDispatchPlan(
            setOf(
                PackageConfig.OPERATION_WAKE,
                PackageConfig.OPERATION_OPEN,
            ),
        )

        assertTrue(plan.wake)
        assertTrue(plan.notify)
        assertTrue(plan.open)
    }

    @Test
    fun `notify only is the documented safe default`() {
        val plan = NotificationDispatchPlan.NOTIFY_ONLY

        assertFalse(plan.wake)
        assertTrue(plan.notify)
        assertFalse(plan.open)
    }
}
