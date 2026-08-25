package io.github.magisk317.mipush.runtime

import io.github.magisk317.mipush.runtime.android.AndroidPushRuntime
import io.github.magisk317.mipush.runtime.android.AndroidPushRuntimeNotificationObservationAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PushRuntimeNotificationObservationAdapterTest {
    @BeforeEach
    fun resetRuntime() {
        AndroidPushRuntime.clearStateForTests()
    }

    @Test
    fun `adapter publishes notification observations to runtime state`() {
        val adapter = AndroidPushRuntimeNotificationObservationAdapter

        adapter.observeNotificationEvent(
            packageName = "com.example.app",
            event = "notify_push_message",
            source = "notification-test",
        )
        adapter.observeNotificationEvent(
            packageName = null,
            event = "replay_notification_drop",
            source = "notification-replay-test",
        )

        val snapshot = AndroidPushRuntime.snapshot()

        assertEquals(2L, snapshot.notificationEventCount)
        assertEquals(null, snapshot.lastPackageName)
        assertEquals("replay_notification_drop", snapshot.lastAction)
    }
}
