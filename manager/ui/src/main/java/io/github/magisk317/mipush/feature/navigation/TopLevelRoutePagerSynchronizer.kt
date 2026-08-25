package io.github.magisk317.mipush.feature.navigation

/**
 * Keeps route-controller updates flowing into the pager without creating a reverse feedback
 * loop. Only an effective top-level page change can produce a pager command; deep-route changes
 * within the same top-level branch are intentionally ignored by the pager bridge.
 */
class TopLevelRoutePagerSynchronizer {
    private var lastEffectivePage: Int? = null

    /**
     * Returns the pager target for a new route, or null when no pager synchronization is needed.
     * The effective route is remembered only after it can be handled, so a route change that
     * arrives during an existing pager animation is retried when that animation settles.
     */
    fun targetPageFor(
        route: String?,
        currentPage: Int,
        isNavigating: Boolean,
    ): Int? {
        val targetPage = route?.let(TopLevelPage::fromRoute)?.index ?: return null
        if (isNavigating) return null
        if (targetPage == lastEffectivePage) return null
        lastEffectivePage = targetPage
        if (targetPage == currentPage) return null
        return targetPage
    }

    /** True only after restored route state and pager state have converged on the same page. */
    fun isReconciled(
        route: String?,
        currentPage: Int,
        isNavigating: Boolean,
    ): Boolean {
        if (isNavigating) return false
        return route?.let(TopLevelPage::fromRoute)?.index == currentPage
    }
}
