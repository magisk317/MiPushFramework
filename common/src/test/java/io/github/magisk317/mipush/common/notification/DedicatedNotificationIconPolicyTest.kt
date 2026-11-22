package io.github.magisk317.mipush.common.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DedicatedNotificationIconPolicyTest {
    @Test
    fun candidateNamesStayConservativeAndDeterministic() {
        assertEquals(
            listOf(
                "appicon_push",
                "push_icon",
                "notification_icon",
                "notification_small_icon",
                "small_icon",
                "status_bar_icon",
                "ic_notification",
            ),
            DedicatedNotificationIconPolicy.candidateNames,
        )
    }

    @Test
    fun validGrayscaleGlyphNeedsTransparentMarginAndVisiblePixels() {
        assertTrue(DedicatedNotificationIconPolicy.isValidStatusGlyph(16, 16, glyph()))
    }

    @Test
    fun coloredFullBleedEmptyAndTinyResourcesAreRejected() {
        val colored = glyph().also { it[8 * 16 + 8] = 0xFFFF0000.toInt() }
        assertFalse(DedicatedNotificationIconPolicy.isValidStatusGlyph(16, 16, colored))

        val fullBleed = IntArray(16 * 16) { 0xFFFFFFFF.toInt() }
        assertFalse(DedicatedNotificationIconPolicy.isValidStatusGlyph(16, 16, fullBleed))
        assertFalse(DedicatedNotificationIconPolicy.isValidStatusGlyph(16, 16, IntArray(16 * 16)))
        assertFalse(DedicatedNotificationIconPolicy.isValidStatusGlyph(15, 16, glyph()))
    }

    private fun glyph(): IntArray = IntArray(16 * 16).also { pixels ->
        for (y in 3..12) {
            for (x in 3..12) pixels[y * 16 + x] = 0xFFFFFFFF.toInt()
        }
    }
}
