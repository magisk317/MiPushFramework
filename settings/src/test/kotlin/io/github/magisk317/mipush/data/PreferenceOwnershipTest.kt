package io.github.magisk317.mipush.data

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
        assertEquals(PreferenceOwner.RUNTIME, PreferenceOwnership.ownerOf("debug_mode"))
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
                assertTrue(
                    entry.defaultValue!!.value == "true" || entry.defaultValue!!.value == "false",
                    "Invalid boolean default for '${entry.key}': ${entry.defaultValue.value}",
                )
            }
    }

    @Test
    fun intDefaultsParseCorrectly() {
        PreferenceOwnership.entries
            .filter { it.defaultValue?.type == "int" }
            .forEach { entry ->
                entry.defaultValue!!.value.toIntOrNull()
                    ?: throw AssertionError("Invalid int default for '${entry.key}'")
            }
    }
}
