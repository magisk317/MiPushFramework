package io.github.magisk317.mipush.common.notification

import android.app.Notification
import android.os.Bundle
import android.widget.RemoteViews
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class NotificationContentSupportTest {
    @Test
    fun `firstText returns first non-blank char sequence`() {
        val extras = Bundle().apply {
            putString("blank", "  ")
            putCharSequence("title", StringBuilder("Title"))
            putString("later", "Later")
        }

        assertEquals(
            "Title",
            NotificationContentSupport.firstText(extras, "missing", "blank", "title", "later"),
        )
        assertNull(NotificationContentSupport.firstText(null, "title"))
    }

    @Test
    fun `visible text normalization removes whitespace format control and surrogate characters`() {
        val raw = " \nA\u200B\u0000B\uD83D\uDE00\t"

        assertEquals("AB", NotificationContentSupport.normalizedVisibleText(raw))
        assertEquals("", NotificationContentSupport.normalizedVisibleText(null))
    }

    @Test
    fun `visible candidates cover standard extras ticker and inbox lines`() {
        val notification = notification(
            title = " Title ",
            text = "Body",
            lines = arrayOf("Line 1", "Line 2"),
            ticker = "Ticker",
        )

        val candidates = NotificationContentSupport.normalizedVisibleTextCandidates(notification)

        assertTrue(candidates.containsAll(listOf("Title", "Body", "Ticker", "Line1", "Line2")))
    }

    @Test
    fun `contextual text ignores package label and channel metadata`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val applicationLabel = NotificationContentSupport.applicationLabelOrNull(context, packageName)
        assertNotNull(applicationLabel)

        assertFalse(
            NotificationContentSupport.hasMeaningfulVisibleText(
                context = context,
                packageName = packageName,
                notification = notification(title = applicationLabel),
                channelName = "Messages",
                channelDescription = "Message notifications",
            )
        )

        assertTrue(
            NotificationContentSupport.hasMeaningfulVisibleText(
                context = context,
                packageName = packageName,
                notification = notification(text = "New message"),
                channelName = "Messages",
                channelDescription = "Message notifications",
            )
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun `custom RemoteViews count as meaningful visible content`() {
        val context = RuntimeEnvironment.getApplication()
        val notification = notification().apply {
            contentView = RemoteViews(context.packageName, android.R.layout.simple_list_item_1)
        }

        assertTrue(
            NotificationContentSupport.hasMeaningfulVisibleContent(
                context = context,
                packageName = context.packageName,
                notification = notification,
            )
        )
    }

    private fun notification(
        title: CharSequence? = null,
        text: CharSequence? = null,
        lines: Array<CharSequence>? = null,
        ticker: CharSequence? = null,
    ): Notification {
        return Notification().apply {
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, title)
                putCharSequence(Notification.EXTRA_TEXT, text)
                putCharSequenceArray(Notification.EXTRA_TEXT_LINES, lines)
            }
            tickerText = ticker
        }
    }
}
