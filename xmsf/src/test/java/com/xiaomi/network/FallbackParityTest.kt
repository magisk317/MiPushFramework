package com.xiaomi.network

import android.app.Application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.IOException

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
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
        val context: Application = RuntimeEnvironment.getApplication()
        val fallback = HostManager(context).getFallbacksByHost("missing.example", false)

        requireNotNull(fallback)
        assertFalse(fallback.isEffective())
        assertEquals("missing.example", fallback.getHosts(true).last())
    }
}
