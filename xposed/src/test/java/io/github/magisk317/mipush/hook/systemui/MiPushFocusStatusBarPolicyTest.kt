package io.github.magisk317.mipush.hook.systemui

import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiPushFocusStatusBarPolicyTest {
    @Test
    fun `only MiPush island owner marker is treated as proxy focus`() {
        assertTrue(
            MiPushFocusStatusBarPolicy.hasMiPushIslandOwner {
                if (it == IslandDispatchContract.OWNER) IslandDispatchContract.OWNER_MARKER else null
            }
        )

        assertFalse(
            MiPushFocusStatusBarPolicy.hasMiPushIslandOwner {
                if (it == IslandDispatchContract.OWNER) "io.github.hyperisland" else null
            }
        )
        assertFalse(MiPushFocusStatusBarPolicy.hasMiPushIslandOwner { null })
    }
}
