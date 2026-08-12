package io.github.magisk317.mipush.common.island

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IslandVisualContractTest {
    @Test
    fun `auto selects HyperIsland only when it is installed`() {
        assertEquals(
            IslandRendererMode.HYPERISLAND,
            IslandVisualContract.effectiveMode(IslandRendererMode.AUTO, hyperIslandInstalled = true),
        )
        assertEquals(
            IslandRendererMode.MIPUSH,
            IslandVisualContract.effectiveMode(IslandRendererMode.AUTO, hyperIslandInstalled = false),
        )
    }

    @Test
    fun `explicit HyperIsland gracefully falls back when package is missing`() {
        assertEquals(
            IslandRendererMode.MIPUSH,
            IslandVisualContract.effectiveMode(IslandRendererMode.HYPERISLAND, hyperIslandInstalled = false),
        )
        assertEquals(
            IslandVisualContract.MIPUSH_OWNER,
            IslandVisualContract.ownerFor(IslandRendererMode.HYPERISLAND, hyperIslandInstalled = false),
        )
        assertFalse(
            IslandVisualContract.delegatesToHyperIsland(
                IslandRendererMode.HYPERISLAND,
                hyperIslandInstalled = false,
            ),
        )
    }

    @Test
    fun `explicit renderer ownership is stable when both packages are installed`() {
        assertEquals(
            IslandVisualContract.MIPUSH_OWNER,
            IslandVisualContract.ownerFor(IslandRendererMode.MIPUSH, hyperIslandInstalled = true),
        )
        assertEquals(
            IslandVisualContract.HYPERISLAND_OWNER,
            IslandVisualContract.ownerFor(IslandRendererMode.HYPERISLAND, hyperIslandInstalled = true),
        )
        assertTrue(
            IslandVisualContract.delegatesToHyperIsland(
                IslandRendererMode.HYPERISLAND,
                hyperIslandInstalled = true,
            ),
        )
    }
}
