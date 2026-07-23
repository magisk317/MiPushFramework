package io.github.magisk317.mipush.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreferenceOwnershipTest {
    @Test
    fun `classifies manager presentation and runtime policy keys`() {
        assertEquals(PreferenceOwner.MANAGER, PreferenceOwnership.ownerOf("theme_mode"))
        assertEquals(PreferenceOwner.MANAGER, PreferenceOwnership.ownerOf("config_directory"))
        assertEquals(PreferenceOwner.RUNTIME, PreferenceOwnership.ownerOf("xmpp_server"))
        assertEquals(PreferenceOwner.RUNTIME, PreferenceOwnership.ownerOf("event_retention_days"))
        assertTrue(PreferenceOwnership.managerKeys().contains("ui_kit_style"))
        assertTrue(PreferenceOwnership.runtimeKeys().contains("show_all_events"))
        // No key is dual-owned.
        assertTrue(PreferenceOwnership.managerKeys().intersect(PreferenceOwnership.runtimeKeys()).isEmpty())
    }
}
