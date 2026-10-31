package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationCountLimitPolicyTest {
    private val coordinatorFrame =
        "com.android.systemui.statusbar.notification.collection.coordinator." +
            "CountLimitCoordinator\$\$ExternalSyntheticLambda0"

    @Test
    fun `coordinator-initiated dismissal is detected through synthetic lambda frames`() {
        val stack = listOf(
            "com.android.systemui.statusbar.notification.collection.NotifCollection",
            coordinatorFrame,
            "android.os.Handler",
        )
        assertTrue(NotificationCountLimitPolicy.isCoordinatorInitiated(stack))
    }

    @Test
    fun `user-initiated dismissal paths are not flagged as coordinator calls`() {
        listOf(
            "com.android.systemui.statusbar.notification.MiuiNotificationListener",
            "com.android.systemui.statusbar.policy.DismissNotificationHelper",
            "com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataManagerImpl",
        ).forEach { caller ->
            val stack = listOf(
                "com.android.systemui.statusbar.notification.collection.NotifCollection",
                caller,
                "android.os.Handler",
            )
            assertFalse(NotificationCountLimitPolicy.isCoordinatorInitiated(stack), caller)
        }
    }

    @Test
    fun `look-alike package names do not satisfy the caller prefix check`() {
        val stack = listOf(
            "com.android.systemui.statusbar.notification.collection.coordinator." +
                "CountLimitCoordinatorImpostor",
        )
        assertFalse(NotificationCountLimitPolicy.isCoordinatorInitiated(stack))
    }

    @Test
    fun `empty target batch never suppresses the stock dismissal`() {
        val exempt = NotificationCountLimitPolicy.isAllTargetsExempt(emptyList()) { true }
        assertFalse(exempt)
    }

    @Test
    fun `mixed batch keeps the stock eviction`() {
        val targets = listOf(
            CountLimitTarget("com.example.exempt", 0),
            CountLimitTarget("com.example.stock", 0),
        )
        val suppressed = NotificationCountLimitPolicy.isAllTargetsExempt(targets) {
            it.packageName == "com.example.exempt"
        }
        assertFalse(suppressed)
    }

    @Test
    fun `batch of one exempt victim per package and user is suppressed`() {
        val targets = listOf(
            CountLimitTarget("com.example.exempt", 0),
            CountLimitTarget("com.example.exempt", 999),
        )
        val suppressed = NotificationCountLimitPolicy.isAllTargetsExempt(targets) {
            it.packageName == "com.example.exempt"
        }
        assertTrue(suppressed)
    }

    @Test
    fun `target data carries the normalized user id`() {
        val target = CountLimitTarget(packageName = "com.example", userId = 10)
        assertEquals(10, target.userId)
    }
}
