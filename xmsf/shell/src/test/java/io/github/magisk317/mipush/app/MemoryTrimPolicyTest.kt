package io.github.magisk317.mipush.app

import android.content.ComponentCallbacks2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Suppress("DEPRECATION")
class MemoryTrimPolicyTest {
    @Test
    fun `ui hidden and moderate running pressure release bitmap caches`() {
        assertEquals(
            MemoryTrimPolicy.Action.CLEAR_BITMAPS,
            MemoryTrimPolicy.actionFor(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN),
        )
        assertEquals(
            MemoryTrimPolicy.Action.CLEAR_BITMAPS,
            MemoryTrimPolicy.actionFor(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE),
        )
    }

    @Test
    fun `critical and background pressure release all accelerators`() {
        assertEquals(
            MemoryTrimPolicy.Action.CLEAR_ALL,
            MemoryTrimPolicy.actionFor(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL),
        )
        assertEquals(
            MemoryTrimPolicy.Action.CLEAR_ALL,
            MemoryTrimPolicy.actionFor(ComponentCallbacks2.TRIM_MEMORY_COMPLETE),
        )
    }

    @Test
    fun `unknown levels leave runtime state untouched`() {
        assertEquals(MemoryTrimPolicy.Action.NONE, MemoryTrimPolicy.actionFor(12345))
    }
}
