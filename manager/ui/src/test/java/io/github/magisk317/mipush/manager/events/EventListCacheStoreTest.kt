package io.github.magisk317.mipush.manager.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

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
    fun `invalid user ids use the primary user namespace`() {
        assertEquals(
            buildEventListCacheKey(0, "q=;p="),
            buildEventListCacheKey(-1, "q=;p="),
        )
    }
}
