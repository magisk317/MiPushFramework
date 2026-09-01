package io.github.magisk317.mipush.runtime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushHostPlanFactoryTest {
    @Test
    fun `gslb fields retain stock order and values`() {
        val plan = PushHostPlanFactory.planGslbRequest(41, 37, "model", "incremental", 2)

        assertEquals(listOf("sdkver", "osver", "os", "mi"), plan.queryFields.map { it.name })
        assertEquals(listOf("41", "37", "model:incremental", "2"), plan.queryFields.map { it.value })
        assertEquals(80, plan.defaultStatsPort)
    }

    @Test
    fun `bucket fetch preserves strict throttle boundary`() {
        assertFalse(PushHostPlanFactory.decideBucketFetch(false, 0L, 100L, 10L).shouldRefresh)
        assertFalse(PushHostPlanFactory.decideBucketFetch(true, 0L, 10L, 10L).shouldRefresh)
        assertTrue(PushHostPlanFactory.decideBucketFetch(true, 0L, 11L, 10L).shouldRefresh)
    }

    @Test
    fun `bucket reconnect ignores blanks and reconnects only for changed hosts`() {
        val unchanged = PushHostPlanFactory.decideBucketReconnect(true, "a", listOf(" ", " a "))
        val changed = PushHostPlanFactory.decideBucketReconnect(true, "a", listOf("b"))

        assertFalse(unchanged.shouldReconnect)
        assertTrue(changed.shouldReconnect)
        assertEquals("bucket_changed", changed.connectionStateReason)
    }

    @Test
    fun `refresh targets retain host order and exclude existing fallbacks`() {
        val plan = PushHostPlanFactory.planRefreshTargets(
            allHosts = listOf("a", "b", "c"),
            hostsWithFallback = setOf("b"),
        )

        assertEquals(listOf("a", "c"), plan.targetHosts)
    }

    @Test
    fun `remote request throttle preserves inclusive failure window`() {
        val throttled = PushHostPlanFactory.planRemoteFallbackRequest(120_000L, 60_000L, 1L)
        val allowed = PushHostPlanFactory.planRemoteFallbackRequest(120_001L, 60_000L, 1L)

        assertFalse(throttled.shouldRequest)
        assertEquals(60_000L, throttled.nextTimestampMs)
        assertTrue(allowed.shouldRequest)
        assertEquals(120_001L, allowed.nextTimestampMs)
    }

    @Test
    fun `request urls prefer local fallback and otherwise include distinct reserved hosts`() {
        val local = PushHostPlanFactory.planRequestUrls(
            defaultUrl = "http://resolver.msg.xiaomi.net/gslb",
            localFallbackUrls = listOf("local", "local"),
            reservedHosts = listOf("r1"),
        )
        val generated = PushHostPlanFactory.planRequestUrls(
            defaultUrl = "http://resolver.msg.xiaomi.net/gslb",
            localFallbackUrls = emptyList(),
            reservedHosts = listOf("r1", "r1"),
        )

        assertEquals(listOf("local"), local.urls)
        assertEquals(
            listOf("http://resolver.msg.xiaomi.net/gslb", "http://r1/gslb"),
            generated.urls,
        )
    }
}
