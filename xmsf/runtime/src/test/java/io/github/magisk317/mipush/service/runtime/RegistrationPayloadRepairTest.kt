package io.github.magisk317.mipush.service.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `manifest fallback resolves a complete appid+appkey pair`() {
        val resolved = RegistrationPayloadRepair.credentialFromMetadataEntries(
            listOf(
                mapOf("UMENG_APPKEY" to "umeng-only"),
                mapOf("xiaomi_appid" to "2882303761517000111", "xiaomi_appkey" to "5000000000111"),
            ),
        )
        assertEquals("2882303761517000111" to "5000000000111", resolved)
    }

    @Test
    fun `manifest fallback trims padded credential values`() {
        val resolved = RegistrationPayloadRepair.credentialFromMetadataEntries(
            listOf(mapOf("xiaomi_appid" to " 2882303761517000111 ", "xiaomi_appkey" to "	5000000000111")),
        )
        assertEquals("2882303761517000111" to "5000000000111", resolved)
    }

    @Test
    fun `manifest fallback rejects half credentials`() {
        assertNull(
            RegistrationPayloadRepair.credentialFromMetadataEntries(
                listOf(mapOf("com.xiaomi.push.app_id" to "2882303761517000111")),
            ),
        )
        assertNull(
            RegistrationPayloadRepair.credentialFromMetadataEntries(
                listOf(mapOf("com.xiaomi.push.app_id" to "2882303761517000111", "other" to "x")),
            ),
        )
    }
}
