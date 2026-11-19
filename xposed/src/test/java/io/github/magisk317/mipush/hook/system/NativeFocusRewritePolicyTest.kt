package io.github.magisk317.mipush.hook.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeFocusRewritePolicyTest {
    @Test
    fun `disabled app preserves original focus notification`() {
        assertEquals(
            NativeFocusRewritePolicy.Decision.DISABLED,
            NativeFocusRewritePolicy.decide(input(enabled = false)),
        )
    }

    @Test
    fun `promotion only accepts a one shot trusted registration`() {
        assertTrue(
            NativeFocusRewritePolicy.shouldSetPromotedFlag(
                hasTrustedRewriteGrant = true,
                requestPromoted = true,
                ongoing = true,
                hasTitle = true,
                hasPromotableStyle = true,
                isGroupSummary = false,
                hasCustomViews = false,
                colorized = false,
                channelImportance = NativeFocusRewritePolicy.IMPORTANCE_DEFAULT,
            ),
        )
        assertFalse(
            NativeFocusRewritePolicy.shouldSetPromotedFlag(
                hasTrustedRewriteGrant = false,
                requestPromoted = true,
                ongoing = true,
                hasTitle = true,
                hasPromotableStyle = true,
                isGroupSummary = false,
                hasCustomViews = false,
                colorized = false,
                channelImportance = NativeFocusRewritePolicy.IMPORTANCE_DEFAULT,
            ),
        )
        assertFalse(
            NativeFocusRewritePolicy.shouldSetPromotedFlag(
                hasTrustedRewriteGrant = true,
                requestPromoted = true,
                ongoing = true,
                hasTitle = true,
                hasPromotableStyle = true,
                isGroupSummary = false,
                hasCustomViews = false,
                colorized = false,
                channelImportance = NativeFocusRewritePolicy.IMPORTANCE_MIN,
            ),
        )
    }

    @Test
    fun `only enabled non miui native focus notifications are candidates`() {
        assertEquals(
            NativeFocusRewritePolicy.Decision.REWRITE,
            NativeFocusRewritePolicy.decide(input()),
        )
        assertEquals(
            NativeFocusRewritePolicy.Decision.NATIVE_PLATFORM,
            NativeFocusRewritePolicy.decide(input(isMiui = true)),
        )
        assertEquals(
            NativeFocusRewritePolicy.Decision.NOT_FOCUS,
            NativeFocusRewritePolicy.decide(input(hasFocusPayload = false)),
        )
    }

    @Test
    fun `rewriter skips visuals group summaries and missing visible text`() {
        assertEquals(
            NativeFocusRewritePolicy.Decision.CUSTOM_VIEWS,
            NativeFocusRewritePolicy.decide(input(hasCustomViews = true)),
        )
        assertEquals(
            NativeFocusRewritePolicy.Decision.GROUP_SUMMARY,
            NativeFocusRewritePolicy.decide(input(isGroupSummary = true)),
        )
        assertEquals(
            NativeFocusRewritePolicy.Decision.MISSING_TITLE,
            NativeFocusRewritePolicy.decide(input(hasTitle = false)),
        )
        assertEquals(
            NativeFocusRewritePolicy.Decision.MISSING_TEXT,
            NativeFocusRewritePolicy.decide(input(hasText = false)),
        )
        assertEquals(
            NativeFocusRewritePolicy.Decision.MISSING_CHANNEL,
            NativeFocusRewritePolicy.decide(input(hasChannel = false)),
        )
        assertEquals(
            NativeFocusRewritePolicy.Decision.COLORIZED,
            NativeFocusRewritePolicy.decide(input(colorized = true)),
        )
    }

    // Platform capability is not an admission input: the rewriter keeps rebuilding on platforms
    // without live-update rendering and degrades the style itself (see
    // NativeFocusNotificationRewriter.resolveExecutionPlan).

    private fun input(
        enabled: Boolean = true,
        hasFocusPayload: Boolean = true,
        isMiui: Boolean = false,
        hasCustomViews: Boolean = false,
        isGroupSummary: Boolean = false,
        hasTitle: Boolean = true,
        hasText: Boolean = true,
        hasChannel: Boolean = true,
        colorized: Boolean = false,
    ) = NativeFocusRewritePolicy.Input(
        enabled = enabled,
        hasFocusPayload = hasFocusPayload,
        isMiui = isMiui,
        hasCustomViews = hasCustomViews,
        isGroupSummary = isGroupSummary,
        hasTitle = hasTitle,
        hasText = hasText,
        hasChannel = hasChannel,
        colorized = colorized,
    )
}
