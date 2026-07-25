package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Status-bar substitution context tests.
 *
 * Covers [SystemUiNotificationPolicy.isStatusBarSubstitutionContext], the whitelist that keeps the
 * `shouldSubstituteSmallIcon` override on the status-bar path only.
 *
 * Background: `NotifImageUtil.shouldSubstituteSmallIcon` is called from three sites on CN HyperOS:
 * - `StatusBarIconView.updateIconColor()` — status bar; must be forced to false so MIUI does not
 *   clear the color filter and re-colorize BITMAP app logos under monochrome.
 * - `NotificationHeaderViewWrapper.resolveHeaderViews()` and `NotificationViewWrapper.<init>()` —
 *   notification-shade header / expanded rows; forcing false there suppresses the colored app icon
 *   (`applyAppIconAllowCustom`) and turns expanded rows and group-summary headers white.
 *
 * Whitelisting only the [SystemUiNotificationPolicy.STATUS_BAR_ICON_VIEW_CLASS] frame keeps the
 * override status-bar only, so the shade keeps native colored app icons (fixes the "expanded row
 * white" and "SMS group-summary white bubble" regressions).
 */
class StatusBarSubstitutionContextTest {

    private companion object {
        const val STATUS_BAR_ICON_VIEW =
            "com.android.systemui.statusbar.StatusBarIconView"
        const val HEADER_WRAPPER =
            "com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper"
        const val VIEW_WRAPPER =
            "com.android.systemui.statusbar.notification.row.wrapper.NotificationViewWrapper"
        const val NOTIF_IMAGE_UTIL =
            "com.android.systemui.statusbar.notification.utils.NotifImageUtil"
    }

    @Test
    fun `status bar icon view frame is a status-bar context`() {
        val stack = listOf(
            NOTIF_IMAGE_UTIL,
            STATUS_BAR_ICON_VIEW,
            "com.android.systemui.statusbar.phone.NotificationIconAreaController",
        )
        assertTrue(SystemUiNotificationPolicy.isStatusBarSubstitutionContext(stack))
    }

    @Test
    fun `notification shade header wrapper is not a status-bar context`() {
        val stack = listOf(NOTIF_IMAGE_UTIL, HEADER_WRAPPER)
        assertFalse(SystemUiNotificationPolicy.isStatusBarSubstitutionContext(stack))
    }

    @Test
    fun `notification view wrapper is not a status-bar context`() {
        val stack = listOf(NOTIF_IMAGE_UTIL, VIEW_WRAPPER)
        assertFalse(SystemUiNotificationPolicy.isStatusBarSubstitutionContext(stack))
    }

    @Test
    fun `empty stack is not a status-bar context`() {
        assertFalse(SystemUiNotificationPolicy.isStatusBarSubstitutionContext(emptyList()))
    }

    @Test
    fun `status bar frame is detected regardless of position`() {
        val deep = listOf(
            NOTIF_IMAGE_UTIL,
            "some.other.Frame",
            "another.Frame",
            STATUS_BAR_ICON_VIEW,
        )
        assertTrue(SystemUiNotificationPolicy.isStatusBarSubstitutionContext(deep))
    }
}
