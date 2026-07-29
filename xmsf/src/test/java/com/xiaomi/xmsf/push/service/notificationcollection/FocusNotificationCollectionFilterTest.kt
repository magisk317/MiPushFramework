package com.xiaomi.xmsf.push.service.notificationcollection

import android.app.Notification
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class FocusNotificationCollectionFilterTest {
    @Test
    fun `policy follows stock HyperOS version code boundary`() {
        assertEquals(
            FocusNotificationCollectionFilter.Policy.DISABLED,
            FocusNotificationCollectionFilter.policyForOsVersionCode(null),
        )
        assertEquals(
            FocusNotificationCollectionFilter.Policy.DISABLED,
            FocusNotificationCollectionFilter.policyForOsVersionCode("not-a-number"),
        )
        assertEquals(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_1,
            FocusNotificationCollectionFilter.policyForOsVersionCode("1"),
        )
        assertEquals(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_2,
            FocusNotificationCollectionFilter.policyForOsVersionCode("2"),
        )
        assertEquals(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_2,
            FocusNotificationCollectionFilter.policyForOsVersionCode("12"),
        )
    }

    @Test
    fun `HyperOS 2 filters by status bar key rather than package and id`() {
        val scheduler = RecordingTimeoutScheduler()
        val filter = hyperOs2Filter(scheduler)
        val deleted = sbn("com.example.app", id = 42, tag = "first")
        val sameIdentity = sbn(
            "com.example.app",
            id = 42,
            tag = "first",
            focusParam = ROOT_CLOSE,
        )
        val differentKey = sbn(
            "com.example.app",
            id = 42,
            tag = "second",
            focusParam = ROOT_CLOSE,
        )

        assertNotEquals(deleted.key, differentKey.key)
        filter.onNotificationRemoved(deleted, NotificationListenerService.REASON_CANCEL)

        assertTrue(filter.onNotificationPosted(sameIdentity))
        assertFalse(filter.onNotificationPosted(differentKey))
        assertEquals(listOf(deleted.key), scheduler.scheduled)
    }

    @Test
    fun `root reopen clears HyperOS 2 state and alarm`() {
        val scheduler = RecordingTimeoutScheduler()
        val filter = hyperOs2Filter(scheduler)
        val deleted = sbn("com.example.app", id = 7, tag = "trip")

        filter.onNotificationRemoved(deleted, NotificationListenerService.REASON_CANCEL)
        assertFalse(
            filter.onNotificationPosted(
                sbn("com.example.app", id = 7, tag = "trip", focusParam = ROOT_REOPEN),
            ),
        )

        assertFalse(filter.containsKey(deleted.key))
        assertEquals(listOf(deleted.key), scheduler.cancelled)
        assertFalse(
            filter.onNotificationPosted(
                sbn("com.example.app", id = 7, tag = "trip", focusParam = ROOT_CLOSE),
            ),
        )
    }

    @Test
    fun `stock root parser does not broaden collection filtering to param v2`() {
        val filter = hyperOs2Filter()
        val deleted = sbn("com.example.app", id = 8, tag = "delivery")
        filter.onNotificationRemoved(deleted, NotificationListenerService.REASON_CANCEL)

        assertFalse(
            filter.onNotificationPosted(
                sbn("com.example.app", id = 8, tag = "delivery", focusParam = PARAM_V2_CLOSE),
            ),
        )
        assertTrue(filter.containsKey(deleted.key))
        assertTrue(
            filter.onNotificationPosted(
                sbn("com.example.app", id = 8, tag = "delivery", focusParam = ROOT_CLOSE),
            ),
        )
    }

    @Test
    fun `HyperOS 1 records user dismiss only for stock allowlist and has no alarm`() {
        val scheduler = RecordingTimeoutScheduler()
        val filter = FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_1,
            scheduler,
        )
        val allowed = sbn("com.autonavi.minimap", id = 1, tag = "route")
        val excluded = sbn("com.example.app", id = 2, tag = "route")

        filter.onNotificationRemoved(allowed, NotificationListenerService.REASON_CANCEL)
        filter.onNotificationRemoved(excluded, NotificationListenerService.REASON_CANCEL)

        assertTrue(filter.containsKey(allowed.key))
        assertFalse(filter.containsKey(excluded.key))
        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test
    fun `HyperOS 2 excludes stock system packages from user dismiss state`() {
        val scheduler = RecordingTimeoutScheduler()
        val filter = hyperOs2Filter(scheduler)
        val excluded = sbn("com.android.settings", id = 1, tag = "settings")

        filter.onNotificationRemoved(excluded, NotificationListenerService.REASON_CANCEL)

        assertFalse(filter.containsKey(excluded.key))
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `HyperOS 2 reason 12 preserves state while other reasons clear it`() {
        val scheduler = RecordingTimeoutScheduler()
        val filter = hyperOs2Filter(scheduler)
        val notification = sbn("com.example.app", id = 9, tag = "group")

        filter.onNotificationRemoved(notification, NotificationListenerService.REASON_CANCEL)
        filter.onNotificationRemoved(
            notification,
            NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED,
        )
        assertTrue(filter.containsKey(notification.key))
        assertTrue(scheduler.cancelled.isEmpty())

        filter.onNotificationRemoved(notification, NotificationListenerService.REASON_APP_CANCEL)
        assertFalse(filter.containsKey(notification.key))
        assertEquals(listOf(notification.key), scheduler.cancelled)
    }

    @Test
    fun `HyperOS 2 timeout and capacity eviction clear matching alarms`() {
        val scheduler = RecordingTimeoutScheduler()
        val filter = hyperOs2Filter(scheduler)
        val notifications = (0..50).map { index ->
            sbn("com.example.app", id = index, tag = "tag-$index")
        }
        notifications.forEach { notification ->
            filter.onNotificationRemoved(notification, NotificationListenerService.REASON_CANCEL)
        }

        assertEquals(50, filter.size())
        assertFalse(filter.containsKey(notifications.first().key))
        assertTrue(scheduler.cancelled.contains(notifications.first().key))

        filter.onTimeout(notifications.last().key)
        assertFalse(filter.containsKey(notifications.last().key))
        assertTrue(scheduler.cancelled.contains(notifications.last().key))
    }

    @Test
    fun `new filter does not restore deleted state from a previous process instance`() {
        val deleted = sbn("com.example.app", id = 10, tag = "process")
        hyperOs2Filter().onNotificationRemoved(deleted, NotificationListenerService.REASON_CANCEL)

        val restartedFilter = hyperOs2Filter()

        assertFalse(
            restartedFilter.onNotificationPosted(
                sbn("com.example.app", id = 10, tag = "process", focusParam = ROOT_CLOSE),
            ),
        )
    }

    private fun hyperOs2Filter(
        scheduler: RecordingTimeoutScheduler = RecordingTimeoutScheduler(),
    ): FocusNotificationCollectionFilter {
        return FocusNotificationCollectionFilter(
            FocusNotificationCollectionFilter.Policy.HYPER_OS_2,
            scheduler,
        )
    }

    @Suppress("DEPRECATION")
    private fun sbn(
        packageName: String,
        id: Int,
        tag: String,
        focusParam: String? = null,
    ): StatusBarNotification {
        val notification = Notification().apply {
            focusParam?.let { extras.putString("miui.focus.param", it) }
        }
        return StatusBarNotification(
            packageName,
            packageName,
            id,
            tag,
            10_000,
            123,
            0,
            notification,
            Process.myUserHandle(),
            1_000L,
        )
    }

    private class RecordingTimeoutScheduler : FocusNotificationCollectionFilter.TimeoutScheduler {
        val scheduled = mutableListOf<String>()
        val cancelled = mutableListOf<String>()

        override fun schedule(key: String) {
            scheduled += key
        }

        override fun cancel(key: String) {
            cancelled += key
        }
    }

    companion object {
        private const val ROOT_CLOSE = """{"updatable":true,"reopen":"close"}"""
        private const val ROOT_REOPEN = """{"updatable":true,"reopen":"reopen"}"""
        private const val PARAM_V2_CLOSE =
            """{"param_v2":{"updatable":true,"reopen":"close"}}"""
    }
}
