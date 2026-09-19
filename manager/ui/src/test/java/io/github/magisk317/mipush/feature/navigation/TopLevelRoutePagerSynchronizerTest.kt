package io.github.magisk317.mipush.feature.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Regression cover for the route -> pager bridge.
 *
 * A tab tap moves the pager first and lets the route controller catch up afterwards. Everything
 * here exists because `targetPageFor` used to read that gap as a real back navigation: the tap
 * animated the pager onto the tapped page, the follow-up lookup still saw the route the user had
 * just left, and the pager was bounced straight back while `routePagerReconciled` went false and
 * deadlocked the reverse bridge.
 */
class TopLevelRoutePagerSynchronizerTest {

    @Test
    fun `a tab tap is not undone by the route the user just left`() {
        val synchronizer = TopLevelRoutePagerSynchronizer()
        synchronizer.notifyUserIntent(TopLevelPage.APPLICATIONS.index)

        // The animation finished (pager on Applications) but the route controller has not published
        // the navigation yet, so the lookup still reads Overview. It must not command the pager.
        assertNull(
            synchronizer.targetPageFor(
                route = AppDestinations.Overview.ROUTE,
                currentPage = TopLevelPage.APPLICATIONS.index,
                isNavigating = false,
            ),
        )
    }

    @Test
    fun `the intent is released once the route catches up and real navigation works again`() {
        val synchronizer = TopLevelRoutePagerSynchronizer()
        synchronizer.notifyUserIntent(TopLevelPage.APPLICATIONS.index)

        // Route caught up while the pager was still animating: intent fulfilled, no pager command.
        assertNull(
            synchronizer.targetPageFor(
                route = AppDestinations.AppsList.ROUTE,
                currentPage = TopLevelPage.OVERVIEW.index,
                isNavigating = true,
            ),
        )

        // The guard is gone, so a genuine back navigation to Overview still drives the pager.
        assertEquals(
            TopLevelPage.OVERVIEW.index,
            synchronizer.targetPageFor(
                route = AppDestinations.Overview.ROUTE,
                currentPage = TopLevelPage.APPLICATIONS.index,
                isNavigating = false,
            ),
        )
    }

    @Test
    fun `a later tap supersedes the intent it replaces`() {
        val synchronizer = TopLevelRoutePagerSynchronizer()
        synchronizer.notifyUserIntent(TopLevelPage.APPLICATIONS.index)
        synchronizer.notifyUserIntent(TopLevelPage.EVENTS.index)

        // The intermediate route is now stale too: only the newest tap may be honoured.
        assertNull(
            synchronizer.targetPageFor(
                route = AppDestinations.AppsList.ROUTE,
                currentPage = TopLevelPage.OVERVIEW.index,
                isNavigating = false,
            ),
        )
    }

    @Test
    fun `a genuine route change still drives the pager`() {
        val synchronizer = TopLevelRoutePagerSynchronizer()

        assertEquals(
            TopLevelPage.EVENTS.index,
            synchronizer.targetPageFor(
                route = AppDestinations.EventsList.ROUTE,
                currentPage = TopLevelPage.OVERVIEW.index,
                isNavigating = false,
            ),
        )
        // Already published: the same route must not re-issue the command on every recomposition.
        assertNull(
            synchronizer.targetPageFor(
                route = AppDestinations.EventsList.ROUTE,
                currentPage = TopLevelPage.OVERVIEW.index,
                isNavigating = false,
            ),
        )
    }

    @Test
    fun `an in-flight animation is never synchronized`() {
        val synchronizer = TopLevelRoutePagerSynchronizer()

        assertNull(
            synchronizer.targetPageFor(
                route = AppDestinations.EventsList.ROUTE,
                currentPage = TopLevelPage.OVERVIEW.index,
                isNavigating = true,
            ),
        )
    }

    @Test
    fun `secondary settings routes resolve to the settings tab`() {
        val synchronizer = TopLevelRoutePagerSynchronizer()

        assertEquals(
            TopLevelPage.SETTINGS.index,
            synchronizer.targetPageFor(
                route = AppDestinations.ThemeSettings.ROUTE,
                currentPage = TopLevelPage.OVERVIEW.index,
                isNavigating = false,
            ),
        )
    }

    @Test
    fun `routes outside the pager never move it`() {
        val synchronizer = TopLevelRoutePagerSynchronizer()

        assertNull(synchronizer.targetPageFor(route = null, currentPage = 0, isNavigating = false))
        assertNull(synchronizer.targetPageFor(route = "not_a_tab", currentPage = 0, isNavigating = false))
    }

    @Test
    fun `reconciliation is reported only when route and pager agree`() {
        val synchronizer = TopLevelRoutePagerSynchronizer()

        assertEquals(
            true,
            synchronizer.isReconciled(
                route = AppDestinations.AppsList.ROUTE,
                currentPage = TopLevelPage.APPLICATIONS.index,
                isNavigating = false,
            ),
        )
        assertEquals(
            false,
            synchronizer.isReconciled(
                route = AppDestinations.AppsList.ROUTE,
                currentPage = TopLevelPage.OVERVIEW.index,
                isNavigating = false,
            ),
        )
        assertEquals(
            false,
            synchronizer.isReconciled(
                route = AppDestinations.AppsList.ROUTE,
                currentPage = TopLevelPage.APPLICATIONS.index,
                isNavigating = true,
            ),
        )
    }
}
