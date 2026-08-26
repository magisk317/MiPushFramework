package io.github.magisk317.mipush.runtime.store.kmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationClassifierKmpTest {

    @Test
    fun `channel keyword takes priority`() {
        assertEquals(NotificationStyle.MESSAGE, NotificationClassifier.classify("hi", "hello", channelId = "msg_channel"))
        assertEquals(NotificationStyle.MEDIA, NotificationClassifier.classify("song", "playing", channelId = "media_playback"))
        assertEquals(NotificationStyle.PROGRESS, NotificationClassifier.classify("dl", "50%", channelId = "download_progress"))
    }

    @Test
    fun `content keyword beats package name`() {
        val style = NotificationClassifier.classify(
            title = "下载完成",
            content = "file.apk",
            packageName = "com.tencent.mm"
        )
        assertEquals(NotificationStyle.PROGRESS, style)
    }

    @Test
    fun `im package falls through to message`() {
        val style = NotificationClassifier.classify(
            title = "hello",
            content = "how are you",
            packageName = "com.tencent.mm"
        )
        assertEquals(NotificationStyle.MESSAGE, style)
    }

    @Test
    fun `unknown content returns general`() {
        val style = NotificationClassifier.classify(title = "test", content = "body")
        assertEquals(NotificationStyle.GENERAL, style)
    }
}
