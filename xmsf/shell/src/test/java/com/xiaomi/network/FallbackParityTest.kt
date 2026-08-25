package com.xiaomi.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import com.xiaomi.channel.commonutils.network.Network
import java.io.File
import java.io.IOException

class FallbackParityTest {
    @Test
    fun `failed access history changes weighted host ordering`() {
        val fallback = Fallback("primary.example")
        fallback.addHost(WeightedHost("backup.example", 0))
        assertEquals("backup.example", fallback.getHosts(true).first())

        fallback.failedHost("backup.example", 10L, 0L, IOException("first"))
        fallback.failedHost("backup.example", 10L, 0L, IOException("second"))

        assertEquals("primary.example", fallback.getHosts(true).first())
    }

    @Test
    fun `missing local bucket returns ineffective refresh proxy`() {
        mockkStatic(Network::class)
        every { Network.hasNetwork(any()) } returns false

        val context = mockk<Context> {
            every { packageName } returns "com.xiaomi.network.test"
            every { filesDir } returns File(System.getProperty("java.io.tmpdir"))
        }
        val fallback = HostManager(context).getFallbacksByHost("missing.example", false)

        requireNotNull(fallback)
        assertFalse(fallback.isEffective())
        assertEquals("missing.example", fallback.getHosts(true).last())
    }
}
