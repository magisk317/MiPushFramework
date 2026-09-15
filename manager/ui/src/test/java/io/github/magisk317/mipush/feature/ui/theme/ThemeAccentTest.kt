package io.github.magisk317.mipush.feature.ui.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemeAccentTest {
    @Test
    fun `unknown enum value falls back to system accent`() {
        assertEquals(ThemeAccent.System, ThemeAccent.fromValue(99))
    }

    @Test
    fun `unknown color falls back to system accent`() {
        assertEquals(ThemeAccent.System, ThemeAccent.fromArgb(0xFF123456.toInt()))
    }

    @Test
    fun `fixed accents have stable persisted colors`() {
        ThemeAccent.entries
            .filter { it != ThemeAccent.System }
            .forEach { accent ->
                assertEquals(accent, ThemeAccent.fromArgb(accent.colorArgb!!))
            }
    }
}
