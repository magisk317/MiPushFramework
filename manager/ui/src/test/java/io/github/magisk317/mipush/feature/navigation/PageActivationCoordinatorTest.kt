package io.github.magisk317.mipush.feature.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageActivationCoordinatorTest {
    @Test
    fun acceptedRequestsCreateOneTokenAndInvalidTargetsPreserveState() {
        val coordinator = PageActivationCoordinator(clockNanos = { 42L })

        val accepted = coordinator.requestNavigation(2, NavigationInputKind.SWIPE)
        assertTrue(accepted is NavigationRequestResult.Accepted)
        val token = (accepted as NavigationRequestResult.Accepted).token
        assertEquals(1L, token.sequence)
        assertEquals(NavigationInputKind.SWIPE, token.inputKind)
        assertEquals(2, coordinator.state.selectedPage)

        val rejected = coordinator.requestNavigation(9, NavigationInputKind.CLICK)
        assertTrue(rejected is NavigationRequestResult.Rejected)
        assertEquals(2, coordinator.state.selectedPage)
        assertEquals(token, coordinator.token)
    }

    @Test
    fun invalidOrStaleSettlesDoNotChangeLastValidState() {
        val coordinator = PageActivationCoordinator()
        val first = coordinator.requestNavigation(1, NavigationInputKind.CLICK) as NavigationRequestResult.Accepted
        assertFalse(coordinator.onPagerSettled(8, first.token))
        assertEquals(0, coordinator.state.settledPage)
        assertFalse(coordinator.onPagerSettled(2, first.token))
        assertEquals(0, coordinator.state.settledPage)

        assertTrue(coordinator.onPagerSettled(1, first.token))
        val second = coordinator.requestNavigation(2, NavigationInputKind.CLICK) as NavigationRequestResult.Accepted
        assertFalse(coordinator.onPagerSettled(1, first.token))
        assertEquals(1, coordinator.state.settledPage)
        assertTrue(coordinator.onPagerSettled(2, second.token))
        assertEquals(AppDestinations.EventsList.ROUTE, coordinator.state.route)
    }

    @Test
    fun allEntryPointsUseTheSameTokenAndRouteMapping() {
        val coordinator = PageActivationCoordinator()

        val restore = coordinator.requestNavigation(3, NavigationInputKind.RESTORE) as NavigationRequestResult.Accepted
        assertEquals(NavigationInputKind.RESTORE, restore.token.inputKind)
        coordinator.onPagerSettled(3, restore.token)
        val deepRoute = coordinator.requestFromRoute(AppDestinations.ConnectionStatus.ROUTE)
        assertTrue(deepRoute is NavigationRequestResult.Accepted)
        assertEquals(NavigationInputKind.DEEP_ROUTE, (deepRoute as NavigationRequestResult.Accepted).token.inputKind)
        assertEquals(2L, deepRoute.token.sequence)
        assertEquals(3, coordinator.state.selectedPage)
    }

    @Test
    fun routeChangesAreAppliedOnceForTheSameEffectiveState() {
        val coordinator = PageActivationCoordinator()
        val first = coordinator.onRouteChanged(AppDestinations.AppsList.ROUTE)
        assertNotNull(first)
        assertEquals(AppDestinations.AppsList.ROUTE, coordinator.state.route)
        assertNull(coordinator.onRouteChanged(AppDestinations.AppsList.ROUTE))
        assertEquals(1L, coordinator.token?.sequence)
    }

    @Test
    fun clickGestureRestoreAndDeepRouteEntriesShareTheSameTokenLifecycle() {
        val coordinator = PageActivationCoordinator(clockNanos = { 7L })
        val inputs = listOf(
            NavigationInputKind.CLICK to 1,
            NavigationInputKind.SWIPE to 2,
            NavigationInputKind.RESTORE to 3,
            NavigationInputKind.DEEP_ROUTE to 0,
        )

        inputs.forEachIndexed { index, (input, page) ->
            val result = coordinator.requestNavigation(page, input)
            assertTrue(result is NavigationRequestResult.Accepted)
            val token = (result as NavigationRequestResult.Accepted).token
            assertEquals(index + 1L, token.sequence)
            assertEquals(input, token.inputKind)
            assertEquals(page, token.targetPage)
            assertTrue(coordinator.onPagerSettled(page, token))
            assertEquals(TopLevelPage.fromIndex(page)!!.route, coordinator.state.route)
        }
    }

    @Test
    fun repeatedTopLevelRouteChangesDoNotCreateDuplicateEffectiveEntries() {
        val coordinator = PageActivationCoordinator()

        assertNotNull(coordinator.onRouteChanged(AppDestinations.AppsList.ROUTE))
        assertNull(coordinator.onRouteChanged(AppDestinations.AppsList.ROUTE))
        assertNull(coordinator.onRouteChanged(AppDestinations.AppDetails.route("com.example.app")))
        assertEquals(1L, coordinator.token?.sequence)
        assertEquals(AppDestinations.AppsList.ROUTE, coordinator.state.route)
    }

    @Test
    fun activationRequiresSettledPageAndRespectsDataPolicy() {
        val policies = PageActivationPolicy.defaults() + (
            TopLevelPage.EVENTS to PageActivationPolicy(
                page = TopLevelPage.EVENTS,
                dataPolicy = PageDataPolicy.CACHE_ONLY,
            )
        )
        val coordinator = PageActivationCoordinator(policies)
        val inactive = coordinator.activationFor(2)
        assertFalse(inactive.isActive)
        assertFalse(inactive.allowsBusinessRead)
        assertFalse(inactive.allowsRemoteCall)

        val request = coordinator.requestNavigation(2, NavigationInputKind.CLICK) as NavigationRequestResult.Accepted
        val animating = coordinator.activationFor(2)
        assertFalse(animating.isActive)
        assertFalse(animating.allowsBusinessRead)
        assertFalse(animating.allowsRemoteCall)

        assertTrue(coordinator.onPagerSettled(2, request.token))
        val active = coordinator.activationFor(2)
        assertTrue(active.isActive)
        assertTrue(active.allowsBusinessRead)
        assertFalse(active.allowsRemoteCall)
    }

    @Test
    fun noReadsPolicyCanBeActiveWithoutStartingReadsOrRemoteCalls() {
        val policies = PageActivationPolicy.defaults() + (
            TopLevelPage.SETTINGS to PageActivationPolicy(
                page = TopLevelPage.SETTINGS,
                dataPolicy = PageDataPolicy.NO_READS,
            )
        )
        val coordinator = PageActivationCoordinator(policies)
        val request = coordinator.requestNavigation(3, NavigationInputKind.CLICK) as NavigationRequestResult.Accepted
        assertTrue(coordinator.onPagerSettled(3, request.token))

        val activation = coordinator.activationFor(3)
        assertTrue(activation.isActive)
        assertFalse(activation.allowsBusinessRead)
        assertFalse(activation.allowsRemoteCall)
    }

    @Test
    fun adjacentPrecompositionOnlyExposesLightweightShells() {
        val coordinator = PageActivationCoordinator(adjacentPrecompositionEnabled = true)
        val request = coordinator.requestNavigation(2, NavigationInputKind.CLICK) as NavigationRequestResult.Accepted

        assertEquals(setOf(TopLevelPage.APPLICATIONS, TopLevelPage.SETTINGS), coordinator.adjacentShellPages)
        assertTrue(coordinator.shouldComposeShell(1))
        assertTrue(coordinator.shouldComposeShell(3))
        assertFalse(coordinator.shouldComposeShell(2))
        assertFalse(coordinator.activationFor(1).allowsBusinessRead)
        assertFalse(coordinator.activationFor(1).allowsRemoteCall)
        assertTrue(coordinator.onPagerSettled(2, request.token))
        assertFalse(coordinator.shouldComposeShell(2))
    }

    @Test
    fun adjacentShellCannotStartPageRequestOrRemoteWork() {
        val coordinator = PageActivationCoordinator(adjacentPrecompositionEnabled = true)
        val request = coordinator.requestNavigation(2, NavigationInputKind.CLICK) as NavigationRequestResult.Accepted

        assertTrue(coordinator.shouldComposeShell(1))
        assertFalse(coordinator.activationFor(1).isActive)
        assertFalse(coordinator.activationFor(1).allowsRemoteCall)
        assertNull(coordinator.beginPageRequest(token = request.token, route = TopLevelPage.APPLICATIONS.route))

        assertTrue(coordinator.onPagerSettled(2, request.token))
        assertFalse(coordinator.shouldComposeShell(2))
    }

    @Test
    fun onlySettledActivePagesCanStartWork() {
        val coordinator = PageActivationCoordinator()
        val request = coordinator.requestNavigation(1, NavigationInputKind.CLICK) as NavigationRequestResult.Accepted
        assertNull(coordinator.beginPageRequest(token = request.token))

        assertTrue(coordinator.onPagerSettled(1, request.token))
        assertNotNull(coordinator.beginPageRequest(query = "old", token = request.token))
    }

    @Test
    fun newerQueryGenerationCancelsOlderCancellableWorkOnly() {
        val coordinator = PageActivationCoordinator()
        val request = coordinator.requestNavigation(1, NavigationInputKind.CLICK) as NavigationRequestResult.Accepted
        assertTrue(coordinator.onPagerSettled(1, request.token))

        val firstScope = coordinator.beginPageRequest(query = "old", token = request.token)!!
        var cancelled = 0
        val first = coordinator.registerPageWork(firstScope, PageWorkKind.FIRST_BUSINESS_READ) { cancelled++ }!!
        val secondScope = coordinator.beginPageRequest(query = "new", token = request.token)!!

        assertEquals(1, cancelled)
        assertTrue(first.isCancelled)
        assertFalse(coordinator.isCurrent(firstScope))
        assertTrue(coordinator.isCurrent(secondScope))

        var newerCancelled = 0
        val second = coordinator.registerPageWork(secondScope, PageWorkKind.REFRESH) { newerCancelled++ }!!
        val next = coordinator.requestNavigation(2, NavigationInputKind.SWIPE) as NavigationRequestResult.Accepted
        assertEquals(1, newerCancelled)
        assertTrue(second.isCancelled)
        assertEquals(next.token, coordinator.token)
    }

    @Test
    fun nonCancellableStaleWorkIsNotCancelledButCannotPublish() {
        val coordinator = PageActivationCoordinator()
        val request = coordinator.requestNavigation(1, NavigationInputKind.CLICK) as NavigationRequestResult.Accepted
        assertTrue(coordinator.onPagerSettled(1, request.token))
        val scope = coordinator.beginPageRequest(token = request.token)!!
        var cancelled = 0
        val work = coordinator.registerPageWork(scope, PageWorkKind.REFRESH, cancellable = false) { cancelled++ }!!

        coordinator.beginPageRequest(query = "changed", token = request.token)

        assertEquals(0, cancelled)
        assertFalse(work.isCancelled)
        assertFalse(coordinator.isCurrent(scope))
    }

    @Test
    fun routePagerBridgeSynchronizesOnlyEffectiveTopLevelChanges() {
        val bridge = TopLevelRoutePagerSynchronizer()

        assertEquals(1, bridge.targetPageFor(AppDestinations.AppsList.ROUTE, currentPage = 0, isNavigating = false))
        assertNull(bridge.targetPageFor(AppDestinations.AppDetails.route("com.example.app"), currentPage = 1, isNavigating = false))
        assertNull(bridge.targetPageFor(AppDestinations.AppsList.ROUTE, currentPage = 1, isNavigating = false))
        assertEquals(2, bridge.targetPageFor(AppDestinations.EventsList.ROUTE, currentPage = 1, isNavigating = false))
    }

    @Test
    fun routePagerBridgeDefersRouteChangeWhilePagerIsNavigating() {
        val bridge = TopLevelRoutePagerSynchronizer()

        assertNull(bridge.targetPageFor(AppDestinations.AppsList.ROUTE, currentPage = 0, isNavigating = true))
        assertEquals(1, bridge.targetPageFor(AppDestinations.AppsList.ROUTE, currentPage = 0, isNavigating = false))
    }

    @Test
    fun routePagerBridgeRequiresEveryRestoredRouteAndPagePairToConverge() {
        TopLevelPage.entries.forEach { routePage ->
            TopLevelPage.entries.forEach { pagerPage ->
                val bridge = TopLevelRoutePagerSynchronizer()
                assertEquals(
                    routePage == pagerPage,
                    bridge.isReconciled(
                        route = routePage.route,
                        currentPage = pagerPage.index,
                        isNavigating = false,
                    ),
                )
                assertFalse(
                    bridge.isReconciled(
                        route = routePage.route,
                        currentPage = pagerPage.index,
                        isNavigating = true,
                    ),
                )
            }
        }
    }
}
