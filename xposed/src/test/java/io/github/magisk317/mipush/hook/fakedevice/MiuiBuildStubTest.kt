package io.github.magisk317.mipush.hook.fakedevice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiuiBuildStubTest {
    @Test
    fun `miui build stub exposes stable cn build semantics`() {
        assertTrue(miui.os.Build.IS_STABLE_VERSION)
        assertFalse(miui.os.Build.IS_DEVELOPMENT_VERSION)
        assertFalse(miui.os.Build.IS_ALPHA_BUILD)
        assertFalse(miui.os.Build.IS_GLOBAL_BUILD)
        assertFalse(miui.os.Build.IS_INTERNATIONAL_BUILD)
        assertEquals("CN", miui.os.Build.getRegion())
        assertEquals("cn_chinatelecom", miui.os.Build.getCustVariant())
    }
}
