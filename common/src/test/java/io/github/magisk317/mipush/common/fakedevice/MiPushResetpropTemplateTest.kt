package io.github.magisk317.mipush.common.fakedevice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiPushResetpropTemplateTest {
    @Test
    fun `default template exposes custom props from mipush prop toml`() {
        val props = MiPushResetpropTemplate.defaultCustomProps()

        assertEquals("Xiaomi", props["ro.fota.oem"])
        assertEquals("V125", props["ro.miui.ui.version.name"])
        assertEquals("zh-CN", props["ro.product.locale"])
    }

    @Test
    fun `template values override base props`() {
        val merged = MiPushResetpropTemplate.mergedCustomProps(
            linkedMapOf(
                "ro.miui.ui.version.name" to "V130",
                "ro.product.brand" to "Xiaomi",
            )
        )

        assertEquals("V125", merged["ro.miui.ui.version.name"])
        assertEquals("Xiaomi", merged["ro.product.brand"])
        assertTrue(merged.containsKey("ro.miui.region"))
    }
}
