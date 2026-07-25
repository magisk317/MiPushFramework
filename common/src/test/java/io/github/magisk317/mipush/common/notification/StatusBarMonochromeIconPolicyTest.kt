package io.github.magisk317.mipush.common.notification

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class StatusBarMonochromeIconPolicyTest {
    @Test
    fun `status bar fallback helper returns null when package icon is unavailable`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        assertNull(
            StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(
                context,
                "com.example.app",
            ),
        )
    }

}
