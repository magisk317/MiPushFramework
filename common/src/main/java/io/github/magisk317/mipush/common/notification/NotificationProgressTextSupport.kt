package io.github.magisk317.mipush.common.notification

import io.github.magisk317.mipush.runtime.store.kmp.NotificationProgressTextPolicy

/** Android-side adapter delegating to the platform-neutral progress text parser. */
object NotificationProgressTextSupport {
    const val NO_PROGRESS = NotificationProgressTextPolicy.NO_PROGRESS

    @JvmStatic
    fun extractProgressPercent(text: CharSequence?): Int =
        NotificationProgressTextPolicy.extractProgressPercent(text?.toString())

    @JvmStatic
    fun extractCountdownMillis(title: CharSequence?, content: CharSequence?): Long =
        NotificationProgressTextPolicy.extractCountdownMillis(title?.toString(), content?.toString())

    @JvmStatic
    fun extractDurationMillis(text: CharSequence?): Long =
        NotificationProgressTextPolicy.extractDurationMillis(text?.toString())

    @JvmStatic
    fun resolveAlertHint(title: CharSequence?, content: CharSequence?): String? =
        NotificationProgressTextPolicy.resolveAlertHint(title?.toString(), content?.toString())

    @JvmStatic
    fun resolveProgressHint(title: CharSequence?, content: CharSequence?): String? =
        NotificationProgressTextPolicy.resolveProgressHint(title?.toString(), content?.toString())
}
