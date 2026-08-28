package io.github.magisk317.mipush.common.island

import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_RENDERER_MODE
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.common.LOG_SANITIZATION_ENABLED_KEY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IslandOptionsTest {
    @Test
    fun `preference flags preserve wire values and normalize timeout`() {
        val flags = IslandOptions(
            enabled = false,
            timeoutSecs = 0,
            rendererMode = IslandRendererMode.HYPERISLAND,
        ).toPreferenceFlags(logSanitizationEnabled = true)

        assertEquals("0", flags.getValue(ISLAND_PREF_ENABLED))
        assertEquals("1", flags.getValue(ISLAND_PREF_TIMEOUT))
        assertEquals("hyperisland", flags.getValue(ISLAND_PREF_RENDERER_MODE))
        assertEquals("1", flags.getValue(LOG_SANITIZATION_ENABLED_KEY))
    }
}
