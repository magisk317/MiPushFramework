package io.github.magisk317.mipush.manager.xmsfdelivery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The variant a device runs is what keeps an update inside its own channel, and
 * it is resolved from a version code -- so the numbers baked from the version
 * catalog, and the channel keys derived from them, are asserted here.
 */
class XmsfRuntimeFlavorTest {

    @Test
    fun `version codes map to their variant`() {
        assertEquals(NORMAL_CODE, XmsfRuntimeFlavor.Normal.versionCode)
        assertEquals(VC105_CODE, XmsfRuntimeFlavor.Vc105.versionCode)
        assertEquals(XmsfRuntimeFlavor.Normal, XmsfRuntimeFlavor.forVersionCode(NORMAL_CODE))
        assertEquals(XmsfRuntimeFlavor.Vc105, XmsfRuntimeFlavor.forVersionCode(VC105_CODE))
    }

    @Test
    fun `unknown or absent version code falls back to normal`() {
        // A device with nothing installed has no variant to honour, and normal is
        // the variant the release pipeline has always published.
        assertEquals(XmsfRuntimeFlavor.Normal, XmsfRuntimeFlavor.forVersionCode(null))
        assertEquals(XmsfRuntimeFlavor.Normal, XmsfRuntimeFlavor.forVersionCode(7L))
    }

    @Test
    fun `every variant owns one channel root and one registry package`() {
        val ciDistRoots = XmsfRuntimeFlavor.entries.map { it.ciDistRoot }
        val registryPackages = XmsfRuntimeFlavor.entries.map { it.registryPackage }

        assertEquals(
            listOf("com.xiaomi.xmsf", "com.xiaomi.xmsf-vc105"),
            ciDistRoots,
            "ci-dist roots must match the layout scripts/ci/stage_ci_dist.sh stages",
        )
        assertEquals(
            listOf("xmsf-runtime", "xmsf-runtime-vc105"),
            registryPackages,
            "registry packages must match what scripts/ci/xmsf_runtime_publish.sh publishes",
        )
        assertEquals(ciDistRoots.distinct().size, ciDistRoots.size)
        assertEquals(registryPackages.distinct().size, registryPackages.size)
    }

    private companion object {
        const val NORMAL_CODE = 1003003000L
        const val VC105_CODE = 105L
    }
}
