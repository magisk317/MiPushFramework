package io.github.magisk317.mipush.hook.island

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IslandDispatchStateTest {
    @BeforeEach
    fun reset() {
        IslandDispatchState.resetForTest()
    }

    @Test
    fun `posted ids are tracked and removed by notification id`() {
        IslandDispatchState.markPosted(7, userId = 0)
        IslandDispatchState.markPosted(9, userId = 999)
        IslandDispatchState.markCancelled(7, userId = 0)

        assertEquals(setOf(9), IslandDispatchState.postedIds())
    }

    @Test
    fun `reset clears registration and posted ids`() {
        IslandDispatchState.registered = true
        IslandDispatchState.markPosted(7, userId = 0)

        IslandDispatchState.resetForTest()

        assertFalse(IslandDispatchState.registered)
        assertTrue(IslandDispatchState.postedIds().isEmpty())
    }

    @Test
    fun `same notification id stays isolated between users`() {
        IslandDispatchState.markPosted(7, userId = 0)
        IslandDispatchState.markPosted(7, userId = 999)

        IslandDispatchState.markCancelled(7, userId = 0)

        assertEquals(setOf(DispatchKey(999, 7)), IslandDispatchState.postedKeys())
    }
}
