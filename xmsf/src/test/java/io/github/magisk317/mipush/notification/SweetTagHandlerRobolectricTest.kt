package io.github.magisk317.mipush.notification

import android.graphics.Color
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SweetTagHandlerRobolectricTest {

    @Test
    fun `renderFtHtml strips ft tags and applies color and size spans`() {
        val rendered = SweetTagHandler.renderFtHtml(
            """hello <ft color="#ff0000" size="14">rich</ft> text"""
        )

        assertEquals("hello rich text", rendered.toString())
        assertTrue(
            rendered.getSpans(0, rendered.length, ForegroundColorSpan::class.java)
                .any { it.foregroundColor == Color.RED }
        )
        assertTrue(
            rendered.getSpans(0, rendered.length, AbsoluteSizeSpan::class.java)
                .any { it.size == 14 && it.dip }
        )
    }
}
