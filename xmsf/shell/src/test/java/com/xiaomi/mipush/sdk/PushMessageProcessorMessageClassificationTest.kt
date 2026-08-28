package com.xiaomi.mipush.sdk

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushMessageProcessorMessageClassificationTest {
    @Test
    fun `hybrid classification recognizes both stock deferred acknowledgement actions`() {
        assertTrue(
            PushMessageProcessorMessageClassification.isHybridMessage(
                mapOf(Constants.EXTRA_KEY_PUSH_SERVER_ACTION to Constants.EXTRA_VALUE_HYBRID_MESSAGE),
            ),
        )
        assertTrue(
            PushMessageProcessorMessageClassification.isHybridMessage(
                mapOf(Constants.EXTRA_KEY_PUSH_SERVER_ACTION to Constants.EXTRA_VALUE_PLATFORM_MESSAGE),
            ),
        )
    }

    @Test
    fun `hybrid classification rejects absent and unrelated server actions`() {
        assertFalse(PushMessageProcessorMessageClassification.isHybridMessage(null))
        assertFalse(PushMessageProcessorMessageClassification.isHybridMessage(emptyMap()))
        assertFalse(
            PushMessageProcessorMessageClassification.isHybridMessage(
                mapOf(Constants.EXTRA_KEY_PUSH_SERVER_ACTION to "ordinary_message"),
            ),
        )
    }
}
