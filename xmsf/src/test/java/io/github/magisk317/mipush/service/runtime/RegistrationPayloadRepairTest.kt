package io.github.magisk317.mipush.service.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class RegistrationPayloadRepairTest {
    @Test
    fun `parser keeps only confirmed credential overrides`() {
        val credentials = RegistrationPayloadRepair.parseCredentialOverrides(
            """
            {
              "profiles": [
                {
                  "packageName": "com.example.confirmed",
                  "hookPipelines": [],
                  "credentialOverride": {
                    "appId": "2882303761517000000",
                    "appKey": "5000000000000"
                  }
                },
                {
                  "packageName": "com.example.metadata.only",
                  "hookPipelines": ["COMMON"]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("2882303761517000000" to "5000000000000", credentials["com.example.confirmed"])
        assertFalse(credentials.containsKey("com.example.metadata.only"))
    }
}
