package io.github.magisk317.mipush.feature.main

import io.github.magisk317.mipush.manager.notification.NotificationChannelGroupSummary
import io.github.magisk317.mipush.manager.notification.NotificationChannelSnapshot
import io.github.magisk317.mipush.manager.notification.NotificationChannelSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppConfigurationUtilsTest {
    @Test
    fun `notification sections use domain ownership and preserve native groups`() {
        val managedGroup = group(id = "managed", managedByMiPush = true)
        val nativeGroup = group(id = "native", managedByMiPush = false)
        val snapshot = NotificationChannelSnapshot(
            packageName = "com.example",
            isHooked = true,
            channels = listOf(
                channel(id = "managed-b", groupId = managedGroup.id, managedByMiPush = true),
                channel(id = "native-a", groupId = nativeGroup.id, managedByMiPush = false),
                channel(id = "managed-a", groupId = managedGroup.id, managedByMiPush = true),
            ),
            groups = listOf(nativeGroup, managedGroup),
        )

        val sections = AppConfigurationUtils.notificationChannelSections(snapshot)

        assertEquals(listOf(NotificationChannelSectionKind.MIPUSH, NotificationChannelSectionKind.NATIVE), sections.map { it.kind })
        assertEquals(listOf("managed-a", "managed-b"), sections[0].channels.map { it.id })
        assertEquals(managedGroup, sections[0].group)
        assertEquals(listOf("native-a"), sections[1].channels.map { it.id })
        assertEquals(nativeGroup, sections[1].group)
    }

    @Test
    fun `unhooked snapshot hides native channels`() {
        val snapshot = NotificationChannelSnapshot(
            packageName = "com.example",
            isHooked = false,
            channels = listOf(
                channel(id = "managed", managedByMiPush = true),
                channel(id = "native", managedByMiPush = false),
            ),
            groups = emptyList(),
        )

        val sections = AppConfigurationUtils.notificationChannelSections(snapshot)

        assertEquals(1, sections.size)
        assertEquals(listOf("managed"), sections.single().channels.map { it.id })
    }

    @Test
    fun `notification content distinguishes empty hidden and visible channel data`() {
        val empty = snapshot(isHooked = false)
        val hidden = snapshot(
            isHooked = false,
            channels = listOf(channel(id = "native")),
        )
        val visible = snapshot(
            isHooked = false,
            channels = listOf(channel(id = "managed", managedByMiPush = true)),
        )

        assertEquals(NotificationChannelContentKind.EMPTY, contentKind(empty))
        assertEquals(NotificationChannelContentKind.HIDDEN, contentKind(hidden))
        assertEquals(NotificationChannelContentKind.VISIBLE, contentKind(visible))
    }

    @Test
    fun `notification title and summary trust domain fields`() {
        val channel = channel(
            id = "channel-id",
            name = "   ",
            enabled = false,
            description = "description",
        )

        assertEquals("channel-id", AppConfigurationUtils.getNotificationTitle(channel))
        assertEquals("id: channel-id\ndescription", AppConfigurationUtils.getNotificationSummary(channel))
    }

    private fun channel(
        id: String,
        name: String = id,
        groupId: String? = null,
        enabled: Boolean = true,
        managedByMiPush: Boolean = false,
        description: String? = null,
    ) = NotificationChannelSummary(
        id = id,
        name = name,
        importance = 4,
        groupId = groupId,
        description = description,
        enabled = enabled,
        managedByMiPush = managedByMiPush,
    )

    private fun group(id: String, managedByMiPush: Boolean) = NotificationChannelGroupSummary(
        id = id,
        name = id,
        managedByMiPush = managedByMiPush,
    )

    private fun snapshot(
        isHooked: Boolean,
        channels: List<NotificationChannelSummary> = emptyList(),
    ) = NotificationChannelSnapshot(
        packageName = "com.example",
        isHooked = isHooked,
        channels = channels,
        groups = emptyList(),
    )

    private fun contentKind(snapshot: NotificationChannelSnapshot): NotificationChannelContentKind =
        notificationChannelContentKind(
            snapshot = snapshot,
            sections = AppConfigurationUtils.notificationChannelSections(snapshot),
        )
}
