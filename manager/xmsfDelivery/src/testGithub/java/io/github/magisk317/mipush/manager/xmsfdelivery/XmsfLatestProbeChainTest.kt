package io.github.magisk317.mipush.manager.xmsfdelivery

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XmsfLatestProbeChainTest {

    @Test
    fun `ci artifacts answer wins and is flagged`() = runBlocking {
        val ci = mockk<XmsfCiArtifactProbe>()
        val registry = mockk<XmsfRuntimeLatestProbe>()
        coEvery { ci.fetchLatestCompatible(any()) } returns manifest("1.1.1-20261022_025631")

        val result = XmsfLatestProbeChain(ci, registry).fetchLatestCompatible(HOST_VERSION_CODE)

        assertEquals("1.1.1-20261022_025631", result.manifest.versionName)
        assertTrue(result.fromCiArtifacts)
        io.mockk.coVerify(exactly = 0) { registry.fetchLatestCompatible(any()) }
    }

    @Test
    fun `ci failure falls back to the registry`() = runBlocking {
        val ci = mockk<XmsfCiArtifactProbe>()
        val registry = mockk<XmsfRuntimeLatestProbe>()
        coEvery { ci.fetchLatestCompatible(any()) } throws
            IllegalStateException("ci artifacts probe failed: HTTP 404")
        coEvery { registry.fetchLatestCompatible(any()) } returns manifest("1.1.0")

        val result = XmsfLatestProbeChain(ci, registry).fetchLatestCompatible(HOST_VERSION_CODE)

        assertEquals("1.1.0", result.manifest.versionName)
        assertFalse(result.fromCiArtifacts)
    }

    @Test
    fun `both tracks failing propagates the registry error`() = runBlocking {
        val ci = mockk<XmsfCiArtifactProbe>()
        val registry = mockk<XmsfRuntimeLatestProbe>()
        coEvery { ci.fetchLatestCompatible(any()) } throws
            IllegalStateException("ci artifacts probe failed: HTTP 404")
        coEvery { registry.fetchLatestCompatible(any()) } throws
            IllegalStateException("registry lists no xmsf-runtime packages")

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                XmsfLatestProbeChain(ci, registry).fetchLatestCompatible(HOST_VERSION_CODE)
            }
        }

        assertTrue(error.message!!.contains("registry lists no"), error.message!!)
    }

    @Test
    fun `absent ci probe goes straight to the registry`() = runBlocking {
        val registry = mockk<XmsfRuntimeLatestProbe>()
        coEvery { registry.fetchLatestCompatible(any()) } returns manifest("1.1.0")

        val result = XmsfLatestProbeChain(ciArtifacts = null, registry = registry)
            .fetchLatestCompatible(HOST_VERSION_CODE)

        assertEquals("1.1.0", result.manifest.versionName)
        assertFalse(result.fromCiArtifacts)
    }

    private fun manifest(versionName: String) = XmsfRuntimeManifest(
        schema = XmsfRuntimeManifest.SCHEMA,
        variant = XmsfRuntimeManifest.VARIANT,
        versionName = versionName,
        versionCode = 1003003000L,
        minManagerVersionCode = 0L,
        sha256 = "0".repeat(64),
    )

    private companion object {
        const val HOST_VERSION_CODE = 1003003000L
    }
}
