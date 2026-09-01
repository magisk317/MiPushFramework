package com.xiaomi.slim

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BlobStreamPolicyWiringContractTest {
    @Test
    fun `reader executes handshake and payload plans on real stream path`() {
        val source = resolveVendorSource("com/xiaomi/slim/BlobReader.kt").readText()

        assertTrue("planSlimHandshake(" in source)
        assertTrue("planSlimPayloadDispatch(" in source)
        assertFalse("resolveSlimInboundPlan(blob3.channelId" in source)
    }

    @Test
    fun `writer fallback delegates to core write plan`() {
        val source = resolveVendorSource("com/xiaomi/slim/BlobWriter.kt").readText()

        assertTrue("PushSlimStreamPlanFactory.planWrite(" in source)
        assertFalse("if (serializedSize > Blob.MAX_BLOB_SIZE)" in source)
    }

    private fun resolveVendorSource(relativePath: String): File =
        listOf(
            File("vendor/src/main/java/$relativePath"),
            File("src/main/java/$relativePath"),
        ).firstOrNull(File::isFile)
            ?: error("Vendor source not found: $relativePath")
}
