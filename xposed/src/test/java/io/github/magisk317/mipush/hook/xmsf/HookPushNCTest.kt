package io.github.magisk317.mipush.hook.xmsf

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HookPushNCTest {
    @Test
    fun `ready retry covers delayed system server startup`() {
        assertTrue(HookPushNC.shouldRetrySystemHookReady(0))
        assertTrue(HookPushNC.shouldRetrySystemHookReady(119))
        assertFalse(HookPushNC.shouldRetrySystemHookReady(120))
    }
}
