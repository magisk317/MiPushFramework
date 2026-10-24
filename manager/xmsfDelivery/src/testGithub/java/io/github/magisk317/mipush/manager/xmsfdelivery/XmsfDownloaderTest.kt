package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class XmsfDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var filesDir: File
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        filesDir = createTempDirectory(prefix = "xmsf-downloader-test").toFile()
        context = mockk()
        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir
    }

    @AfterEach
    fun tearDown() {
        server.close()
        filesDir.deleteRecursively()
    }

    @Test
    fun `happy path downloads verifies and caches the pinned runtime`() = runBlocking {
        val apkBytes = "fake-runtime-apk".toByteArray()
        val hash = sha256Hex(apkBytes)
        enqueueManifest(hash)
        enqueueApk(apkBytes)
        enqueueSidecar(hash)

        val downloaded = downloader().download()

        assertTrue(downloaded.apk.isFile)
        assertEquals(apkBytes.size.toLong(), downloaded.apk.length())
        assertEquals(VERSION, downloaded.manifest.versionName)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `cached runtime is reused without re-downloading the apk`() = runBlocking {
        val apkBytes = "fake-runtime-apk".toByteArray()
        val hash = sha256Hex(apkBytes)
        enqueueManifest(hash)
        enqueueApk(apkBytes)
        enqueueSidecar(hash)
        downloader().download()
        val requestsAfterFirst = server.requestCount

        // Only the manifest is re-fetched; the APK must stay untouched.
        enqueueManifest(hash)
        val progress = mutableListOf<Int>()
        val cached = downloader().download { progress += it }

        assertTrue(cached.apk.isFile)
        assertEquals(requestsAfterFirst + 1, server.requestCount)
        assertEquals(listOf(XmsfDownloader.PROGRESS_COMPLETE), progress)
    }

    @Test
    fun `corrupted cache is dropped and re-downloaded`() = runBlocking {
        val apkBytes = "fake-runtime-apk".toByteArray()
        val hash = sha256Hex(apkBytes)
        enqueueManifest(hash)
        enqueueApk(apkBytes)
        enqueueSidecar(hash)
        val first = downloader().download()
        first.apk.writeBytes("corrupted".toByteArray())

        enqueueManifest(hash)
        enqueueApk(apkBytes)
        enqueueSidecar(hash)
        val second = downloader().download()

        assertTrue(second.apk.readBytes().contentEquals(apkBytes))
    }

    @Test
    fun `sidecar checksum mismatch deletes the download and fails`() = runBlocking {
        val apkBytes = "fake-runtime-apk".toByteArray()
        enqueueManifest(sha256Hex(apkBytes))
        enqueueApk(apkBytes)
        server.enqueue(MockResponse.Builder().code(200).body("f".repeat(64)).build())

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { downloader().download() }
        }

        assertEquals("runtime checksum mismatch", error.message)
        assertFalse(downloadedApkFile().isFile)
    }

    @Test
    fun `missing sidecar falls back to the manifest hash`() = runBlocking {
        val apkBytes = "fake-runtime-apk".toByteArray()
        val hash = sha256Hex(apkBytes)
        enqueueManifest(hash)
        enqueueApk(apkBytes)
        server.enqueue(MockResponse.Builder().code(404).build())

        val downloaded = downloader().download()

        assertTrue(downloaded.apk.isFile)
        assertEquals(hash, sha256Hex(downloaded.apk))
    }

    @Test
    fun `apk download failure surfaces the http status`() = runBlocking {
        enqueueManifest("0".repeat(64))
        server.enqueue(MockResponse.Builder().code(404).build())

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { downloader().download() }
        }

        assertTrue(error.message!!.contains("runtime download failed"), error.message!!)
        assertFalse(downloadedApkFile().isFile)
    }

    @Test
    fun `manifest describing another version is rejected`() = runBlocking {
        enqueueManifest("0".repeat(64), version = "9.9.9")

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { downloader().download() }
        }

        assertTrue(error.message!!.contains("does not match pinned"), error.message!!)
    }

    @Test
    fun `manifest requiring a newer manager is rejected`() = runBlocking {
        enqueueManifest("0".repeat(64), minManagerVersionCode = HOST_VERSION_CODE + 1)

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { downloader().download() }
        }

        assertTrue(error.message!!.contains("manifest rejected"), error.message!!)
    }

    @Test
    fun `malformed manifest fails loudly`() {
        server.enqueue(MockResponse.Builder().code(200).body("{not json").build())

        assertThrows(Exception::class.java) {
            runBlocking { downloader().download() }
        }
    }

    @Test
    fun `ci artifacts requests carry the job query`() = runBlocking {
        val apkBytes = "fake-runtime-apk".toByteArray()
        val hash = sha256Hex(apkBytes)
        enqueueManifest(hash)
        enqueueApk(apkBytes)
        enqueueSidecar(hash)

        ciDownloader().download()

        // The CI artifacts raw endpoint requires ?job=<job> on every file
        // (XmsfCiArtifactProbe.CI_JOB_QUERY); the registry base needs none.
        repeat(server.requestCount) {
            assertEquals("android:build", server.takeRequest().url.queryParameter("job"))
        }
    }

    private fun downloader(version: String = VERSION) = XmsfDownloader(
        context = context,
        expectedVersionName = version,
        baseUrl = server.url("/").toString().removeSuffix("/"),
        httpClient = OkHttpClient(),
        hostVersionCodeProvider = { HOST_VERSION_CODE },
    )

    private fun ciDownloader(version: String = VERSION) = XmsfDownloader(
        context = context,
        expectedVersionName = version,
        baseUrl = server.url("/").toString().removeSuffix("/"),
        httpClient = OkHttpClient(),
        hostVersionCodeProvider = { HOST_VERSION_CODE },
        requestQuery = XmsfCiArtifactProbe.CI_JOB_QUERY,
    )

    private fun downloadedApkFile(): File =
        filesDir.resolve("xmsf-runtime/$VERSION/${XmsfDownloader.APK_NAME}")

    private fun enqueueApk(bytes: ByteArray) {
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(bytes)).build())
    }

    private fun enqueueManifest(
        sha256: String,
        version: String = VERSION,
        minManagerVersionCode: Long = 0L,
    ) {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """
                {
                  "schema": 1,
                  "variant": "github",
                  "versionName": "$version",
                  "versionCode": 1003003000,
                  "minManagerVersionCode": $minManagerVersionCode,
                  "sha256": "$sha256"
                }
                """.trimIndent(),
            ).build(),
        )
    }

    private fun enqueueSidecar(hash: String) {
        server.enqueue(MockResponse.Builder().code(200).body("$hash  xmsf-runtime.apk").build())
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256Hex(file: File): String = sha256Hex(file.readBytes())

    private companion object {
        const val VERSION = "1.0.9"
        const val HOST_VERSION_CODE = 1003003000L
    }
}
