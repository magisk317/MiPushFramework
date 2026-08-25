package com.xiaomi.push.service

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.ConfigKey
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.push.pipeline.MockMessageRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MyMIPushNotificationHelperTest {
    @BeforeEach
    fun resetMockMessageRegistry() {
        MockMessageRegistry.clearAllForTests()
    }

    @Test
    fun `shouldPublishNotification allows displayable push payloads`() {
        val sendMessage = XmPushActionContainer().apply { action = ActionType.SendMessage }
        val displayNotification = XmPushActionContainer().apply {
            action = ActionType.Notification
            metaInfo = PushMetaInfo().apply {
                title = "title"
                description = "content"
                passThrough = 0
            }
        }
        val titleOnlyNotification = XmPushActionContainer().apply {
            action = ActionType.Notification
            metaInfo = PushMetaInfo().apply {
                title = "title"
                passThrough = 0
            }
        }
        val descriptionOnlyNotification = XmPushActionContainer().apply {
            action = ActionType.Notification
            metaInfo = PushMetaInfo().apply {
                description = "content"
                passThrough = 0
            }
        }
        val contentlessNotification = XmPushActionContainer().apply {
            action = ActionType.Notification
            metaInfo = PushMetaInfo().apply {
                passThrough = 0
            }
        }
        val ackNotification = XmPushActionContainer().apply {
            action = ActionType.Notification
            metaInfo = PushMetaInfo().apply {
                passThrough = 1
            }
        }
        val command = XmPushActionContainer().apply { action = ActionType.Command }

        assertTrue(MyMIPushNotificationHelper.shouldPublishNotification(sendMessage))
        assertTrue(MyMIPushNotificationHelper.shouldPublishNotification(displayNotification))
        assertTrue(MyMIPushNotificationHelper.shouldPublishNotification(titleOnlyNotification))
        assertTrue(MyMIPushNotificationHelper.shouldPublishNotification(descriptionOnlyNotification))
        assertFalse(MyMIPushNotificationHelper.shouldPublishNotification(contentlessNotification))
        assertFalse(MyMIPushNotificationHelper.shouldPublishNotification(ackNotification))
        assertFalse(MyMIPushNotificationHelper.shouldPublishNotification(command))
        assertFalse(MyMIPushNotificationHelper.shouldPublishNotification(XmPushActionContainer()))
    }

    @Test
    fun `shouldDispatchNonDisplayPayload allows only command-style server results`() {
        val registrationResult = XmPushActionContainer().apply {
            action = ActionType.Registration
            isRequest = false
        }
        val registrationRequest = XmPushActionContainer().apply {
            action = ActionType.Registration
            isRequest = true
        }
        val commandResult = XmPushActionContainer().apply {
            action = ActionType.Command
            isRequest = false
        }
        val notification = XmPushActionContainer().apply {
            action = ActionType.Notification
            isRequest = false
        }
        val sendMessage = XmPushActionContainer().apply {
            action = ActionType.SendMessage
            isRequest = false
        }

        assertTrue(MyMIPushNotificationHelper.shouldDispatchNonDisplayPayload(registrationResult))
        assertTrue(MyMIPushNotificationHelper.shouldDispatchNonDisplayPayload(commandResult))
        assertFalse(MyMIPushNotificationHelper.shouldDispatchNonDisplayPayload(registrationRequest))
        assertFalse(MyMIPushNotificationHelper.shouldDispatchNonDisplayPayload(notification))
        assertFalse(MyMIPushNotificationHelper.shouldDispatchNonDisplayPayload(sendMessage))
    }

    @Test
    fun `foreground display suppression is limited to MIUI payloads that opt out`() {
        assertTrue(
            MyMIPushNotificationHelper.shouldSuppressForegroundNotification(
                extra = mapOf("notify_foreground" to "0"),
                isMiui = true,
                isTargetForeground = true,
            ),
        )
        assertFalse(
            MyMIPushNotificationHelper.shouldSuppressForegroundNotification(
                extra = mapOf("notify_foreground" to "1"),
                isMiui = true,
                isTargetForeground = true,
            ),
        )
        assertFalse(
            MyMIPushNotificationHelper.shouldSuppressForegroundNotification(
                extra = emptyMap(),
                isMiui = true,
                isTargetForeground = true,
            ),
        )
        assertFalse(
            MyMIPushNotificationHelper.shouldSuppressForegroundNotification(
                extra = mapOf("notify_foreground" to "0"),
                isMiui = false,
                isTargetForeground = true,
            ),
        )
        assertFalse(
            MyMIPushNotificationHelper.shouldSuppressForegroundNotification(
                extra = mapOf("notify_foreground" to "0"),
                isMiui = true,
                isTargetForeground = false,
            ),
        )
    }

    @Test
    fun `shouldDropReplayNotification drops messages older than replay window`() {
        val sixHoursMs = 6 * 60 * 60 * 1000L
        val container = XmPushActionContainer().apply {
            action = ActionType.SendMessage
            metaInfo = PushMetaInfo().apply {
                id = "msg-1"
                setMessageTs(1_000L)
            }
        }

        // messageTs is 7 hours before session -> should drop
        assertTrue(
            MyMIPushNotificationHelper.shouldDropReplayNotification(
                container = container,
                sessionStartedAtMs = 1_000L + sixHoursMs + 3_600_000L,
            )
        )
        // messageTs is 5 hours before session -> within window, should keep
        assertFalse(
            MyMIPushNotificationHelper.shouldDropReplayNotification(
                container = container,
                sessionStartedAtMs = 1_000L + sixHoursMs - 3_600_000L,
            )
        )
        // messageTs equals session -> should keep
        assertFalse(
            MyMIPushNotificationHelper.shouldDropReplayNotification(
                container = container,
                sessionStartedAtMs = 1_000L,
            )
        )
    }

    @Test
    fun `shouldDropReplayNotification keeps payloads without message timestamp`() {
        val container = XmPushActionContainer().apply {
            action = ActionType.SendMessage
            metaInfo = PushMetaInfo().apply { id = "msg-1" }
        }

        assertFalse(MyMIPushNotificationHelper.shouldDropReplayNotification(container, sessionStartedAtMs = 1_000_000L))
    }

    @Test
    fun `getNotificationId preserves stock replacement semantics when notifyId repeats`() {
        val first = notificationContainer("job-123")
        val second = notificationContainer("job-456")
        val expected = ("com.ruanmei.ithome".hashCode() / 10) * 10 + 42

        assertEquals(expected, MyMIPushNotificationHelper.getNotificationId(first))
        assertEquals(expected, MyMIPushNotificationHelper.getNotificationId(second))
    }

    @Test
    fun `getNotificationId uses stable source identity for mock replay notifications`() {
        val container = notificationContainer("job-replay")
        val regularId = MyMIPushNotificationHelper.getNotificationId(container)

        MockMessageRegistry.mark(container)
        container.metaInfo.putToExtra(MockMessageRegistry.EXTRA_MOCK_REPLAY_SOURCE_ID, "job-replay")

        val firstReplayId = MyMIPushNotificationHelper.getNotificationId(container)
        val secondReplayId = MyMIPushNotificationHelper.getNotificationId(container)

        assertNotEquals(regularId, firstReplayId)
        assertEquals(firstReplayId, secondReplayId)
        assertEquals("com.ruanmei.ithome_mock_replay:job-replay".hashCode(), firstReplayId)
    }

    @Test
    fun `getNotificationId uses stock zero notifyId when notifyId absent`() {
        val container = XmPushActionContainer().apply {
            packageName = "com.example.app"
            metaInfo = PushMetaInfo().apply {
                id = "meta-id"
                putToExtra(PushConstants.EXTRA_JOB_KEY, "job-123")
            }
        }

        val expected = ("com.example.app".hashCode() / 10) * 10
        assertEquals(expected, MyMIPushNotificationHelper.getNotificationId(container))
    }

    @Test
    fun `getNotificationId keeps notifyId for update style notifications`() {
        val voip = notificationContainer("job-voip").apply {
            metaInfo.putToExtra("msg_busi_type", "voip")
            metaInfo.putToExtra("voip_type", "1")
        }
        val focus = notificationContainer("job-focus").apply {
            metaInfo.putToExtra("miui.focus.param", """{"updatable":true,"reopen":"close"}""")
        }
        val liveUpdate = notificationContainer("job-live").apply {
            metaInfo.title = "外卖配送"
            metaInfo.description = "骑手正在配送中，预计5分钟送达"
        }

        val expected = ("com.ruanmei.ithome".hashCode() / 10) * 10 + 42
        assertEquals(expected, MyMIPushNotificationHelper.getNotificationId(voip))
        assertEquals(expected, MyMIPushNotificationHelper.getNotificationId(focus))
        assertEquals(expected, MyMIPushNotificationHelper.getNotificationId(liveUpdate))
    }

    @Test
    fun `stock group remains absent when payload does not request grouping`() {
        assertEquals(
            null,
            MyMIPushNotificationHelper.resolveStockGroup(
                targetPackage = "com.example.target",
                sourceGroup = null,
                disableDefault = false,
                isMiui = true,
            ),
        )
    }

    @Test
    fun `stock group uses target package by default and preserves source only when disabled off MIUI`() {
        assertEquals(
            "com.example.target",
            MyMIPushNotificationHelper.resolveStockGroup(
                targetPackage = "com.example.target",
                sourceGroup = "orders",
                disableDefault = false,
                isMiui = true,
            ),
        )
        assertEquals(
            "com.example.target",
            MyMIPushNotificationHelper.resolveStockGroup(
                targetPackage = "com.example.target",
                sourceGroup = "orders",
                disableDefault = false,
                isMiui = false,
            ),
        )
        assertEquals(
            "orders",
            MyMIPushNotificationHelper.resolveStockGroup(
                targetPackage = "com.example.target",
                sourceGroup = "orders",
                disableDefault = true,
                isMiui = false,
            ),
        )
    }

    @Test
    fun `Android W group strategy follows stock dex control flow`() {
        assertTrue(MyMIPushNotificationHelper.shouldSkipForceGroup("orders", strategy = 1))
        assertFalse(MyMIPushNotificationHelper.shouldSkipForceGroup(null, strategy = 1))
        assertTrue(MyMIPushNotificationHelper.shouldSkipForceGroup(null, strategy = 2))
        assertFalse(MyMIPushNotificationHelper.shouldSkipForceGroup("orders", strategy = 3))
        assertTrue(MyMIPushNotificationHelper.shouldSkipForceGroup("orders", strategy = 99))
    }

    @Test
    fun `stock 7x notification config ids are pinned`() {
        assertEquals(140, ConfigKey.OnetrackSwitch.value)
        assertEquals(141, ConfigKey.NotificationGroupUpdateTimeSwitch.value)
        assertEquals(142, ConfigKey.KASwitch.value)
        assertEquals(208, ConfigKey.AndroidWGroupStrategy.value)
        assertEquals(ConfigKey.OnetrackSwitch, ConfigKey.findByValue(140))
        assertEquals(ConfigKey.NotificationGroupUpdateTimeSwitch, ConfigKey.findByValue(141))
        assertEquals(ConfigKey.KASwitch, ConfigKey.findByValue(142))
        assertEquals(ConfigKey.AndroidWGroupStrategy, ConfigKey.findByValue(208))
    }

    @Test
    fun `stock MIUI identity extras include target message id and event type`() {
        val extras = MyMIPushNotificationHelper.buildStockMiuiIdentityExtras(
            targetPackage = "com.ruanmei.ithome",
            messageId = "s123456789012345678901",
            eventMessageType = 1000,
        )

        assertEquals("com.ruanmei.ithome", extras["target_package"])
        assertEquals("s123456789012345678901", extras["message_id"])
        assertEquals("1000", extras["eventMessageType"])
    }

    private fun notificationContainer(jobKey: String): XmPushActionContainer {
        return XmPushActionContainer().apply {
            packageName = "com.ruanmei.ithome"
            action = ActionType.SendMessage
            metaInfo = PushMetaInfo().apply {
                id = "meta-$jobKey"
                title = "新闻标题"
                description = "新闻内容"
                setNotifyId(42)
                putToExtra(PushConstants.EXTRA_JOB_KEY, jobKey)
            }
        }
    }
}
