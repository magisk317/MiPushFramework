package io.github.magisk317.mipush.feature.wizard

import android.os.Build
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveUpdatePermissionVisibilityTest {
    @Test
    fun `live update permission is hidden before Android 16 qpr1`() {
        assertFalse(supportsLiveUpdatePermission(Build.VERSION_CODES_FULL.BAKLAVA_1 - 1))
    }

    @Test
    fun `live update permission is visible on Android 16 qpr1 and later`() {
        assertTrue(supportsLiveUpdatePermission(Build.VERSION_CODES_FULL.BAKLAVA_1))
        assertTrue(supportsLiveUpdatePermission(Build.VERSION_CODES_FULL.BAKLAVA_1 + 1))
    }
}
