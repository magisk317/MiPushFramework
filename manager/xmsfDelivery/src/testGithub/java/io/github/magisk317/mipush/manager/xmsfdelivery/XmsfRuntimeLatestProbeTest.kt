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

class XmsfRuntimeLatestProbeTest {

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
    fun `newest published version wins regardless of api order`() = runBlocking {
        // Deliberately listed oldest-first: the comparator, not the API
        // order, decides what "latest" means.
        enqueuePackages("1.0.8-20261014_222819", "1.0.9", "1.0.10")
        enqueueManifest("1.0.10")

        val manifest = probe().fetchLatestCompatible(HOST_VERSION_CODE)

        assertEquals("1.0.10", manifest.versionName)
        assertTrue(manifest.validate(HOST_VERSION_CODE) == null)
    }

    @Test
    fun `clean release outranks its own ci child at the same core`() = runBlocking {
        enqueuePackages("1.0.9-20261016_120000", "1.0.9")
        enqueueManifest("1.0.9")

        val manifest = probe().fetchLatestCompatible(HOST_VERSION_CODE)

        assertEquals("1.0.9", manifest.versionName)
    }

    @Test
    fun `version needing a newer manager falls back to the next one down`() = runBlocking {
        enqueuePackages("1.0.11", "1.0.10")
        // Newest first: 1.0.11 demands a manager this host cannot satisfy.
        enqueueManifest("1.0.11", minManagerVersionCode = HOST_VERSION_CODE + 1)
        enqueueManifest("1.0.10")

        val manifest = probe().fetchLatestCompatible(HOST_VERSION_CODE)

        assertEquals("1.0.10", manifest.versionName)
        // One listing + two manifests (the incompatible one, then the hit).
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `incompatible schema and variant are skipped, not offered`() = runBlocking {
        enqueuePackages("2.0.0", "1.0.10")
        enqueueManifest("2.0.0", schema = 2)
        enqueueManifest("1.0.10")

        val manifest = probe().fetchLatestCompatible(HOST_VERSION_CODE)

        assertEquals("1.0.10", manifest.versionName)
    }

    @Test
    fun `no compatible version at all fails loudly`() = runBlocking {
        enqueuePackages("1.0.11")
        enqueueManifest("1.0.11", minManagerVersionCode = HOST_VERSION_CODE + 1)

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { probe().fetchLatestCompatible(HOST_VERSION_CODE) }
        }

        assertTrue(error.message!!.contains("no published runtime is compatible"), error.message!!)
    }

    @Test
    fun `empty registry fails loudly`() = runBlocking {
        enqueuePackages()

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { probe().fetchLatestCompatible(HOST_VERSION_CODE) }
        }

        assertTrue(error.message!!.contains("registry lists no"), error.message!!)
    }

    @Test
    fun `unreachable registry fails loudly`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).build())

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { probe().fetchLatestCompatible(HOST_VERSION_CODE) }
        }

        assertTrue(error.message!!.contains("registry probe failed"), error.message!!)
    }

    @Test
    fun `duplicate package entries for one version are deduplicated`() = runBlocking {
        // Generic package uploads can leave two entries for the same version.
        enqueuePackages("1.0.10", "1.0.10", "1.0.9")
        enqueueManifest("1.0.10")

        val manifest = probe().fetchLatestCompatible(HOST_VERSION_CODE)

        assertEquals("1.0.10", manifest.versionName)
        // One listing + one manifest: the duplicate must not trigger a walk.
        assertEquals(2, server.requestCount)
    }

    private fun probe() = XmsfRuntimeLatestProbe(
        packagesApiUrl = server.url("/").toString() + "packages?package_name=xmsf-runtime&per_page=20",
        manifestBaseUrl = server.url("/").toString().removeSuffix("/"),
        httpClient = OkHttpClient(),
    )

    private fun enqueuePackages(vararg versions: String) {
        val entries = versions.joinToString(",") { """{"id": 1, "name": "xmsf-runtime", "version": "$it"}""" }
        server.enqueue(
            MockResponse.Builder().code(200).body("[$entries]").build(),
        )
    }

    private fun enqueueManifest(
        version: String,
        schema: Int = XmsfRuntimeManifest.SCHEMA,
        minManagerVersionCode: Long = 0L,
    ) {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """
                {
                  "schema": $schema,
                  "variant": "github",
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
