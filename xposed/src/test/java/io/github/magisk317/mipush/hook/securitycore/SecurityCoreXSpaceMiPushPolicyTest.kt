package io.github.magisk317.mipush.hook.securitycore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecurityCoreXSpaceMiPushPolicyTest {

    @Test
    fun `keeps original true decision untouched`() {
        val decision = SecurityCoreXSpaceMiPushPolicy.decide(
            "com.taobao.idlefish",
            originalRequired = true,
            dualAppEnabled = true,
        )

        assertFalse(decision.forceRequired)
        assertEquals("already_required", decision.reason)
    }

    @Test
    fun `forces mipush module package only when dual app enabled`() {
        val decision = SecurityCoreXSpaceMiPushPolicy.decide(
            "io.github.magisk317.mipush",
            originalRequired = false,
            dualAppEnabled = true,
        )

        assertTrue(decision.forceRequired)
        assertEquals("force_xspace_xmsf_retention", decision.reason)
    }

    @Test
    fun `does not force mipush module when dual app disabled`() {
        val decision = SecurityCoreXSpaceMiPushPolicy.decide(
            "io.github.magisk317.mipush",
            originalRequired = false,
            dualAppEnabled = false,
        )

        assertFalse(decision.forceRequired)
        assertEquals("dual_app_disabled", decision.reason)
    }

    @Test
    fun `does not force unrelated xspace packages`() {
        val decision = SecurityCoreXSpaceMiPushPolicy.decide(
            "com.example.app",
            originalRequired = false,
            dualAppEnabled = true,
        )

        assertFalse(decision.forceRequired)
        assertEquals("not_mipush_module", decision.reason)
    }

    @Test
    fun `does not force blank or core packages`() {
        assertFalse(
            SecurityCoreXSpaceMiPushPolicy.decide("", originalRequired = false, dualAppEnabled = true)
                .forceRequired,
        )
        assertFalse(
            SecurityCoreXSpaceMiPushPolicy.decide(
                "com.miui.securitycore",
                originalRequired = false,
                dualAppEnabled = true,
            ).forceRequired,
        )
        assertFalse(
            SecurityCoreXSpaceMiPushPolicy.decide(
                "com.xiaomi.xmsf",
                originalRequired = false,
                dualAppEnabled = true,
            ).forceRequired,
        )
    }
}
