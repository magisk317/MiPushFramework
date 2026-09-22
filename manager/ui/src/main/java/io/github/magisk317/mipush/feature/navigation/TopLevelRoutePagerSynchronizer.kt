package io.github.magisk317.mipush.feature.navigation

/**
 * Keeps route-controller updates flowing into the pager without creating a reverse feedback
 * loop. Only an effective top-level page change can produce a pager command; deep-route changes
 * within the same top-level branch are intentionally ignored by the pager bridge.
 */
class TopLevelRoutePagerSynchronizer {
    private var lastEffectivePage: Int? = null

    /**
     * Top-level page a tab tap asked for, until the route controller catches up with it. While
     * this is set, a route -> pager lookup that points at any other page is still reading the
     * route the user just left, so it is a stale lookup and not a real back navigation. Ignoring
     * it is what keeps the pager from bouncing back to the tab the tap came from.
     */
    private var pendingUserIntent: Int? = null

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

        // Evaluated before the animation guard on purpose: with navigation issued on the tap the
        // route normally catches up while the animation is still running, and an intent that is
        // only released on a settled lookup would stay armed until some unrelated change happened
        // to re-run the caller's effect. Releasing early is safe because the animation guard below
        // still suppresses any pager command for this pass.
        val pending = pendingUserIntent
        if (pending != null) {
            // Route has not caught up with the tap yet: report no synchronization at all, which
            // is the whole point of remembering the intent.
            if (targetPage != pending) return null
            // Route caught up: the intent is fulfilled and normal synchronization resumes from
            // the page it asked for.
            pendingUserIntent = null
            lastEffectivePage = pending
            return null
        }

        if (isNavigating) return null

        if (targetPage == lastEffectivePage) return null
        lastEffectivePage = targetPage
        if (targetPage == currentPage) return null
        return targetPage
    }

    /**
     * Called by a tab tap, before the pager starts moving. Records the tapped page as a user
     * intent so a route lookup that still reads the previous route cannot produce the backward
     * bounce this class documents as its reason to exist. The intent is released as soon as the
     * route catches up; navigation on tap is what bounds that window to a single frame.
     */
    fun notifyUserIntent(page: Int) {
        pendingUserIntent = page
        lastEffectivePage = page
    }

    /**
     * Called by route-driven pager synchronization. Keeps [lastEffectivePage] in sync with the
     * position the pager is being moved to.
     */
    fun notifyTargetPage(page: Int) {
        lastEffectivePage = page
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
