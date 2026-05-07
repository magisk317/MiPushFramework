package io.github.magisk317.mipush.hook.fakedevice.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModuleCredentialResolverTest {
    @Test
    fun `direct metadata keys win over fallback`() {
        val resolved = ModuleCredentialResolver.resolveFromMetadataEntries(
            listOf(
                mapOf(
                    "mipush_app_id" to "2882303761517000000",
                    "mipush_app_key" to "ABCD1234EFGH",
                    "random_app_id" to "123",
                ),
            ),
        )

        assertEquals(
            ModuleCredential(appId = "2882303761517000000", appKey = "ABCD1234EFGH"),
            resolved,
        )
    }

    @Test
    fun `fallback heuristic extracts credential from generic keys`() {
        val resolved = ModuleCredentialResolver.resolveFromMetadataEntries(
            listOf(
                mapOf(
                    "xiaomi_app_id_value" to "2882303761517999999",
                    "xiaomi_app_key_value" to "KEYVALUEABCDE1",
                ),
            ),
        )

        assertEquals(
            ModuleCredential(appId = "2882303761517999999", appKey = "KEYVALUEABCDE1"),
            resolved,
        )
    }

    @Test
    fun `invalid metadata returns null`() {
        assertNull(
            ModuleCredentialResolver.resolveFromMetadataEntries(
                listOf(mapOf("name" to "value")),
            ),
        )
    }
}
