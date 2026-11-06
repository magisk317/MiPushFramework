package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StaleTargetNotificationPolicyTest {
    private val installTime = 1_000L

    @Test
    fun `notification posted before the install is stale`() {
        assertTrue(cancel(postTime = 999L))
    }

    @Test
    fun `notification posted after the install is kept`() {
        assertFalse(cancel(postTime = 1_001L))
    }

    @Test
    fun `notification posted at the install instant is kept`() {
        assertFalse(cancel(postTime = installTime))
    }

    @Test
    fun `ongoing notifications are kept`() {
        assertFalse(cancel(postTime = 500L, ongoing = true))
    }

    @Test
    fun `notifications from another user are kept`() {
        assertFalse(cancel(postTime = 500L, userId = 10))
    }

    @Test
    fun `missing timestamps never cancel`() {
        assertFalse(cancel(postTime = 0L))
        assertFalse(cancel(postTime = 500L, installUpdateTime = 0L))
        assertFalse(cancel(postTime = 0L, installUpdateTime = 0L))
    }

    private fun cancel(
        postTime: Long,
        ongoing: Boolean = false,
        userId: Int = 0,
        currentUserId: Int = 0,
        installUpdateTime: Long = installTime,
    ): Boolean = StaleTargetNotificationPolicy.shouldCancel(
        postTime = postTime,
        ongoing = ongoing,
        userId = userId,
        currentUserId = currentUserId,
        installUpdateTime = installUpdateTime,
    )
}
