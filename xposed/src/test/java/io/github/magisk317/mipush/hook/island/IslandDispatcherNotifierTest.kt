package io.github.magisk317.mipush.hook.island

import android.app.NotificationManager
import android.content.Context
import io.github.magisk317.mipush.common.ISLAND_PREF_READ_PERMISSION
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class IslandDispatcherNotifierTest {
    @Test
    fun `auto cancel timeout follows island timeout with a short grace period`() {
        assertEquals(6_000L, IslandDispatcherNotifier.autoCancelAfterMillis(5))
        assertEquals(11_000L, IslandDispatcherNotifier.autoCancelAfterMillis(10))
    }

    @Test
    fun `auto cancel timeout falls back when island timeout is invalid`() {
        assertEquals(6_000L, IslandDispatcherNotifier.autoCancelAfterMillis(0))
        assertEquals(6_000L, IslandDispatcherNotifier.autoCancelAfterMillis(-1))
    }

    @Test
    fun `hidden shade option still posts the focus proxy for island rendering`() {
        val context = RuntimeEnvironment.getApplication()
        val notificationId = 0x4915
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)

        IslandDispatcher.post(
            context,
            IslandRequest(
                title = "Island title",
                content = "Island content",
                notificationId = notificationId,
                showNotification = false,
            ),
        )

        val posted = manager.activeNotifications.singleOrNull { it.id == notificationId }
        assertNotNull(posted)
        assertFalse(posted!!.notification.extras.getString(IslandDispatchContract.FOCUS_PARAM).isNullOrBlank())
        assertTrue(notificationId in IslandDispatcher.postedIdsForTest())
    }

    @Test
    fun `dispatcher receiver requires the xmsf signature sender permission`() {
        assertEquals(ISLAND_PREF_READ_PERMISSION, IslandDispatcherReceiver.REQUIRED_SENDER_PERMISSION)
    }
}
