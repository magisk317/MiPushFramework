package io.github.magisk317.mipush.hook.securitycore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecurityCoreXSpaceMiPushPolicyTest {

    @Test
    fun `keeps original true decision untouched`() {
        val decision = SecurityCoreXSpaceMiPushPolicy.decide("com.taobao.idlefish", originalRequired = true)

        assertFalse(decision.forceRequired)
        assertEquals("already_required", decision.reason)
    }

    @Test
    fun `forces non system packages to retain xmsf in xspace`() {
        val decision = SecurityCoreXSpaceMiPushPolicy.decide("com.example.app", originalRequired = false)

        assertTrue(decision.forceRequired)
        assertEquals("force_xspace_xmsf_retention", decision.reason)
    }

    @Test
    fun `does not force blank or core packages`() {
        assertFalse(SecurityCoreXSpaceMiPushPolicy.decide("", originalRequired = false).forceRequired)
        assertFalse(SecurityCoreXSpaceMiPushPolicy.decide("com.miui.securitycore", originalRequired = false).forceRequired)
        assertFalse(SecurityCoreXSpaceMiPushPolicy.decide("com.xiaomi.xmsf", originalRequired = false).forceRequired)
    }
}
