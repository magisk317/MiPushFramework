package io.github.magisk317.mipush.hook.util

import android.app.Notification
import android.os.Bundle
import android.widget.RemoteViews
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class NotificationTest {
    @Test
    @Suppress("DEPRECATION")
    fun `hook notification extensions delegate visible content policy to common`() {
        val context = RuntimeEnvironment.getApplication()
        val notification = Notification().apply {
            extras = Bundle().apply {
                putString(Notification.EXTRA_TITLE, " Hook title ")
                putCharSequenceArray(Notification.EXTRA_TEXT_LINES, arrayOf("Line 1"))
            }
            contentView = RemoteViews(context.packageName, android.R.layout.simple_list_item_1)
        }

        assertEquals(listOf("Hooktitle", "", "", "", "", "", "", "Line1"), notification.normalizedVisibleTextCandidates())
        assertTrue(notification.hasMeaningfulVisibleText())
        assertTrue(notification.hasCustomVisualContent())
        assertEquals("AB", " A\u200BB ".normalizedVisibleText())
    }
}
