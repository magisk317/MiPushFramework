package io.github.magisk317.mipush.hook.xmsf.nm

import io.github.magisk317.mipush.common.notification.NotificationDumpCommandContract
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
    fun `root dump reader does not execute plain after valid noredact`() {
        val commands = mutableListOf<String>()
        val expected = validDump("noredact")

        val actual = RootNotificationHelper.readNotificationServiceDump { command ->
            commands += command
            expected
        }

        assertEquals(expected, actual)
        assertEquals(listOf(NotificationDumpCommandContract.NOREDACT_COMMAND), commands)
    }

    @Test
    fun `root dump reader falls back after failed noredact`() {
        val commands = mutableListOf<String>()
        val expected = validDump("plain")

        val actual = RootNotificationHelper.readNotificationServiceDump { command ->
            commands += command
            if (command == NotificationDumpCommandContract.NOREDACT_COMMAND) null else expected
        }

        assertEquals(expected, actual)
        assertEquals(expectedCommandOrder(), commands)
    }

    @Test
    fun `root dump reader falls back after invalid noredact output`() {
        val commands = mutableListOf<String>()
        val expected = validDump("plain")

        val actual = RootNotificationHelper.readNotificationServiceDump { command ->
            commands += command
            if (command == NotificationDumpCommandContract.NOREDACT_COMMAND) {
                "notification service unavailable"
            } else {
                expected
            }
        }

        assertEquals(expected, actual)
        assertEquals(expectedCommandOrder(), commands)
    }

    @Test
    fun `root dump reader accepts and parses simple noredact format`() {
        val commands = mutableListOf<String>()
        val expected = "channelId=legacy_messages importance=2"

        val actual = RootNotificationHelper.readNotificationServiceDump { command ->
            commands += command
            expected
        }
        val channels = RootNotificationHelper.parseChannels(
            requireNotNull(actual),
            "com.example.app",
        ).filterNotNull()

        assertEquals(listOf(NotificationDumpCommandContract.NOREDACT_COMMAND), commands)
        assertEquals(1, channels.size)
        assertEquals("legacy_messages", channels.single().id)
        assertEquals(2, channels.single().importance)
    }

    @Test
    fun `root dump reader accepts and parses group-only noredact format`() {
        val commands = mutableListOf<String>()
        val expected = "NotificationChannelGroup{id=social name=Social}"

        val actual = RootNotificationHelper.readNotificationServiceDump { command ->
            commands += command
            expected
        }
        val groups = RootNotificationHelper.parseGroups(
            requireNotNull(actual),
            "com.example.app",
        ).filterNotNull()

        assertEquals(listOf(NotificationDumpCommandContract.NOREDACT_COMMAND), commands)
        assertEquals(1, groups.size)
        assertEquals("social", groups.single().id)
    }

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

    @Test
    fun `parse channels keeps full name when vibration effect nests braces`() {
        val output = """
            NotificationChannel{mId='message_channel_new_id', mName=新消息通知, mDescription=hasDescription , mImportance=4, mBypassDnd=false, mVibrationPattern=[300, 200, 300, 200], mVibrationEffect=Composed{segments=[Step{amplitude=0.0, frequencyHz=0.0, duration=300}, Step{amplitude=-1.0, frequencyHz=0.0, duration=200}], repeat=-1}, mGroup='null'}
        """.trimIndent()

        val channels = RootNotificationHelper.parseChannels(output, "com.example.app")
            .filterNotNull()

        assertEquals(1, channels.size)
        assertEquals("message_channel_new_id", channels.single().id)
        assertEquals("新消息通知", channels.single().name.toString())
        assertEquals(4, channels.single().importance)
    }

    @Test
    fun `parse channels keeps long chinese and english names from app settings dump`() {
        val output = """
            AppSettings: com.ruanmei.ithome (10357) importance=NONE userSet=false
              NotificationChannel{mId='ch_com.ruanmei.ithome_118557', mName=订阅今日要闻, mDescription=hasDescription , mImportance=4, mBypassDnd=false, mGroup='gp_com.ruanmei.ithome'}
              NotificationChannel{mId='mipush_mock_replay_receipt', mName=MiPush replay, mDescription=, mImportance=4, mBypassDnd=false, mGroup='null'}
        """.trimIndent()

        val channels = RootNotificationHelper.parseChannels(output, "com.ruanmei.ithome")
            .filterNotNull()
            .associateBy { it.id }

        assertEquals("订阅今日要闻", channels.getValue("ch_com.ruanmei.ithome_118557").name.toString())
        assertEquals("MiPush replay", channels.getValue("mipush_mock_replay_receipt").name.toString())
    }

    @Test
    fun `scoped parser selects target uid after an empty package namespace`() {
        assertCoolapkUidScope(coolapkDuplicateDump(targetFirst = false))
    }

    @Test
    fun `scoped parser selects target uid before an empty package namespace`() {
        assertCoolapkUidScope(coolapkDuplicateDump(targetFirst = true))
    }

    @Test
    fun `scoped parser skips an empty namespace when uid is unavailable`() {
        val scoped = RootNotificationHelper.selectAppSettingsBlock(
            output = coolapkDuplicateDump(targetFirst = false),
            packageName = "com.coolapk.market",
            packageUid = null,
        )

        val channels = RootNotificationHelper.parseChannels(scoped, "com.coolapk.market")
            .filterNotNull()

        assertEquals(listOf("messages", "mipush|com.coolapk.market|105272"), channels.map { it.id })
    }

    private fun assertCoolapkUidScope(output: String) {
        val scoped = RootNotificationHelper.selectAppSettingsBlock(
            output = output,
            packageName = "com.coolapk.market",
            packageUid = 10329,
        )
        val channels = RootNotificationHelper.parseChannels(scoped, "com.coolapk.market")
            .filterNotNull()
        val groups = RootNotificationHelper.parseGroups(scoped, "com.coolapk.market")
            .filterNotNull()

        assertEquals(listOf("messages", "mipush|com.coolapk.market|105272"), channels.map { it.id })
        assertEquals(listOf("gp_com.coolapk.market"), groups.map { it.id })
    }

    private fun coolapkDuplicateDump(targetFirst: Boolean): String {
        val emptyBlock = "AppSettings: com.coolapk.market (1000)"
        val targetBlock = """
            AppSettings: com.coolapk.market (10329) importance=DEFAULT userSet=true
              NotificationChannel{mId='messages', mName=Messages, mImportance=3, mGroup='null'}
              NotificationChannel{mId='mipush|com.coolapk.market|105272', mName=Community, mImportance=3, mGroup='gp_com.coolapk.market'}
              NotificationChannelGroup{mId='gp_com.coolapk.market', mName=Mi Push}
        """.trimIndent()
        val packageBlocks = if (targetFirst) {
            "$targetBlock\n$emptyBlock"
        } else {
            "$emptyBlock\n$targetBlock"
        }
        return """
            $packageBlocks
            AppSettings: com.example.other (10400) importance=DEFAULT userSet=true
              NotificationChannel{mId='other', mName=Other, mImportance=3}
              NotificationChannelGroup{mId='other_group', mName=Other}
        """.trimIndent()
    }

    private fun expectedCommandOrder(): List<String> = listOf(
        NotificationDumpCommandContract.NOREDACT_COMMAND,
        NotificationDumpCommandContract.PLAIN_COMMAND,
    )

    private fun validDump(name: String): String =
        "NotificationChannel{id=messages name=$name importance=3}"
}
