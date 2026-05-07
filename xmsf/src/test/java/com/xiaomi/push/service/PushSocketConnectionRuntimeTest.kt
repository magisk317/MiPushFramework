package com.xiaomi.push.service

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushSocketConnectionRuntimeTest {

    @Test
    fun `candidate hosts prefer fallback list when present`() {
        val plan = PushSocketConnectionRuntime.resolveCandidateHosts(
            requestedHost = "resolver1",
            fallbackHosts = listOf("resolver2", "resolver3")
        )

        assertEquals(listOf("resolver2", "resolver3"), plan.candidateHosts)
        assertEquals("socket_connect_fallback_hosts", plan.eventAction)
    }

    @Test
    fun `candidate hosts fall back to requested host when no bucket hosts exist`() {
        val plan = PushSocketConnectionRuntime.resolveCandidateHosts(
            requestedHost = "resolver1",
            fallbackHosts = emptyList()
        )

        assertEquals(listOf("resolver1"), plan.candidateHosts)
        assertEquals("socket_connect_direct_host", plan.eventAction)
    }

    @Test
    fun `failure retry stops when network point changes`() {
        val plan = PushSocketConnectionRuntime.planFailureRetry(
            initialConnPoint = "wifi",
            currentConnPoint = "mobile"
        )

        assertFalse(plan.shouldContinue)
        assertEquals("socket_connect_abort_network_changed", plan.eventAction)
    }

    @Test
    fun `failure retry continues on same network point`() {
        val plan = PushSocketConnectionRuntime.planFailureRetry(
            initialConnPoint = "wifi",
            currentConnPoint = "wifi"
        )

        assertTrue(plan.shouldContinue)
        assertEquals("socket_connect_retry_next_host", plan.eventAction)
    }

    @Test
    fun `derive connection key matches legacy algorithm`() {
        val challenge = "1234567890abcdef"
        val deviceUuid = "abcdef1234567890"

        val derived = PushSocketConnectionRuntime.deriveConnectionKey(challenge, deviceUuid)
        val expected = RC4Cryption.encrypt(
            challenge.toByteArray(),
            (challenge.substring(challenge.length / 2) + deviceUuid.substring(deviceUuid.length / 2)).toByteArray()
        )

        assertArrayEquals(expected, derived)
    }

    @Test
    fun `derive connection key returns null when challenge missing`() {
        assertNull(PushSocketConnectionRuntime.deriveConnectionKey(null, "uuid"))
        assertNull(PushSocketConnectionRuntime.deriveConnectionKey("", "uuid"))
    }

    @Test
    fun `short connection window resets after threshold`() {
        val plan = PushSocketConnectionRuntime.evaluateShortConnection(
            nowElapsedMs = 400_000L,
            lastConnectedTime = 0L,
            hasNetwork = true,
            curShortConnCount = 1
        )

        assertEquals(0, plan.nextShortConnCount)
        assertFalse(plan.shouldSinkDown)
        assertEquals("socket_short_conn_window_reset", plan.eventAction)
    }

    @Test
    fun `short connection increments until sinkdown threshold`() {
        val firstPlan = PushSocketConnectionRuntime.evaluateShortConnection(
            nowElapsedMs = 200_000L,
            lastConnectedTime = 0L,
            hasNetwork = true,
            curShortConnCount = 0
        )
        val secondPlan = PushSocketConnectionRuntime.evaluateShortConnection(
            nowElapsedMs = 200_000L,
            lastConnectedTime = 0L,
            hasNetwork = true,
            curShortConnCount = 1
        )

        assertEquals(1, firstPlan.nextShortConnCount)
        assertFalse(firstPlan.shouldSinkDown)
        assertEquals("socket_short_conn_retry", firstPlan.eventAction)
        assertEquals(0, secondPlan.nextShortConnCount)
        assertTrue(secondPlan.shouldSinkDown)
        assertEquals("socket_sinkdown_host", secondPlan.eventAction)
    }

    @Test
    fun `short connection does nothing without network`() {
        val plan = PushSocketConnectionRuntime.evaluateShortConnection(
            nowElapsedMs = 200_000L,
            lastConnectedTime = 0L,
            hasNetwork = false,
            curShortConnCount = 1
        )

        assertEquals(1, plan.nextShortConnCount)
        assertFalse(plan.shouldSinkDown)
        assertEquals("socket_short_conn_no_network", plan.eventAction)
    }
}
