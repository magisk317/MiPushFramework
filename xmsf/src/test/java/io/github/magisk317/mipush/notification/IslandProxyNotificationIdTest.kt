package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class IslandProxyNotificationIdTest {
    @Test
    fun `proxy ids collapse to one per source package`() {
        val first = IslandProxyNotificationId.fromPackage("com.example.app", 42, null)
        val second = IslandProxyNotificationId.fromPackage("com.example.app", 99, "tag")
        val third = IslandProxyNotificationId.fromPackage("com.example.app", 7, null)
        val otherPackage = IslandProxyNotificationId.fromPackage("com.other", 42, null)

        assertEquals(first, second)
        assertEquals(first, third)
        assertNotEquals(first, otherPackage)
    }
}
