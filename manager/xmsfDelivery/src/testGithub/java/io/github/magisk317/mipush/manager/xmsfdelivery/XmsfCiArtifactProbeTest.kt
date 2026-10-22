package io.github.magisk317.mipush.manager.xmsfdelivery

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class XmsfCiArtifactProbeTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `sidecar version with a compatible manifest resolves`() = runBlocking {
        enqueueSidecar("1.1.1-20261022_025631")
        enqueueManifest("1.1.1-20261022_025631")

        val manifest = probe().fetchLatestCompatible(HOST_VERSION_CODE)

        assertEquals("1.1.1-20261022_025631", manifest.versionName)
        assertTrue(manifest.validate(HOST_VERSION_CODE) == null)
    }

    @Test
    fun `manifest version that disagrees with the sidecar fails loudly`() = runBlocking {
        enqueueSidecar("1.1.1-20261022_025631")
        enqueueManifest("1.1.1-20261022_025999")

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { probe().fetchLatestCompatible(HOST_VERSION_CODE) }
        }

        assertTrue(error.message!!.contains("does not match sidecar"), error.message!!)
    }

    @Test
    fun `incompatible manifest is rejected, not offered`() = runBlocking {
        enqueueSidecar("1.1.1-20261022_025631")
        enqueueManifest("1.1.1-20261022_025631", minManagerVersionCode = HOST_VERSION_CODE + 1)

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { probe().fetchLatestCompatible(HOST_VERSION_CODE) }
        }

        assertTrue(error.message!!.contains("rejected"), error.message!!)
    }

    @Test
    fun `expired artifacts fail loudly on the sidecar`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { probe().fetchLatestCompatible(HOST_VERSION_CODE) }
        }

        assertTrue(error.message!!.contains("ci artifacts probe failed"), error.message!!)
    }

    @Test
    fun `missing manifest behind a live sidecar fails loudly`() = runBlocking {
        enqueueSidecar("1.1.1-20261022_025631")
        server.enqueue(MockResponse.Builder().code(404).build())

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { probe().fetchLatestCompatible(HOST_VERSION_CODE) }
        }

        assertTrue(error.message!!.contains("ci artifacts probe failed"), error.message!!)
    }

    @Test
    fun `blank sidecar fails loudly`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("  \n").build())

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { probe().fetchLatestCompatible(HOST_VERSION_CODE) }
        }

        assertTrue(error.message!!.contains("sidecar is blank"), error.message!!)
    }

    private fun probe() = XmsfCiArtifactProbe(
        versionSidecarUrl = server.url("/ci-dist/com.xiaomi.xmsf/version.txt").toString(),
        manifestBaseUrl = server.url("/ci-dist/com.xiaomi.xmsf").toString(),
        httpClient = OkHttpClient(),
    )

    private fun enqueueSidecar(version: String) {
        server.enqueue(MockResponse.Builder().code(200).body(version).build())
    }

    private fun enqueueManifest(version: String, minManagerVersionCode: Long = 0L) {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """
                {
                  "schema": ${XmsfRuntimeManifest.SCHEMA},
                  "variant": "${XmsfRuntimeManifest.VARIANT}",
                  "versionName": "$version",
                  "versionCode": 1003003000,
                  "minManagerVersionCode": $minManagerVersionCode,
                  "sha256": "${"0".repeat(64)}"
                }
                """.trimIndent(),
            ).build(),
        )
    }

    private companion object {
        const val HOST_VERSION_CODE = 1003003000L
    }
}
