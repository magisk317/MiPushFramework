package com.xiaomi.xmsf.provider

import android.content.pm.ApplicationInfo
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IslandPreferenceProviderPolicyTest {
    private val provider = IslandPreferenceProvider()

    @Test
    fun `system ui caller requires both exact package and system flags`() {
        assertTrue(
            provider.isTrustedSystemUiPackage(
                "com.android.systemui",
                ApplicationInfo.FLAG_SYSTEM,
            ),
        )
        assertTrue(
            provider.isTrustedSystemUiPackage(
                "com.android.systemui",
                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
            ),
        )
        assertFalse(provider.isTrustedSystemUiPackage("com.android.systemui", 0))
        assertFalse(
            provider.isTrustedSystemUiPackage(
                "com.example.systemui",
                ApplicationInfo.FLAG_SYSTEM,
            ),
        )
    }

    @Test
    fun `AMap caller is exact but does not need to be a system package`() {
        assertTrue(provider.isTrustedAmapPackage("com.autonavi.minimap", 0))
        assertFalse(provider.isTrustedAmapPackage("com.example.navigation", 0))
    }

    @Test
    fun `AMap can read only the global focus bypass flag`() {
        assertEquals(
            listOf(ISLAND_PREF_FOCUS_NOTIF),
            provider.preferenceKeysForCaller(
                focusBypassOnly = true,
                selectionArgs = arrayOf(ISLAND_PREF_ENABLED, ISLAND_PREF_FOCUS_NOTIF),
            ),
        )
        assertEquals(
            listOf(ISLAND_PREF_FOCUS_NOTIF),
            provider.preferenceKeysForCaller(
                focusBypassOnly = true,
                selectionArgs = null,
            ),
        )
        assertEquals(
            emptyList<String>(),
            provider.preferenceKeysForCaller(
                focusBypassOnly = true,
                selectionArgs = arrayOf(ISLAND_PREF_ENABLED),
            ),
        )
    }

    @Test
    fun `package scoped reads require an explicit Android user`() {
        assertTrue(provider.requiresExplicitUserScope("com.example.app", null))
        assertFalse(provider.requiresExplicitUserScope("com.example.app", 0))
        assertFalse(provider.requiresExplicitUserScope(null, null))
        assertFalse(provider.requiresExplicitUserScope("", null))
    }
}
