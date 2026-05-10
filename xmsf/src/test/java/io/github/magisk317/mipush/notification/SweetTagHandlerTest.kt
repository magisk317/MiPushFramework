package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SweetTagHandlerTest {

    @Test
    fun `containsFtTag detects rich text marker case insensitively`() {
        assertTrue(SweetTagHandler.containsFtTag("""hello <ft color="#ff0000">red</ft>"""))
        assertTrue(SweetTagHandler.containsFtTag("""hello <FT size="14">big</FT>"""))
        assertFalse(SweetTagHandler.containsFtTag("plain text"))
        assertFalse(SweetTagHandler.containsFtTag(null))
    }
}
