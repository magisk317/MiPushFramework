package io.github.magisk317.mipush.manager.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerPhase3ParcelableTest {
    @Test
    fun `protocol recognizes phase 3 capabilities`() {
        assertTrue(ManagerProtocol.MINOR >= 3)
        assertTrue(ManagerProtocol.KNOWN_CAPABILITIES.contains(ManagerProtocol.CAPABILITY_RUNTIME_PREFERENCES))
        assertTrue(ManagerProtocol.KNOWN_CAPABILITIES.contains(ManagerProtocol.CAPABILITY_CONFIGURATION_UPLOAD))
        assertEquals(
            "configuration_upload_missing_descriptor",
            ManagerProtocol.validateConfigurationUploadRequest(
                ManagerConfigurationUploadRequestDto(path = "a.json", contentLength = 1),
            ),
        )
    }
}
