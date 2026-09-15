package com.xiaomi.xmsf.provider

import android.content.pm.ApplicationInfo
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
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
    fun `preference keys expose the full set and honor selection args`() {
        assertTrue(
            provider.preferenceKeysForCaller(selectionArgs = null).contains(ISLAND_PREF_ENABLED),
        )
        assertEquals(
            listOf(ISLAND_PREF_ENABLED),
            provider.preferenceKeysForCaller(selectionArgs = arrayOf(ISLAND_PREF_ENABLED, "not.a.key")),
        )
        assertEquals(
            listOf(ISLAND_PREF_TIMEOUT),
            provider.preferenceKeysForCaller(selectionArgs = arrayOf(ISLAND_PREF_TIMEOUT)),
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
