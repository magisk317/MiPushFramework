package io.github.magisk317.mipush.hook.systemui

import android.service.notification.StatusBarNotification
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiPushIslandHookContractTest {
    @Test
    fun `recognizes the SystemUI notification removal override`() {
        val hook = MiPushIslandHook()
        val removal = CallbackSamples::class.java.getDeclaredMethod(
            "onNotificationRemoved",
            StatusBarNotification::class.java,
            Int::class.javaPrimitiveType,
        )
        val unrelated = CallbackSamples::class.java.getDeclaredMethod(
            "onNotificationPosted",
            StatusBarNotification::class.java,
        )

        assertTrue(hook.isNotificationRemovedCallback(removal))
        assertFalse(hook.isNotificationRemovedCallback(unrelated))
    }

    @Suppress("UNUSED_PARAMETER")
    private class CallbackSamples {
        fun onNotificationRemoved(sbn: StatusBarNotification, reason: Int) = Unit

        fun onNotificationPosted(sbn: StatusBarNotification) = Unit
    }
}
