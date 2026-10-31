package io.github.magisk317.mipush.hook.systemui

/**
 * Pure decision core for the per-app notification count-limit exemption; no Android or Xposed
 * dependencies so the contract stays unit-testable.
 *
 * HyperOS's CountLimitCoordinator trims each package to its most recent notifications by calling
 * [NotifCollection.dismissNotifications] with the eviction victims. The hook lets that call
 * through unless the caller is the coordinator itself AND every victim belongs to a package the
 * user exempted from the manager's app detail page. Any other dismissal path (user swipes, media
 * replacement, bubbles) must keep the stock behavior, which is what the caller check guarantees.
 */
object NotificationCountLimitPolicy {
    const val COUNT_LIMIT_COORDINATOR_CLASS =
        "com.android.systemui.statusbar.notification.collection.coordinator.CountLimitCoordinator"

    /**
     * True when the dismissal currently on the stack was initiated by CountLimitCoordinator.
     * The coordinator class name is unobfuscated across the archived SystemUI builds, unlike the
     * R8 synthetic lambda numbering inside it.
     */
    fun isCoordinatorInitiated(stackClassNames: List<String>): Boolean =
        stackClassNames.any {
            it == COUNT_LIMIT_COORDINATOR_CLASS ||
                it.startsWith("${COUNT_LIMIT_COORDINATOR_CLASS}$")
        }

    /** Every victim must be exempt: the coordinator batches one eviction per call, so a mixed
     * batch would only be possible on a future behavior change, and then the stock path wins. */
    fun isAllTargetsExempt(
        targets: List<CountLimitTarget>,
        isExempt: (CountLimitTarget) -> Boolean,
    ): Boolean = targets.isNotEmpty() && targets.all(isExempt)
}

data class CountLimitTarget(
    val packageName: String,
    val userId: Int,
)
