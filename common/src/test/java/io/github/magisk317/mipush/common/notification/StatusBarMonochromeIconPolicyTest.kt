package io.github.magisk317.mipush.common.notification

import android.content.Context
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StatusBarMonochromeIconPolicyTest {
    @Test
    fun `status bar fallback helper returns null when package icon is unavailable`() {
        val context = mockk<Context>(relaxed = true)
        assertNull(
            StatusBarMonochromeIconPolicy.whiteIconForPackageOrNull(
                context,
                "com.example.app",
            ),
        )
    }

}
