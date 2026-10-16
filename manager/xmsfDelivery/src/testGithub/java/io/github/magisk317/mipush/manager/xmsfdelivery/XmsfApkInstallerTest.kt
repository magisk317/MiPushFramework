package io.github.magisk317.mipush.manager.xmsfdelivery

import android.os.Build
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
}
