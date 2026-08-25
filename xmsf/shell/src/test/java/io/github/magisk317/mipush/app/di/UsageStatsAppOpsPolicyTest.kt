package io.github.magisk317.mipush.app.di

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UsageStatsAppOpsPolicyTest {
    @Test
    fun `allow foreground and default app op modes are accepted`() {
        assertTrue(isUsageStatsAppOpAllowed("GET_USAGE_STATS: allow"))
        assertTrue(isUsageStatsAppOpAllowed("GET_USAGE_STATS: foreground"))
        assertTrue(isUsageStatsAppOpAllowed("android:get_usage_stats: default"))
    }

    @Test
    fun `denied or unrelated app op output is rejected`() {
        assertFalse(isUsageStatsAppOpAllowed("GET_USAGE_STATS: deny"))
        assertFalse(isUsageStatsAppOpAllowed("GET_NOTIFICATIONS: allow"))
        assertFalse(isUsageStatsAppOpAllowed("permission GET_USAGE_STATS is unavailable"))
    }
}
