package io.github.magisk317.mipush.common.island

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IslandVisualContractTest {
    @Test
    fun `visual contract is owned by MiPush`() {
        assertEquals("io.github.magisk317.mipush", IslandVisualContract.MIPUSH_OWNER)
        assertEquals("mipush-island-visual", IslandVisualContract.VISUAL_MARKER)
    }
}
