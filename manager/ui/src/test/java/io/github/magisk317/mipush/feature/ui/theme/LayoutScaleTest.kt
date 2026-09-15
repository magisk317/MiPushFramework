package io.github.magisk317.mipush.feature.ui.theme

import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.theme.Spacing
import io.github.magisk317.uikit.theme.UiKitLayoutScale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LayoutScaleTest {
    @Test
    fun `unknown persisted values fall back to standard`() {
        assertEquals(UiKitLayoutScale.Standard, UiKitLayoutScale.fromValue(-1))
        assertEquals(UiKitLayoutScale.Standard, UiKitLayoutScale.fromValue(99))
    }

    @Test
    fun `scale factors remain finite and ordered`() {
        assertEquals(0.92f, UiKitLayoutScale.Compact.spacingFactor)
        assertEquals(1f, UiKitLayoutScale.Standard.spacingFactor)
        assertEquals(1.08f, UiKitLayoutScale.Comfortable.spacingFactor)
    }

    @Test
    fun `spacing scales without changing the token set`() {
        val compact = Spacing().scaled(UiKitLayoutScale.Compact.spacingFactor)
        val standard = Spacing().scaled(UiKitLayoutScale.Standard.spacingFactor)
        val comfortable = Spacing().scaled(UiKitLayoutScale.Comfortable.spacingFactor)

        assertEquals(4.dp, standard.extraSmall)
        assertEquals(8.dp * 0.92f, compact.small)
        assertEquals(16.dp * 1.08f, comfortable.large)
    }
}
