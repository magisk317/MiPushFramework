package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfLatestCheck
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfRuntimeManifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun runtimeManifest(versionName: String) = XmsfRuntimeManifest(
        schema = XmsfRuntimeManifest.SCHEMA,
        variant = XmsfRuntimeManifest.VARIANT,
        versionName = versionName,
        versionCode = 1003003000L,
        minManagerVersionCode = 0L,
        sha256 = "0".repeat(64),
    )
}
