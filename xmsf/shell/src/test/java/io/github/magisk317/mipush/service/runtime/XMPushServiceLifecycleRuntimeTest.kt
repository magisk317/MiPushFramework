package io.github.magisk317.mipush.service.runtime

import com.xiaomi.push.service.PushClientsManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class XMPushServiceLifecycleRuntimeTest {
    @Test
    fun `product listener appends channel tracking without replacing stock listeners`() {
        val manager = mockk<PushClientsManager>(relaxed = true)
        val listener = slot<PushClientsManager.ClientChangeListener>()
        val syncSources = mutableListOf<String>()
        every { manager.addClientChangeListener(capture(listener)) } just Runs
        val runtime = XMPushServiceLifecycleRuntime(syncSources::add)

        runtime.configureClientChangeListener(manager)
        listener.captured.onChange()

        verify(exactly = 0) { manager.removeAllClientChangeListeners() }
        assertEquals(
            listOf(
                "XMPushServiceLifecycleRuntime.configureClientChangeListener",
                "XMPushServiceLifecycleRuntime.ClientChangeListener",
            ),
            syncSources,
        )
    }

    @Test
    fun `close removes only the product listener and blocks late callbacks`() {
        val manager = mockk<PushClientsManager>(relaxed = true)
        val listener = slot<PushClientsManager.ClientChangeListener>()
        val syncSources = mutableListOf<String>()
        every { manager.addClientChangeListener(capture(listener)) } just Runs
        val runtime = XMPushServiceLifecycleRuntime(syncSources::add)
        runtime.configureClientChangeListener(manager)

        runtime.close()
        listener.captured.onChange()

        verify(exactly = 1) { manager.removeClientChangeListener(listener.captured) }
        assertEquals(
            listOf("XMPushServiceLifecycleRuntime.configureClientChangeListener"),
            syncSources,
        )
    }
}
