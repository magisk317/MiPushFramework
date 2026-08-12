package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IslandProxyNotificationTrackerTest {
    @Test
    fun `proxy ids collapse to one per source package`() {
        val first = IslandProxyNotificationIds.fromPackage("com.example.app", userId = 0)
        val second = IslandProxyNotificationIds.fromStatusBarKey(
            key = "0|com.example.app|42|null|1000",
            packageName = "com.example.app",
            notificationId = 42,
            tag = null,
            userId = 0,
        )
        val third = IslandProxyNotificationIds.fromStatusBarKey(
            key = "0|com.example.app|99|tag|1000",
            packageName = "com.example.app",
            notificationId = 99,
            tag = "tag",
            userId = 0,
        )
        val otherPackage = IslandProxyNotificationIds.fromPackage("com.other", userId = 0)

        assertEquals(first, second)
        assertEquals(first, third)
        assertNotEquals(first, otherPackage)
    }

    @Test
    fun `proxy ids stay isolated between notification users`() {
        val owner = IslandProxyNotificationIds.fromPackage("com.example.app", userId = 0)
        val clone = IslandProxyNotificationIds.fromPackage("com.example.app", userId = 999)

        assertNotEquals(owner, clone)
    }

    @Test
    fun `proxy ids fall back to systemui owner for blank package`() {
        val blank = IslandProxyNotificationIds.fromPackage("", userId = 0)
        val nullPkg = IslandProxyNotificationIds.fromPackage(null, userId = 0)
        val systemui = IslandProxyNotificationIds.fromPackage("com.android.systemui", userId = 0)

        assertEquals(blank, nullPkg)
        assertEquals(blank, systemui)
    }

    @Test
    fun `source keys prefer status bar key and otherwise include tag`() {
        val key = IslandProxySourceKeys.fromStatusBarKey(
            key = "0|com.example.app|42|tag|1000",
            packageName = "com.example.app",
            notificationId = 42,
            tag = "ignored",
            userId = 0,
        )
        val fallbackWithTag = IslandProxySourceKeys.fromStatusBarKey(
            key = null,
            packageName = "com.example.app",
            notificationId = 42,
            tag = "tag",
            userId = 0,
        )
        val fallbackWithoutTag = IslandProxySourceKeys.fromStatusBarKey(
            key = null,
            packageName = "com.example.app",
            notificationId = 42,
            tag = null,
            userId = 0,
        )

        assertEquals("0|0|com.example.app|42|tag|1000", key)
        assertNotEquals(fallbackWithTag, fallbackWithoutTag)
    }

    @Test
    fun `source keys stay isolated between users when status bar key is absent`() {
        val owner = IslandProxySourceKeys.fromStatusBarKey(
            key = null,
            packageName = "com.example.app",
            notificationId = 42,
            tag = "same",
            userId = 0,
        )
        val clone = IslandProxySourceKeys.fromStatusBarKey(
            key = null,
            packageName = "com.example.app",
            notificationId = 42,
            tag = "same",
            userId = 999,
        )

        assertNotEquals(owner, clone)
    }

    @Test
    fun `dedup keys stay isolated between users and tags`() {
        val owner = IslandProxyDedupKeys.fromStatusBarKey(
            key = null,
            packageName = "com.example.app",
            notificationId = 42,
            tag = "owner",
            userId = 0,
        )
        val clone = IslandProxyDedupKeys.fromStatusBarKey(
            key = null,
            packageName = "com.example.app",
            notificationId = 42,
            tag = "owner",
            userId = 999,
        )
        val otherTag = IslandProxyDedupKeys.fromStatusBarKey(
            key = null,
            packageName = "com.example.app",
            notificationId = 42,
            tag = "clone",
            userId = 0,
        )

        assertNotEquals(owner, clone)
        assertNotEquals(owner, otherTag)
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

    @Test
    fun `removing an older source does not cancel a newer package proxy`() {
        val tracker = IslandProxyOwnershipTracker(maxTrackedSources = 10)
        val proxyId = IslandProxyNotificationIds.fromPackage("com.example.app", userId = 0)

        tracker.record("source-old", proxyId)
        tracker.record("source-new", proxyId)

        assertEquals(null, tracker.removeAndResolveCancellation("source-old"))
        assertEquals(proxyId, tracker.removeAndResolveCancellation("source-new"))
    }
}
