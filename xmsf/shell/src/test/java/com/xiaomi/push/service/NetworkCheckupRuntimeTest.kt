package com.xiaomi.push.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkCheckupRuntimeTest {
    @Test
    fun `shouldRunConnectivityTest requires stats hosts and interval`() {
        assertFalse(
            NetworkCheckupRuntime.shouldRunConnectivityTest(
                activeCount = 1,
                nowMs = 1_000L,
                lastCheckTimeMs = 500L,
                allowStats = true,
                testHostsCount = 2,
            ),
        )
        assertFalse(
            NetworkCheckupRuntime.shouldRunConnectivityTest(
                activeCount = 0,
                nowMs = 1_800_000L,
                lastCheckTimeMs = 0L,
                allowStats = false,
                testHostsCount = 2,
            ),
        )
        assertFalse(
            NetworkCheckupRuntime.shouldRunConnectivityTest(
                activeCount = 0,
                nowMs = 1_800_000L,
                lastCheckTimeMs = 0L,
                allowStats = true,
                testHostsCount = 0,
            ),
        )
        assertTrue(
            NetworkCheckupRuntime.shouldRunConnectivityTest(
                activeCount = 0,
                nowMs = 1_800_000L,
                lastCheckTimeMs = 0L,
                allowStats = true,
                testHostsCount = 2,
            ),
        )
    }

    @Test
    fun `extractGateway returns first ip from default route`() {
        assertEquals(
            "192.168.1.1",
            NetworkCheckupRuntime.extractGateway("default via 192.168.1.1 dev wlan0 proto dhcp src 192.168.1.8"),
        )
    }

    @Test
    fun `extractGateway ignores non default and invalid routes`() {
        assertEquals(null, NetworkCheckupRuntime.extractGateway(null))
        assertEquals(null, NetworkCheckupRuntime.extractGateway(""))
        assertEquals(null, NetworkCheckupRuntime.extractGateway("10.0.0.0/24 dev wlan0 scope link"))
        assertEquals(null, NetworkCheckupRuntime.extractGateway("default via gateway dev wlan0"))
    }
}
