package io.github.magisk317.mipush.bridge

import com.xiaomi.push.service.XMPushServiceCore
import com.xiaomi.smack.Connection
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class MiPushRuntimeObserverStateTest {
    @Test
    fun `replacing service drops active connection from previous binding`() {
        val state = MiPushRuntimeObserverState()
        val firstService = mockk<XMPushServiceCore>(relaxed = true)
        val secondService = mockk<XMPushServiceCore>(relaxed = true)
        val connection = mockk<Connection>(relaxed = true)

        state.replaceService(firstService)
        state.setActiveConnection(connection)
        state.replaceService(secondService)

        assertSame(secondService, state.service())
        assertNull(state.activeServiceFor(connection))
    }

    @Test
    fun `clearing service drops binding and active connection`() {
        val state = MiPushRuntimeObserverState()
        val service = mockk<XMPushServiceCore>(relaxed = true)
        val connection = mockk<Connection>(relaxed = true)

        state.replaceService(service)
        state.setActiveConnection(connection)
        state.clearService()

        assertNull(state.service())
        assertNull(state.activeServiceFor(connection))
    }

    @Test
    fun `releasing stale connection does not clear current connection`() {
        val state = MiPushRuntimeObserverState()
        val service = mockk<XMPushServiceCore>(relaxed = true)
        val activeConnection = mockk<Connection>(relaxed = true)
        val staleConnection = mockk<Connection>(relaxed = true)
        var currentConnection: Connection? = activeConnection
        every { service.currentConnection } answers { currentConnection }
        every { service.slimConnection } returns mockk(relaxed = true)
        every { service.clearCurrentConnection() } answers { currentConnection = null }

        state.replaceService(service)
        state.setActiveConnection(activeConnection)
        state.releaseConnection(staleConnection)

        assertSame(service, state.activeServiceFor(activeConnection))
        verify(exactly = 0) { service.clearCurrentConnection() }

        state.releaseConnection(activeConnection)

        assertNull(state.activeServiceFor(activeConnection))
        verify(exactly = 1) { service.clearCurrentConnection() }
    }
}
