package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class IslandProxyNotificationIdTest {
    @Test
    fun `proxy ids collapse to one per source package`() {
        val first = IslandProxyNotificationId.fromPackage("com.example.app", 42, null, userId = 0)
        val second = IslandProxyNotificationId.fromPackage("com.example.app", 99, "tag", userId = 0)
        val third = IslandProxyNotificationId.fromPackage("com.example.app", 7, null, userId = 0)
        val otherPackage = IslandProxyNotificationId.fromPackage("com.other", 42, null, userId = 0)

        assertEquals(first, second)
        assertEquals(first, third)
        assertNotEquals(first, otherPackage)
    }

    @Test
    fun `proxy ids keep same package isolated by user`() {
        val primary = IslandProxyNotificationId.fromPackage("com.example.app", 42, null, userId = 0)
        val cloned = IslandProxyNotificationId.fromPackage("com.example.app", 42, null, userId = 999)

        assertNotEquals(primary, cloned)
    }
}
