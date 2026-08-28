package io.github.magisk317.mipush.runtime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushSocketConnectionPlanFactoryTest {
    @Test
    fun `fallback hosts are trimmed and empty entries are ignored`() {
        val plan = PushSocketConnectionPlanFactory.resolveCandidateHosts(
            requestedHost = "resolver1",
            fallbackHosts = listOf(" resolver2 ", "", "  ", "resolver3"),
        )

        assertEquals(listOf("resolver2", "resolver3"), plan.candidateHosts)
        assertEquals("socket_connect_fallback_hosts", plan.eventAction)
    }

    @Test
    fun `requested host is retained verbatim when fallback list is empty`() {
        val plan = PushSocketConnectionPlanFactory.resolveCandidateHosts(
            requestedHost = " resolver1 ",
            fallbackHosts = listOf("", "  "),
        )

        assertEquals(listOf(" resolver1 "), plan.candidateHosts)
        assertEquals("socket_connect_direct_host", plan.eventAction)
    }

    @Test
    fun `retry compares nullable network points by value`() {
        val sameNullPoint = PushSocketConnectionPlanFactory.planFailureRetry(
            initialConnPoint = null,
            currentConnPoint = null,
        )
        val changedPoint = PushSocketConnectionPlanFactory.planFailureRetry(
            initialConnPoint = "wifi",
            currentConnPoint = null,
        )

        assertTrue(sameNullPoint.shouldContinue)
        assertEquals("socket_connect_retry_next_host", sameNullPoint.eventAction)
        assertFalse(changedPoint.shouldContinue)
        assertEquals("socket_connect_abort_network_changed", changedPoint.eventAction)
    }

    @Test
    fun `short connection resets exactly at window threshold`() {
        val plan = PushSocketConnectionPlanFactory.evaluateShortConnection(
            nowElapsedMs = 300_000L,
            lastConnectedTime = 0L,
            hasNetwork = true,
            curShortConnCount = 1,
        )

        assertEquals(0, plan.nextShortConnCount)
        assertFalse(plan.shouldSinkDown)
        assertEquals("socket_short_conn_window_reset", plan.eventAction)
    }

    @Test
    fun `short connection preserves count while network is unavailable`() {
        val plan = PushSocketConnectionPlanFactory.evaluateShortConnection(
            nowElapsedMs = 10L,
            lastConnectedTime = 0L,
            hasNetwork = false,
            curShortConnCount = 1,
        )

        assertEquals(1, plan.nextShortConnCount)
        assertFalse(plan.shouldSinkDown)
        assertEquals("socket_short_conn_no_network", plan.eventAction)
    }

    @Test
    fun `short connection sinks host at configured count`() {
        val retry = PushSocketConnectionPlanFactory.evaluateShortConnection(
            nowElapsedMs = 10L,
            lastConnectedTime = 0L,
            hasNetwork = true,
            curShortConnCount = 0,
            maxShortConnCount = 2,
        )
        val sink = PushSocketConnectionPlanFactory.evaluateShortConnection(
            nowElapsedMs = 10L,
            lastConnectedTime = 0L,
            hasNetwork = true,
            curShortConnCount = 1,
            maxShortConnCount = 2,
        )

        assertEquals(1, retry.nextShortConnCount)
        assertFalse(retry.shouldSinkDown)
        assertEquals("socket_short_conn_retry", retry.eventAction)
        assertEquals(0, sink.nextShortConnCount)
        assertTrue(sink.shouldSinkDown)
        assertEquals("socket_sinkdown_host", sink.eventAction)
    }
}
