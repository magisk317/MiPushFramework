package io.github.magisk317.mipush.hook.island

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IslandDispatcherNotifierTest {
    @Test
    fun `auto cancel timeout follows island timeout with a short grace period`() {
        assertEquals(6_000L, IslandDispatcherNotifier.autoCancelAfterMillis(5))
        assertEquals(11_000L, IslandDispatcherNotifier.autoCancelAfterMillis(10))
    }

    @Test
    fun `auto cancel timeout falls back when island timeout is invalid`() {
        assertEquals(6_000L, IslandDispatcherNotifier.autoCancelAfterMillis(0))
        assertEquals(6_000L, IslandDispatcherNotifier.autoCancelAfterMillis(-1))
    }
}
