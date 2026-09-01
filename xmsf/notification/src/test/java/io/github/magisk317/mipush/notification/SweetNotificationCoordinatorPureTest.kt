package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SweetNotificationCoordinatorPureTest {

    @Test
    fun `style five maps stock card fields to standard content`() {
        assertNull(SweetNotificationCoordinator.resolveCardContent(emptyMap()))

        val content = SweetNotificationCoordinator.resolveCardContent(
            mapOf(
                SweetNotificationCoordinator.EXTRA_STYLE_TYPE to "5",
                "notify_style_5_alert" to "Delivery",
                "notify_style_5_left" to "Rider nearby",
                "notify_style_5_right" to "2 min",
                "notify_style_5_bg_pic" to "https://example.invalid/card.png",
            ),
        )

        assertEquals("Delivery", content?.title)
        assertEquals("Rider nearby  2 min", content?.text)
        assertEquals("https://example.invalid/card.png", content?.backgroundUri)
    }

    @Test
    fun `duration follows stock lower and upper bounds`() {
        assertEquals(180, SweetNotificationCoordinator.clampDuration(null))
        assertEquals(180, SweetNotificationCoordinator.clampDuration(-1))
        assertEquals(181, SweetNotificationCoordinator.clampDuration(181))
        assertEquals(7_200, SweetNotificationCoordinator.clampDuration(9_000))
    }

    @Test
    fun `older sequence is suppressed but equal sequence can replace`() {
        assertTrue(
            SweetNotificationCoordinator.decidePreflight(
                incomingStatus = "courier_arriving",
                incomingSequence = 9,
                previousSequence = 10,
                milepostStatus = null,
                clickedStatus = null,
                activeNotification = false,
            ).suppress,
        )
        assertFalse(
            SweetNotificationCoordinator.decidePreflight(
                incomingStatus = "courier_arriving",
                incomingSequence = 10,
                previousSequence = 10,
                milepostStatus = null,
                clickedStatus = null,
                activeNotification = false,
            ).suppress,
        )
    }

    @Test
    fun `clicked status is suppressed only without an active reminder`() {
        val repeated = SweetNotificationCoordinator.decidePreflight(
            incomingStatus = "delivered",
            incomingSequence = 2,
            previousSequence = 2,
            milepostStatus = "delivered",
            clickedStatus = "delivered",
            activeNotification = false,
        )
        assertTrue(repeated.suppress)
        assertTrue(repeated.refreshMilepost)

        assertFalse(
            SweetNotificationCoordinator.decidePreflight(
                incomingStatus = "delivered",
                incomingSequence = 2,
                previousSequence = 2,
                milepostStatus = "delivered",
                clickedStatus = "delivered",
                activeNotification = true,
            ).suppress,
        )
    }

    @Test
    fun `milepost parser and job identity match stock persistence`() {
        assertEquals(
            SweetNotificationCoordinator.Milepost(1_000L, "arriving", 180),
            SweetNotificationCoordinator.parseMilepost("1000-arriving-30"),
        )
        assertNull(SweetNotificationCoordinator.parseMilepost("broken"))
        assertEquals(
            "n_sweet_timeout_999_42_com.example.app",
            SweetNotificationCoordinator.jobId("com.example.app", 42, 999),
        )
        assertEquals(
            "n_sweet_timeout_42_com.example.app",
            SweetNotificationCoordinator.jobId("com.example.app", 42, userId = 0),
        )
        assertEquals(
            SweetNotificationCoordinator.TrackedNotification("com.example.app", 42, 999),
            SweetNotificationCoordinator.parseStateKey("999|com.example.app-42"),
        )
        assertEquals(
            SweetNotificationCoordinator.TrackedNotification("com.example.app", 42, 0),
            SweetNotificationCoordinator.parseStateKey("com.example.app-42"),
        )
    }

    @Test
    fun `only stock user removal reasons record clicked status`() {
        assertTrue(SweetNotificationCoordinator.shouldRecordUserRemoval(1))
        assertTrue(SweetNotificationCoordinator.shouldRecordUserRemoval(2))
        assertTrue(SweetNotificationCoordinator.shouldRecordUserRemoval(3))
        assertFalse(SweetNotificationCoordinator.shouldRecordUserRemoval(8))
        assertFalse(SweetNotificationCoordinator.shouldRecordUserRemoval(19))
    }

    @Test
    fun `normal replacement cancels prior reminder without treating another reminder as replacement`() {
        assertTrue(SweetNotificationCoordinator.shouldCancelPriorReminder(null, activeReminder = true))
        assertTrue(SweetNotificationCoordinator.shouldCancelPriorReminder("", activeReminder = true))
        assertFalse(SweetNotificationCoordinator.shouldCancelPriorReminder("arriving", activeReminder = true))
        assertFalse(SweetNotificationCoordinator.shouldCancelPriorReminder(null, activeReminder = false))
    }

    @Test
    fun `screen on restores keyguard only for tracked sweet notifications`() {
        assertTrue(
            SweetNotificationCoordinator.shouldRestoreKeyguard(
                notificationId = 42,
                trackedIds = setOf(42),
                remindStatus = "arriving",
                keyguardEnabled = false,
            ),
        )
        assertFalse(
            SweetNotificationCoordinator.shouldRestoreKeyguard(
                notificationId = 41,
                trackedIds = setOf(42),
                remindStatus = "arriving",
                keyguardEnabled = false,
            ),
        )
        assertFalse(
            SweetNotificationCoordinator.shouldRestoreKeyguard(
                notificationId = 42,
                trackedIds = setOf(42),
                remindStatus = "arriving",
                keyguardEnabled = true,
            ),
        )
    }

    @Test
    fun `reminder identity falls back to status and sequence when generation is unavailable`() {
        assertTrue(
            SweetNotificationCoordinator.matchesReminderIdentity(
                expectedGeneration = 12,
                expectedStatus = "arriving",
                expectedSequence = "3",
                candidateGeneration = 0,
                candidateStatus = "arriving",
                candidateSequence = "3",
            ),
        )
        assertFalse(
            SweetNotificationCoordinator.matchesReminderIdentity(
                expectedGeneration = 12,
                expectedStatus = "arriving",
                expectedSequence = "3",
                candidateGeneration = 11,
                candidateStatus = "arriving",
                candidateSequence = "3",
            ),
        )
        assertFalse(
            SweetNotificationCoordinator.matchesReminderIdentity(
                expectedGeneration = 12,
                expectedStatus = "arriving",
                expectedSequence = "3",
                candidateGeneration = 0,
                candidateStatus = "delivered",
                candidateSequence = "4",
            ),
        )
    }

    @Test
    fun `foreground detection searches every running process for the target package`() {
        assertTrue(
            SweetNotificationCoordinator.hasForegroundTargetProcess(
                processNames = listOf("com.android.systemui", "com.example.target", null),
                packageName = "com.example.target",
            ),
        )
        assertFalse(
            SweetNotificationCoordinator.hasForegroundTargetProcess(
                processNames = listOf("com.android.systemui", "com.example.other"),
                packageName = "com.example.target",
            ),
        )
    }
}
