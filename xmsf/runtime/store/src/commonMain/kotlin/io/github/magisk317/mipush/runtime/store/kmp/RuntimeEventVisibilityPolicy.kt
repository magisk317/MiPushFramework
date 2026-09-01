package io.github.magisk317.mipush.runtime.store.kmp

/** Default event-list visibility; application activity tracking is independent of this policy. */
object RuntimeEventVisibilityPolicy {
    fun isHiddenByDefault(type: Int, result: Int, currentlyDisabled: Boolean): Boolean =
        type == EventRowType.SendMessage &&
            (result == EventRowResultType.DENY_DISABLED || currentlyDisabled)
}
