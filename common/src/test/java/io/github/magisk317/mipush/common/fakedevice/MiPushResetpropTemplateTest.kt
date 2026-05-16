package io.github.magisk317.mipush.common.fakedevice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiPushResetpropTemplateTest {
    @Test
    fun `default template exposes custom props from mipush prop toml`() {
        val props = MiPushResetpropTemplate.defaultCustomProps()

        assertEquals("Xiaomi", props["ro.fota.oem"])
        assertEquals("V130", props["ro.miui.ui.version.name"])
        assertEquals("zh-CN", props["ro.product.locale"])
        assertEquals("1", props["sys.boot_completed"])
        assertEquals("arm64-v8a,armeabi-v7a,armeabi", props["ro.product.cpu.abilist"])
        assertEquals("V13.0.5.0.SGICNXM", props["ro.build.version.incremental"])
    }

    @Test
    fun `template values override base props`() {
        val merged = MiPushResetpropTemplate.mergedCustomProps(
            linkedMapOf(
                "ro.miui.ui.version.name" to "V130",
                "ro.product.brand" to "Xiaomi",
            )
        )

        assertEquals("V130", merged["ro.miui.ui.version.name"])
        assertEquals("Xiaomi", merged["ro.product.brand"])
        assertTrue(merged.containsKey("ro.miui.region"))
    }

    @Test
    fun `template clears competing vendor region fallbacks`() {
        val merged = MiPushResetpropTemplate.mergedCustomProps(
            linkedMapOf(
                "ro.miui.region" to "CN",
                "persist.sys.oppo.region" to "IN",
                "ro.hw.country" to "EU",
                "ro.vivo.os.version" to "14",
            )
        )

        assertEquals("", merged["persist.sys.oppo.region"])
        assertEquals("", merged["ro.hw.country"])
        assertEquals("", merged["ro.vivo.os.version"])
    }
}
