package io.github.magisk317.mipush.service.runtime

/**
 * Independent side effects a notification dispatch may perform.
 *
 * The three phases are isolated from one another: a failure in [wake] or [open] must not stop
 * [notify] from running, and [notify] is never suppressed by a failure in an optional phase.
 */
internal data class NotificationDispatchPlan(
    val wake: Boolean,
    val notify: Boolean,
    val open: Boolean,
) {
    internal companion object {
        /**
         * The conservative default used when the per-package configuration cannot be evaluated.
         *
         * Stock delivers the payload whenever it is valid, so a failed evaluation degrades to
         * "notify without side effects" instead of dropping the notification.
         */
        val NOTIFY_ONLY = NotificationDispatchPlan(wake = false, notify = true, open = false)
    }
}
