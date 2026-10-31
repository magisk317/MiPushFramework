package io.github.magisk317.mipush.data

import io.github.magisk317.mipush.common.MONOCHROME_SHADE_ICON_KEY
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class PreferenceOwnershipTest {

    @Test
    fun allEntryKeysAreUnique() {
        val keys = PreferenceOwnership.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun byKeyMapMatchesEntriesList() {
        assertEquals(PreferenceOwnership.entries.size, PreferenceOwnership.byKey.size)
    }

    @Test
    fun everyEntryHasNonBlankDescription() {
        PreferenceOwnership.entries.forEach { entry ->
            assertTrue(entry.description.isNotBlank(), "Blank description for '${entry.key}'")
        }
    }

    @Test
    fun ownerOfReturnsRuntimeForKnownRuntimeKey() {
        assertEquals(PreferenceOwner.RUNTIME, PreferenceOwnership.ownerOf("show_all_events"))
        assertEquals(
            PreferenceOwner.RUNTIME,
            PreferenceOwnership.ownerOf(MONOCHROME_SHADE_ICON_KEY),
        )
    }

    @Test
    fun ownerOfReturnsNullForRetiredLoggingKeys() {
        // The two retired logging keys must not come back through the import path either.
        assertNull(PreferenceOwnership.ownerOf("debug_mode"))
        assertNull(PreferenceOwnership.ownerOf("pref_log_sanitization_enabled"))
    }

    @Test
    fun ownerOfReturnsManagerForKnownManagerKey() {
        assertEquals(PreferenceOwner.MANAGER, PreferenceOwnership.ownerOf("config_directory"))
    }

    @Test
    fun ownerOfReturnsNullForUnknownKey() {
        assertNull(PreferenceOwnership.ownerOf("__nonexistent__"))
    }

    @Test
    fun runtimeAndManagerKeysDoNotOverlap() {
        val overlap = PreferenceOwnership.runtimeKeys().intersect(PreferenceOwnership.managerKeys())
        assertTrue(overlap.isEmpty())
    }

    @Test
    fun runtimeAndManagerKeysCoverAllEntries() {
        val total = PreferenceOwnership.runtimeKeys().size + PreferenceOwnership.managerKeys().size
        assertEquals(PreferenceOwnership.entries.size, total)
    }

    @Test
    fun booleanDefaultsParseCorrectly() {
        PreferenceOwnership.entries
            .filter { it.defaultValue?.type == "boolean" }
            .forEach { entry ->
                val defaultValue = entry.defaultValue
                requireNotNull(defaultValue)
                assertTrue(
                    defaultValue.value == "true" || defaultValue.value == "false",
                    "Invalid boolean default for '${entry.key}': ${defaultValue.value}",
                )
            }
    }

    @Test
    fun intDefaultsParseCorrectly() {
        PreferenceOwnership.entries
            .filter { it.defaultValue?.type == "int" }
            .forEach { entry ->
                val parsed = entry.defaultValue?.value?.toIntOrNull()
                if (parsed == null) {
                    throw AssertionError("Invalid int default for '${entry.key}'")
                }
            }
    }
}
