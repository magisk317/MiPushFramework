package io.github.magisk317.mipush.manager

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerStatePoliciesTest {
    @Test fun `activation requires settled non animating page`() {
        assertTrue(ManagerStatePolicies.acceptsPage(1, 4, 1, false))
        assertFalse(ManagerStatePolicies.acceptsPage(1, 4, 1, true))
        assertFalse(ManagerStatePolicies.acceptsPage(4, 4, 4, false))
    }
    @Test fun `newest first sorting preserves equal timestamp order`() {
        val values = listOf("old" to 1L, "new" to 2L, "same" to 2L)
        assertEquals(listOf("new", "same", "old"), ManagerStatePolicies.newestFirst(values) { it.second }.map { it.first })
    }
    @Test fun `snapshot policy validates completeness and lifetime`() {
        assertTrue(ManagerStatePolicies.isValidSnapshot(10, 20, 15, true))
        assertFalse(ManagerStatePolicies.isValidSnapshot(10, 20, 15, false))
        assertFalse(ManagerStatePolicies.isValidSnapshot(10, 20, 21, true))
    }

    @Test fun `generic coordinator preserves route and isolates stale work`() {
        val pages = listOf("overview", "events", "settings")
        val coordinator = ManagerPageActivationCoordinator(
            pages = pages,
            routeOf = { it },
            pageForRoute = { route -> pages.firstOrNull { it == route } },
            policies = pages.associateWith { ManagerPageActivationPolicy(it) },
        )
        val first = coordinator.requestNavigation(1, ManagerNavigationInputKind.CLICK)
            as ManagerNavigationRequestResult.Accepted
        var cancelled = 0
        coordinator.onPagerSettled(1, first.token)
        val old = coordinator.beginPageRequest(token = first.token)!!
        coordinator.registerPageWork(old, ManagerPageWorkKind.REFRESH) { cancelled++ }
        coordinator.requestNavigation(2, ManagerNavigationInputKind.SWIPE)

        assertEquals(1, cancelled)
        assertEquals(2, coordinator.state.selectedPage)
        assertEquals("events", old.route)
    }
}
