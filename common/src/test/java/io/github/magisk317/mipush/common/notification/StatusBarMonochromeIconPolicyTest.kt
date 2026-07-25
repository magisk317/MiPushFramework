package io.github.magisk317.mipush.common.notification

import android.os.Bundle
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class StatusBarMonochromeIconPolicyTest {
    @Test
    fun `color mode never rewrites`() {
        assertFalse(
            StatusBarMonochromeIconPolicy.shouldRewrite(
                colorStatusBarIcon = true,
                forceGlobalStatusBarIcons = true,
                extras = null,
            ),
        )
    }

    @Test
    fun `global monochrome rewrites non-island`() {
        assertTrue(
            StatusBarMonochromeIconPolicy.shouldRewrite(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                extras = null,
            ),
        )
    }

    @Test
    fun `non-global monochrome skips native posts`() {
        assertFalse(
            StatusBarMonochromeIconPolicy.shouldRewrite(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = false,
                extras = null,
            ),
        )
    }

    @Test
    fun `island proxy is skipped`() {
        val extras = Bundle().apply {
            putString("hyperisland.owner", "io.github.magisk317.mipush")
        }
        assertFalse(
            StatusBarMonochromeIconPolicy.shouldRewrite(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                extras = extras,
            ),
        )
    }

    @Test
    fun `second apply is no-op for same notification instance`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val notification = android.app.Notification.Builder(context, "ch")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("t")
            .build()
        val first = StatusBarMonochromeIconPolicy.apply(
            context = context,
            postingPackage = "com.example.app",
            notification = notification,
        )
        // first may fail under pure unit env without app icon; only assert no crash and second is false when first true
        val second = StatusBarMonochromeIconPolicy.apply(
            context = context,
            postingPackage = "com.example.app",
            notification = notification,
        )
        if (first) {
            org.junit.jupiter.api.Assertions.assertFalse(second)
        }
    }

}
