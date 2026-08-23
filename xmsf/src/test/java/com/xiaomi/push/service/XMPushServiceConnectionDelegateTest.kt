package com.xiaomi.push.service

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.xiaomi.slim.SlimConnection
import com.xiaomi.smack.Connection
import com.xiaomi.smack.ConnectionConfiguration
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

// Keep Robolectric: this test relies on Android framework implementations indirectly;
// android.jar unit-test stubs throw "Method ... not mocked" without the extension.
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class XMPushServiceConnectionDelegateTest {
    @Test
    fun `connect reuses the service owned stock slim connection`() {
        val service = mockk<XMPushServiceCore>(relaxed = true)
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val configuration = mockk<ConnectionConfiguration>(relaxed = true)
        val slimConnection = mockk<SlimConnection>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager> {
            every { activeNetwork } returns null
        }
        var currentConnection: Connection? = null
        every { service.runtimeObserver } returns observer
        every { service.connectionConfiguration } returns configuration
        every { service.slimConnection } returns slimConnection
        every { service.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { service.currentConnection } answers { currentConnection }
        every { service.currentConnection = any() } answers {
            currentConnection = firstArg()
        }
        every {
            observer.resolveConnectionAttemptPlan(
                isConnected = false,
                isConnecting = false,
            )
        } returns PushConnectionAttemptPlan(
            action = PushConnectionAttemptAction.Connect,
            eventAction = "connect_start",
        )

        XMPushServiceConnectionDelegate(service).connect()

        assertSame(slimConnection, currentConnection)
        verify(exactly = 1) { slimConnection.connect() }
        verify(exactly = 1) {
            slimConnection.addPacketListener(service.servicePacketListener, any())
        }
    }
}
