package com.xiaomi.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostManagerRuntimeTest {

    @Test
    fun `refresh targets skip hosts that already have usable fallback`() {
        val plan = HostManagerRuntime.planRefreshTargets(
            allHosts = listOf("a", "b", "c"),
            hostsWithFallback = setOf("b")
        )

        assertEquals(listOf("a", "c"), plan.targetHosts)
    }

    @Test
    fun `remote fallback request is throttled inside backoff window`() {
        val plan = HostManagerRuntime.planRemoteFallbackRequest(
            nowMs = 120_000L,
            lastRequestTimestampMs = 100_000L,
            failureCount = 1
        )

        assertFalse(plan.shouldRequest)
        assertEquals("gslb_request_throttled", plan.eventAction)
    }

    @Test
    fun `remote fallback request is allowed after backoff window`() {
        val plan = HostManagerRuntime.planRemoteFallbackRequest(
            nowMs = 200_001L,
            lastRequestTimestampMs = 100_000L,
            failureCount = 1
        )

        assertTrue(plan.shouldRequest)
        assertEquals("gslb_request_allowed", plan.eventAction)
    }

    @Test
    fun `request urls prefer local fallback urls when present`() {
        val plan = HostManagerRuntime.planRequestUrls(
            defaultUrl = "https://resolver.msg.xiaomi.net/gslb/?ver=4.0",
            localFallbackUrls = listOf("https://fallback-1/gslb/?ver=4.0"),
            reservedHosts = listOf("reserved-1")
        )

        assertEquals(listOf("https://fallback-1/gslb/?ver=4.0"), plan.urls)
    }
}
