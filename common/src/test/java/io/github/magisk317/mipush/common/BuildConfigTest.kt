package io.github.magisk317.mipush.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildConfigTest {

    @Test
    fun `push version code uses push service version`() {
        assertEquals("1003003000", BuildConfig.PUSH_VERSION_CODE)
    }
}
