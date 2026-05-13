package io.github.magisk317.mipush.hook.fakedevice.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

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
    fun `real MIAPP metadata keys resolve numeric app key`() {
        val resolved = ModuleCredentialResolver.resolveFromMetadataEntries(
            listOf(
                mapOf(
                    "MIAPP_ID" to "2882303761517506461",
                    "MIAPP_KEY" to "5601750626461",
                ),
            ),
        )

        assertEquals(
            ModuleCredential(appId = "2882303761517506461", appKey = "5601750626461"),
            resolved,
        )
    }

    @Test
    fun `agoo xiaomi metadata strips manifest prefixes`() {
        val resolved = ModuleCredentialResolver.resolveFromMetadataEntries(
            listOf(
                mapOf(
                    "org.android.agoo.xiaomi.app_id" to "appid=2882303761517245189",
                    "org.android.agoo.xiaomi.app_key" to "appkey=5461724563189",
                ),
            ),
        )

        assertEquals(
            ModuleCredential(appId = "2882303761517245189", appKey = "5461724563189"),
            resolved,
        )
    }

    @Test
    fun `xiaomi metadata strips numeric long suffix`() {
        val resolved = ModuleCredentialResolver.resolveFromMetadataEntries(
            listOf(
                mapOf(
                    "XIAOMI_APP_ID" to "2882303761517463096L",
                    "XIAOMI_APP_KEY" to "5101746355096L",
                ),
            ),
        )

        assertEquals(
            ModuleCredential(appId = "2882303761517463096", appKey = "5101746355096"),
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
