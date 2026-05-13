package io.github.magisk317.mipush.hook.fakedevice

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePropertyTest {
    @Test
    fun `build field overrides only include known Build fields`() {
        val overrides = mapOf(
            "ro.build.hw_emui_api_level" to "",
            "ro.product.brand" to "Xiaomi",
            "ro.build.version.release" to "12",
        ).buildFieldOverrides()

        assertEquals(2, overrides.size)
        assertTrue(
            overrides.any {
                it.targetClass == Build::class.java &&
                    it.fieldName == "BRAND" &&
                    it.value == "Xiaomi"
            },
        )
        assertTrue(
            overrides.any {
                it.targetClass == Build.VERSION::class.java &&
                    it.fieldName == "RELEASE" &&
                    it.value == "12"
            },
        )
        assertFalse(overrides.any { it.fieldName == "ro.build.hw_emui_api_level" })
    }
}
