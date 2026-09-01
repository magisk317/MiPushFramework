package io.github.magisk317.mipush.manager.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EventListCacheStoreTest {
    @Test
    fun `cache keys are isolated by Android user`() {
        val query = "q=hello;p=com.example.app"

        assertNotEquals(
            buildEventListCacheKey(0, query),
            buildEventListCacheKey(999, query),
        )
        assertEquals(
            "user=999;$query",
            buildEventListCacheKey(999, query),
        )
    }

    @Test
    fun `invalid user ids are rejected instead of using the primary namespace`() {
        assertThrows<IllegalArgumentException> {
            buildEventListCacheKey(-1, "q=;p=")
        }
    }

    @Test
    fun `legacy unscoped cache is readable only by the primary user`() {
        val query = "q=;p="

        assertEquals(query, legacyEventListCacheKey(0, query))
        assertNull(legacyEventListCacheKey(999, query))
    }

    @Test
    fun `cached events must belong to the requested user`() {
        val primaryEvent = event(userId = 0)
        val cloneEvent = event(userId = 999)

        assertTrue(eventsBelongToUser(listOf(primaryEvent), userId = 0))
        assertFalse(eventsBelongToUser(listOf(primaryEvent), userId = 999))
        assertTrue(eventsBelongToUser(listOf(cloneEvent), userId = 999))
    }

    @Test
    fun `cache handoff rejects events owned by another user`() {
        val primaryEvent = event(userId = 0)
        val cloneEvent = event(userId = 999)

        assertEquals(null, validateEventCacheHandoff(listOf(primaryEvent), userId = 0))
        assertEquals("user_mismatch", validateEventCacheHandoff(listOf(cloneEvent), userId = 0))
        assertEquals("invalid_user", validateEventCacheHandoff(emptyList(), userId = -1))
    }

    private fun event(userId: Int) = io.github.magisk317.mipush.manager.application.ManagerEvent(
        id = userId.toLong(),
        userId = userId,
        packageName = "com.example.app",
        configOptions = emptySet(),
        channel = "",
        receiveDateMs = 1L,
        title = "",
        content = "",
    )
}
