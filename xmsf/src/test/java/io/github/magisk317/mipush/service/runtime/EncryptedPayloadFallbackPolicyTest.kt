package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.mipush.common.notification.MockReplayOutcome
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EncryptedPayloadFallbackPolicyTest {
    @Test
    fun `failed encrypted notification falls back to raw app dispatch`() {
        assertTrue(
            MyMIPushNotificationHelper.shouldFallbackToRawEncryptedDispatch(
                isEncrypted = true,
                outcome = MockReplayOutcome.Failed,
            ),
        )
    }

    @Test
    fun `successful or policy-blocked outcomes do not dispatch a second payload`() {
        listOf(
            MockReplayOutcome.Posted,
            MockReplayOutcome.Dispatched,
            MockReplayOutcome.BlockedByPermission,
        ).forEach { outcome ->
            assertFalse(
                MyMIPushNotificationHelper.shouldFallbackToRawEncryptedDispatch(
                    isEncrypted = true,
                    outcome = outcome,
                ),
            )
        }
        assertFalse(
            MyMIPushNotificationHelper.shouldFallbackToRawEncryptedDispatch(
                isEncrypted = false,
                outcome = MockReplayOutcome.Failed,
            ),
        )
    }
}
