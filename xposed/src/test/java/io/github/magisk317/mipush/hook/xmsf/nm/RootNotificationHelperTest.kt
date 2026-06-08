package io.github.magisk317.mipush.hook.xmsf.nm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
class RootNotificationHelperTest {
    @Test
    fun `parse channels skips zen mode policy records`() {
        val output = """
            NotificationChannel{id=sleep importance=3 name=睡眠,zenMode=ZEN_MODE_IMPORTANT_INTERRUPTIONS,conditionId=condition://android/schedule?days=1.2.3.4.5.6.7&start=22.00&end=7.00}
            NotificationChannel{id=chat importance=4 name=聊天通知}
        """.trimIndent()

        val channels = RootNotificationHelper.parseChannels(output, "com.ss.android.ugc.aweme")
            .filterNotNull()

        assertEquals(1, channels.size)
        assertEquals("chat", channels.single().id)
    }

    @Test
    fun `parse channel groups skips zen mode policy records`() {
        val output = """
            NotificationChannelGroup{id=sleep name=睡眠,conditionId=condition://android/schedule?component=ConditionProvider}
            NotificationChannelGroup{id=social name=社交通知}
        """.trimIndent()

        val groups = RootNotificationHelper.parseGroups(output, "com.ss.android.ugc.aweme")
            .filterNotNull()

        assertEquals(1, groups.size)
        assertEquals("social", groups.single().id)
    }

    @Test
    fun `parse simple channels drops policy fallback`() {
        val output = """
            conditionId=condition://android/schedule?days=1.2.3.4.5.6.7 channelId=sleep importance=3
        """.trimIndent()

        val channels = RootNotificationHelper.parseChannels(output, "com.ss.android.ugc.aweme")

        assertTrue(channels.isEmpty())
    }
}
