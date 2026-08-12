package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class XmsfNotificationAvailabilityReaderTest {
    @Test
    fun `cache reuses a value within ttl`() {
        val cache = NotificationAvailabilityCache(maxEntries = 4, ttlMs = 100L)
        val key = NotificationAvailabilityCache.Key(0, "com.example.app", "channel")
        var loads = 0

        assertEquals(false, cache.getOrLoad(key, 1_000L) { loads++; false })
        assertEquals(false, cache.getOrLoad(key, 1_099L) { loads++; true })
        assertEquals(1, loads)
    }

    @Test
    fun `cache expires values and isolates users`() {
        val cache = NotificationAvailabilityCache(maxEntries = 4, ttlMs = 100L)
        val userZero = NotificationAvailabilityCache.Key(0, "com.example.app", "channel")
        val userTen = NotificationAvailabilityCache.Key(10, "com.example.app", "channel")
        var loads = 0

        assertEquals(false, cache.getOrLoad(userZero, 1_000L) { loads++; false })
        assertEquals(true, cache.getOrLoad(userTen, 1_001L) { loads++; true })
        assertEquals(true, cache.getOrLoad(userZero, 1_101L) { loads++; true })
        assertEquals(3, loads)
    }

    @Test
    fun `cache evicts least recently used entries`() {
        val cache = NotificationAvailabilityCache(maxEntries = 2, ttlMs = 100L)
        val first = NotificationAvailabilityCache.Key(0, "com.example.first", "channel")
        val second = NotificationAvailabilityCache.Key(0, "com.example.second", "channel")
        val third = NotificationAvailabilityCache.Key(0, "com.example.third", "channel")
        var loads = 0

        cache.getOrLoad(first, 1_000L) { loads++; false }
        cache.getOrLoad(second, 1_001L) { loads++; false }
        cache.getOrLoad(first, 1_002L) { loads++; false }
        cache.getOrLoad(third, 1_003L) { loads++; false }
        cache.getOrLoad(second, 1_004L) { loads++; false }

        assertEquals(4, loads)
    }
}
