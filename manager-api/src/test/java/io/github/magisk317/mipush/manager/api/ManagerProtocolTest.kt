package io.github.magisk317.mipush.manager.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerProtocolTest {
    @Test
    fun `matching major negotiates the lower minor`() {
        val compatibility = ManagerProtocol.evaluateCompatibility(
            clientMajor = 1,
            clientMinor = 4,
            runtimeMajor = 1,
            runtimeMinor = 2,
        )

        assertTrue(compatibility.isCompatible)
        assertEquals(2, compatibility.negotiatedMinor)
        assertNull(compatibility.reason)
    }

    @Test
    fun `major mismatch blocks only protocol compatibility`() {
        val compatibility = ManagerProtocol.evaluateCompatibility(
            clientMajor = 2,
            clientMinor = 0,
            runtimeMajor = 1,
            runtimeMinor = 8,
        )

        assertFalse(compatibility.isCompatible)
        assertNull(compatibility.negotiatedMinor)
        assertEquals("protocol_major_mismatch", compatibility.reason)
    }

    @Test
    fun `invalid versions return a typed incompatibility`() {
        val compatibility = ManagerProtocol.evaluateCompatibility(
            clientMajor = ManagerProtocol.MAJOR,
            clientMinor = -1,
        )

        assertFalse(compatibility.isCompatible)
        assertEquals(ManagerProtocol.CompatibilityStatus.INVALID_VERSION, compatibility.status)
        assertEquals("invalid_protocol_version", compatibility.reason)
    }

    @Test
    fun `unknown runtime capabilities are ignored`() {
        val capabilities = ManagerProtocol.recognizedCapabilities(
            listOf(
                "future_capability",
                ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT,
                ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT,
            ),
        )

        assertEquals(setOf(ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT), capabilities)
    }
}
