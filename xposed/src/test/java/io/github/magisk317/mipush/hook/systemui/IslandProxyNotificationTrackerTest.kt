package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IslandProxyNotificationTrackerTest {
    @Test
    fun `proxy ids collapse to one per source package`() {
        val first = IslandProxyNotificationIds.fromPackage("com.example.app")
        val second = IslandProxyNotificationIds.fromStatusBarKey(
            key = "0|com.example.app|42|null|1000",
            packageName = "com.example.app",
            notificationId = 42,
            tag = null,
        )
        val third = IslandProxyNotificationIds.fromStatusBarKey(
            key = "0|com.example.app|99|tag|1000",
            packageName = "com.example.app",
            notificationId = 99,
            tag = "tag",
        )
        val otherPackage = IslandProxyNotificationIds.fromPackage("com.other")

        assertEquals(first, second)
        assertEquals(first, third)
        assertNotEquals(first, otherPackage)
    }

    @Test
    fun `proxy ids fall back to systemui owner for blank package`() {
        val blank = IslandProxyNotificationIds.fromPackage("")
        val nullPkg = IslandProxyNotificationIds.fromPackage(null)
        val systemui = IslandProxyNotificationIds.fromPackage("com.android.systemui")

        assertEquals(blank, nullPkg)
        assertEquals(blank, systemui)
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
