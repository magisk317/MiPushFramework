package io.github.magisk317.mipush.data

import io.github.magisk317.mipush.common.ENABLE_ANALYTICS_KEY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreferenceOwnershipTest {
    @Test
    fun `classifies manager presentation and runtime policy keys`() {
        assertEquals(PreferenceOwner.MANAGER, PreferenceOwnership.ownerOf("theme_mode"))
        assertEquals(PreferenceOwner.MANAGER, PreferenceOwnership.ownerOf("config_directory"))
        assertEquals(PreferenceOwner.RUNTIME, PreferenceOwnership.ownerOf("xmpp_server"))
        assertEquals(PreferenceOwner.RUNTIME, PreferenceOwnership.ownerOf("event_retention_days"))
        assertEquals(PreferenceOwner.RUNTIME, PreferenceOwnership.ownerOf(ENABLE_ANALYTICS_KEY))
        assertTrue(PreferenceOwnership.managerKeys().contains("ui_kit_style"))
        assertTrue(PreferenceOwnership.runtimeKeys().contains("show_all_events"))
        // No key is dual-owned.
        assertTrue(PreferenceOwnership.managerKeys().intersect(PreferenceOwnership.runtimeKeys()).isEmpty())
    }

    @Test
    fun `every runtime preference has a typed default`() {
        val runtimeEntries = PreferenceOwnership.entries.filter { it.owner == PreferenceOwner.RUNTIME }

        assertEquals(PreferenceOwnership.runtimeKeys().size, runtimeEntries.size)
        runtimeEntries.forEach { entry ->
            assertNotNull(entry.defaultValue, entry.key)
        }
        // Analytics is opt-in since e5d880249: release default off, while debug builds still
        // force reporting on, so only the persisted default flipped.
        assertEquals("false", PreferenceOwnership.byKey.getValue(ENABLE_ANALYTICS_KEY).defaultValue?.value)
        assertEquals("2", PreferenceOwnership.byKey.getValue("runtime_log_retention_days").defaultValue?.value)
        assertEquals("7", PreferenceOwnership.byKey.getValue("event_retention_days").defaultValue?.value)
    }

    @Test
    fun `ownership entries use each key exactly once`() {
        assertEquals(
            PreferenceOwnership.entries.map { it.key }.toSet().size,
            PreferenceOwnership.entries.size,
        )
    }
}
