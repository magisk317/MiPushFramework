package com.xiaomi.push.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushHostRuntimeTest {

    @Test
    fun `build gslb request appends runtime query parameters`() {
        val request = PushHostRuntime.buildGslbRequest(
            baseUrl = "https://resolver.msg.xiaomi.net/gslb/?ver=4.0",
            sdkVersion = 41,
            droidVersion = 35,
            model = "Pixel 9",
            incremental = "AP4A",
            miuiType = 3
        )

        assertTrue(request.requestUrl.contains("sdkver=41"))
        assertTrue(request.requestUrl.contains("osver=35"))
        assertTrue(request.requestUrl.contains("mi=3"))
        assertEquals("resolver.msg.xiaomi.net:80", request.statsHostPort)
    }

    @Test
    fun `bucket fetch is throttled inside min window`() {
        val plan = PushHostRuntime.decideBucketFetch(
            fetchBucketRequested = true,
            lastFetchTimeMs = 10_000L,
            nowMs = 20_000L,
            minBucketFetchDurationMs = 60_000L
        )

        assertFalse(plan.shouldRefresh)
        assertEquals("gslb_fetch_throttled", plan.eventAction)
    }

    @Test
    fun `bucket fetch refreshes when requested after window`() {
        val plan = PushHostRuntime.decideBucketFetch(
            fetchBucketRequested = true,
            lastFetchTimeMs = 10_000L,
            nowMs = 80_001L,
            minBucketFetchDurationMs = 60_000L
        )

        assertTrue(plan.shouldRefresh)
        assertEquals("gslb_fetch_refresh", plan.eventAction)
    }

    @Test
    fun `bucket reconnect skipped when current host still present`() {
        val plan = PushHostRuntime.decideBucketReconnect(
            currentHost = "resolver1",
            candidateHosts = listOf("resolver1", "resolver2")
        )

        assertFalse(plan.shouldReconnect)
        assertEquals("gslb_hosts_unchanged", plan.eventAction)
    }

    @Test
    fun `bucket reconnect requested when current host disappears`() {
        val plan = PushHostRuntime.decideBucketReconnect(
            currentHost = "resolver1",
            candidateHosts = listOf("resolver2", "resolver3")
        )

        assertTrue(plan.shouldReconnect)
        assertEquals("gslb_hosts_changed", plan.eventAction)
        assertEquals("bucket_changed", plan.connectionStateReason)
    }

    @Test
    fun `bucket reconnect is skipped when there is no active connection`() {
        val plan = PushHostRuntime.decideBucketReconnect(
            hasConnection = false,
            currentHost = "resolver1",
            candidateHosts = listOf("resolver2")
        )

        assertFalse(plan.shouldReconnect)
        assertEquals("gslb_refresh_no_connection", plan.eventAction)
    }
}
