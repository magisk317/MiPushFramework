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
    fun `parse channels supports mId dumpsys format without crossing into notification records`() {
        val output = """
            NotificationChannel{mId='ch_com.ss.android.lark_group_chat', mName=群聊消息, mDescription=, mImportance=3, mBypassDnd=false, mGroup='gp_com.ss.android.lark'}
            NotificationRecord(0x123: pkg=com.ss.android.lark user=UserHandle{0} id=11890 tag=mipush_com.ss.android.lark)
            NotificationChannel{mId='normal_v2', mName=普通消息, mDescription=hasDescription , mImportance=4, mBypassDnd=false, mGroup='null'}
        """.trimIndent()

        val channels = RootNotificationHelper.parseChannels(output, "com.ss.android.lark")
            .filterNotNull()

        assertEquals(listOf("ch_com.ss.android.lark_group_chat", "normal_v2"), channels.map { it.id })
        assertEquals(listOf(3, 4), channels.map { it.importance })
        assertEquals(listOf("群聊消息", "普通消息"), channels.map { it.name.toString() })
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
    fun `parse channel groups supports mId dumpsys format`() {
        val output = """
            NotificationChannelGroup{mId='gp_com.ss.android.lark', mName=飞书, mDescription=, mBlocked=false, mChannels=[], mUserLockedFields=0}
        """.trimIndent()

        val groups = RootNotificationHelper.parseGroups(output, "com.ss.android.lark")
            .filterNotNull()

        assertEquals(1, groups.size)
        assertEquals("gp_com.ss.android.lark", groups.single().id)
        assertEquals("飞书", groups.single().name.toString())
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
