package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IslandClickRoutingTest {
    @Test
    fun `launcher fallback request codes are isolated by notification user`() {
        val owner = IslandClickRouting.requestCode("com.example.app", 42, userId = 0)
        val clone = IslandClickRouting.requestCode("com.example.app", 42, userId = 999)

        assertNotEquals(owner, clone)
    }

    @Test
    fun `negative user ids use the owner user for request identity`() {
        assertEquals(
            IslandClickRouting.requestCode("com.example.app", 42, userId = 0),
            IslandClickRouting.requestCode("com.example.app", 42, userId = -1),
        )
    }
}
