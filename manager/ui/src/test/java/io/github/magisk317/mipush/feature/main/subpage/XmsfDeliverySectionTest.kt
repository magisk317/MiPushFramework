package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfLatestCheck
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfRuntimeFlavor
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfRuntimeManifest
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfRuntimeState
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XmsfDeliverySectionTest {

    @Test
    fun `available check drives the arrow to the probed runtime`() {
        val target = xmsfArrowTarget(
            XmsfLatestCheck.Available(runtimeManifest("1.1.1-20261022_025631")),
            expectedVersionName = "1.1.1",
        )

        assertEquals("1.1.1-20261022_025631", target)
    }

    @Test
    fun `up to date hides the arrow instead of pointing at the pin`() {
        assertNull(xmsfArrowTarget(XmsfLatestCheck.UpToDate, expectedVersionName = "1.1.1"))
    }

    @Test
    fun `failed probe hides the arrow instead of promising a 404`() {
        assertNull(xmsfArrowTarget(XmsfLatestCheck.Failed("boom"), expectedVersionName = "1.1.1"))
    }

    @Test
    fun `unsupported flavor hides the arrow`() {
        assertNull(xmsfArrowTarget(XmsfLatestCheck.Unsupported, expectedVersionName = "1.1.1"))
    }

    @Test
    fun `unprobed state falls back to the build pin`() {
        assertEquals("1.1.1", xmsfArrowTarget(null, expectedVersionName = "1.1.1"))
    }

    @Test
    fun `unprobed state without a pin yields null`() {
        assertNull(xmsfArrowTarget(null, expectedVersionName = null))
    }

    @Test
    fun `ready runtime still installs when the probe found a newer one`() {
        assertTrue(
            xmsfInstallEnabled(
                XmsfStatus(state = XmsfRuntimeState.READY),
                XmsfLatestCheck.Available(runtimeManifest("1.1.1-20261022_025631")),
            ),
        )
    }

    @Test
    fun `ready runtime with nothing newer keeps the action disabled`() {
        assertFalse(
            xmsfInstallEnabled(
                XmsfStatus(state = XmsfRuntimeState.READY),
                XmsfLatestCheck.UpToDate,
            ),
        )
    }

    @Test
    fun `missing runtime keeps the action enabled before any probe`() {
        assertTrue(xmsfInstallEnabled(XmsfStatus(state = XmsfRuntimeState.NOT_INSTALLED), null))
    }

    @Test
    fun `installed runtime label tags only the non-default variant`() {
        assertEquals(
            "1.1.2-20261026_143718",
            xmsfInstalledVersionLabel(
                XmsfStatus(
                    installedVersionName = "1.1.2-20261026_143718",
                    runtimeFlavor = XmsfRuntimeFlavor.Normal,
                ),
            ),
        )
        assertEquals(
            "1.1.2-20261026_143718/vc105",
            xmsfInstalledVersionLabel(
                XmsfStatus(
                    installedVersionName = "1.1.2-20261026_143718",
                    runtimeFlavor = XmsfRuntimeFlavor.Vc105,
                ),
            ),
        )
        // Both variants of a build share one versionName: without this tag a
        // vc105 device looks identical to a normal one in the summary line.
        assertNull(xmsfInstalledVersionLabel(XmsfStatus(state = XmsfRuntimeState.NOT_INSTALLED)))
    }

    private fun runtimeManifest(versionName: String) = XmsfRuntimeManifest(
        schema = XmsfRuntimeManifest.SCHEMA,
        variant = XmsfRuntimeManifest.VARIANT,
        versionName = versionName,
        versionCode = 1003003000L,
        minManagerVersionCode = 0L,
        sha256 = "0".repeat(64),
    )
}
