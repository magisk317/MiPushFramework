package com.xiaomi.xmsf.utils

import android.content.pm.ApplicationInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleLogProviderPolicyTest {
    @Test
    fun `module log caller allowlist requires system flags`() {
        assertTrue(
            ModuleLogIngressPolicy.isTrustedSystemPackage(
                "com.android.systemui",
                ApplicationInfo.FLAG_SYSTEM,
            ),
        )
        assertTrue(
            ModuleLogIngressPolicy.isTrustedSystemPackage(
                "com.miui.securitycore",
                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
            ),
        )
        // Personal Assistant installs to /product/priv-app and reports FLAG_SYSTEM without
        // FLAG_UPDATED_SYSTEM_APP on OS 4, so this mirrors the flag set the guard really sees.
        assertTrue(
            ModuleLogIngressPolicy.isTrustedSystemPackage(
                "com.miui.personalassistant",
                ApplicationInfo.FLAG_SYSTEM,
            ),
        )
        assertFalse(ModuleLogIngressPolicy.isTrustedSystemPackage("com.android.systemui", 0))
        assertFalse(
            ModuleLogIngressPolicy.isTrustedSystemPackage(
                "com.example.attacker",
                ApplicationInfo.FLAG_SYSTEM,
            ),
        )
    }

    @Test
    fun `module log route preserves allowlisted caller route and rejects unknown values`() {
        assertEquals("xmsf_hook", ModuleLogIngressPolicy.resolveRoute("xmsf_hook"))
        assertEquals("nms_hook", ModuleLogIngressPolicy.resolveRoute("nms_hook"))
        assertEquals("app", ModuleLogIngressPolicy.resolveRoute("attacker-controlled-route"))
        assertEquals("app", ModuleLogIngressPolicy.resolveRoute(null))
    }
}
