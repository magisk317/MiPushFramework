package io.github.magisk317.mipush.service.runtime

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeepAliveRuntimeAdapterOwnerCallbackTest {
    @Test
    fun `owner-specific cancellation preserves another trigger pending callback`() {
        assertTrue(KeepAliveRuntimeAdapter.shouldCancelPendingAction("trigger-a", "trigger-a"))
        assertFalse(KeepAliveRuntimeAdapter.shouldCancelPendingAction("trigger-b", "trigger-a"))
        assertTrue(KeepAliveRuntimeAdapter.shouldCancelPendingAction("trigger-b", null))
    }

    @Test
    fun `foreground exit filters target callback cancellation by trigger owner`() {
        val source = File("src/main/java/io/github/magisk317/mipush/service/runtime/KeepAliveRuntimeAdapter.kt")
            .readText()
        val scheduleUnbind = source.substringAfter("private fun scheduleUnbind")
            .substringBefore("private fun attemptBind")

        assertTrue(scheduleUnbind.contains("cancelTargetCallbacks(targetPackage, ownerProcess = triggerProcess)"))
        assertTrue(scheduleUnbind.contains("if (binding.ownerProcess != triggerProcess) return"))
    }
}
