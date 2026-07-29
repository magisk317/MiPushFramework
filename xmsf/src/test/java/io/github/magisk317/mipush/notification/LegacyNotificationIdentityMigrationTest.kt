package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacyNotificationIdentityMigrationTest {
    @Test
    fun `migration matches only old mipush tags`() {
        assertTrue(LegacyNotificationIdentityMigration.isLegacyIdentity("mipush_com.example.app"))
        assertTrue(LegacyNotificationIdentityMigration.isLegacyIdentity("mipush_mock_replay_receipt:pkg"))
        assertFalse(LegacyNotificationIdentityMigration.isLegacyIdentity(null))
        assertFalse(LegacyNotificationIdentityMigration.isLegacyIdentity("native"))
        assertFalse(LegacyNotificationIdentityMigration.isLegacyIdentity("mi_push_pkg"))
    }
}
