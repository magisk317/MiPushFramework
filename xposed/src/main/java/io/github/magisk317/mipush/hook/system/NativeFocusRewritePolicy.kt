package io.github.magisk317.mipush.hook.system

/** Pure admission and promotion checks for the system_server native-focus adapter. */
internal object NativeFocusRewritePolicy {
    const val IMPORTANCE_MIN = 1
    const val IMPORTANCE_DEFAULT = 3
    const val FLAG_ONGOING_EVENT = 0x00000002
    const val FLAG_GROUP_SUMMARY = 0x00000200

    enum class Decision {
        REWRITE,
        DISABLED,
        NATIVE_PLATFORM,
        NOT_FOCUS,
        CUSTOM_VIEWS,
        GROUP_SUMMARY,
        MISSING_CHANNEL,
        MISSING_TITLE,
        MISSING_TEXT,
        COLORIZED,
    }

    data class Input(
        val enabled: Boolean,
        val hasFocusPayload: Boolean,
        val isMiui: Boolean,
        val hasCustomViews: Boolean,
        val isGroupSummary: Boolean,
        val hasTitle: Boolean,
        val hasText: Boolean,
        val hasChannel: Boolean,
        val colorized: Boolean,
    )

    fun decide(input: Input): Decision = when {
        !input.enabled -> Decision.DISABLED
        input.isMiui -> Decision.NATIVE_PLATFORM
        !input.hasFocusPayload -> Decision.NOT_FOCUS
        input.hasCustomViews -> Decision.CUSTOM_VIEWS
        input.isGroupSummary -> Decision.GROUP_SUMMARY
        !input.hasChannel -> Decision.MISSING_CHANNEL
        !input.hasTitle -> Decision.MISSING_TITLE
        !input.hasText -> Decision.MISSING_TEXT
        input.colorized -> Decision.COLORIZED
        else -> Decision.REWRITE
    }

    fun shouldSetPromotedFlag(
        hasTrustedRewriteGrant: Boolean,
        requestPromoted: Boolean,
        ongoing: Boolean,
        hasTitle: Boolean,
        hasPromotableStyle: Boolean,
        isGroupSummary: Boolean,
        hasCustomViews: Boolean,
        colorized: Boolean,
        channelImportance: Int,
    ): Boolean =
        hasTrustedRewriteGrant &&
            requestPromoted &&
            ongoing &&
            hasTitle &&
            hasPromotableStyle &&
            !isGroupSummary &&
            !hasCustomViews &&
            !colorized &&
            channelImportance > IMPORTANCE_MIN
}
