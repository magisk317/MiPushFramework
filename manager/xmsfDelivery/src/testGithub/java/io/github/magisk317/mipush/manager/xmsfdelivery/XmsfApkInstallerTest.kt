package io.github.magisk317.mipush.manager.xmsfdelivery

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class XmsfApkInstallerTest {

    @Test
    fun `pre-O platforms have no install toggle and always allow requests`() {
        assertTrue(XmsfApkInstaller.canRequestPackageInstalls(sdkInt = 25, canRequest = false))
    }

    @Test
    fun `O and above follow the per-app user setting`() {
        assertTrue(XmsfApkInstaller.canRequestPackageInstalls(sdkInt = 26, canRequest = true))
        assertFalse(XmsfApkInstaller.canRequestPackageInstalls(sdkInt = 26, canRequest = false))
    }

    @Test
    fun `the O boundary itself is toggle-driven`() {
        assertFalse(XmsfApkInstaller.canRequestPackageInstalls(Build.VERSION_CODES.O, canRequest = false))
        assertTrue(XmsfApkInstaller.canRequestPackageInstalls(Build.VERSION_CODES.O, canRequest = true))
    }

    @Test
    fun `current platform sdk is handled`() {
        // Whatever the test JVM reports, the function must be total.
        val result = XmsfApkInstaller.canRequestPackageInstalls(Build.VERSION.SDK_INT, canRequest = true)

        assertTrue(result)
    }

    // ---- installer flow (mocked PackageInstaller) ----

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var packageInstaller: PackageInstaller
    private lateinit var apk: File

    @BeforeEach
    fun setUpMocks() {
        context = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        packageInstaller = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.packageManager } returns packageManager
        every { packageManager.packageInstaller } returns packageInstaller
        apk = createTempDirectory(prefix = "xmsf-installer-test").resolve("xmsf-runtime.apk").toFile()
        apk.writeBytes(ByteArray(32) { index -> index.toByte() })
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns mockk(relaxed = true)
        // SessionParams and Intent live in the android.jar stub: their real
        // methods throw "not mocked" under unit tests.
        mockkConstructor(PackageInstaller.SessionParams::class)
        every {
            anyConstructed<PackageInstaller.SessionParams>().setAppPackageName(any())
        } just Runs
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDownMocks() {
        unmockkConstructor(Intent::class)
        unmockkConstructor(PackageInstaller.SessionParams::class)
        unmockkStatic(PendingIntent::class)
    }

    @Test
    fun `session setup failure resumes with a failure event`() = runBlocking {
        every { packageInstaller.createSession(any()) } throws IllegalStateException("boom")

        val event = XmsfApkInstaller(context).install(apk) {}

        assertTrue(event is XmsfInstallEvent.Failed, "setup failure must not suspend forever")
        assertTrue(
            (event as XmsfInstallEvent.Failed).message.contains("boom"),
            "failure message must carry the root cause: ${event.message}",
        )
    }

    @Test
    fun `cancel completes a suspended install with a failure`() = runBlocking {
        val session = mockk<PackageInstaller.Session>(relaxed = true)
        every { packageInstaller.createSession(any()) } returns SESSION_ID
        every { packageInstaller.openSession(SESSION_ID) } returns session

        val installer = XmsfApkInstaller(context)
        var event: XmsfInstallEvent? = null
        val job = launch { event = installer.install(apk) {} }

        // Let install reach the suspended broadcast wait (commit succeeds, no
        // status broadcast is ever delivered in this test).
        repeat(3) { yield() }
        installer.cancel()
        withTimeout(2_000) { job.join() }

        assertTrue(event is XmsfInstallEvent.Failed, "cancel must complete the suspended install")
        assertEquals("install cancelled", (event as XmsfInstallEvent.Failed).message)
        verify { packageInstaller.abandonSession(SESSION_ID) }
    }

    private companion object {
        const val SESSION_ID = 4242
    }
}
