package io.github.magisk317.mipush.notification

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class NotificationManagerExRobolectricTest {
    private val targetPackage = "com.example.target"

    @AfterEach
    fun tearDown() {
        notificationManager().cancelAll()
    }

    @Test
    fun `legacy active notification lookup stays scoped to target marker`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = notificationManager()
        manager.cancelAll()
        NotificationManagerEx.init(context)

        manager.notify(101, notification(targetPackage, "target"))
        manager.notify(102, notification(null, "unmanaged"))
        manager.notify(103, notification("com.example.other", "other"))

        val active = NotificationManagerEx.getActiveNotifications(targetPackage).orEmpty()

        assertEquals(setOf(101), active.mapNotNull { it?.id }.toSet())
    }

    private fun notification(targetPackage: String?, title: String): Notification {
        val context = RuntimeEnvironment.getApplication()
        return Notification.Builder(context)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .apply {
                if (targetPackage != null) {
                    extras.putString("target_package", targetPackage)
                }
            }
            .build()
    }

    private fun notificationManager(): NotificationManager =
        RuntimeEnvironment.getApplication()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
