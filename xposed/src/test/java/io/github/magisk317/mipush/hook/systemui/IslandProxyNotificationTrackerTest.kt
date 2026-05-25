package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IslandProxyNotificationTrackerTest {
    @Test
    fun `proxy ids are stable by source notification key`() {
        val first = IslandProxyNotificationIds.fromStatusBarKey(
            key = "0|com.example.app|42|null|1000",
            packageName = "com.example.app",
            notificationId = 42,
            tag = null,
        )
        val second = IslandProxyNotificationIds.fromStatusBarKey(
            key = "0|com.example.app|42|null|1000",
            packageName = "com.other",
            notificationId = 7,
            tag = "ignored",
        )
        val fallback = IslandProxyNotificationIds.fromStatusBarKey(
            key = null,
            packageName = "com.example.app",
            notificationId = 42,
            tag = null,
        )

        assertEquals(first, second)
        assertNotEquals(first, fallback)
    }

    @Test
    fun `tracker suppresses repeated posts only inside ttl`() {
        var now = 1_000L
        val tracker = IslandProxyPostTracker(ttlMs = 2_000L) { now }

        assertFalse(tracker.shouldSkip(42))
        now += 1_000L
        assertTrue(tracker.shouldSkip(42))
        now += 2_001L
        assertFalse(tracker.shouldSkip(42))
    }
}
