package io.github.magisk317.mipush.feature.navigation

import io.github.magisk317.mipush.feature.main.subpage.EventInfoForDisplay
import io.github.magisk317.mipush.manager.api.ManagerEventPageDto
import io.github.magisk317.mipush.manager.api.ManagerEventSummaryDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import io.github.magisk317.mipush.manager.events.EventListRequest
import io.github.magisk317.mipush.manager.events.EventReadResult
import io.github.magisk317.mipush.manager.events.RemoteEventListSource
import io.github.magisk317.mipush.manager.events.mergeEventSnapshots
import io.github.magisk317.mipush.manager.telemetry.PagePerformanceHandle
import io.github.magisk317.mipush.manager.telemetry.TransitionPerformanceRecorder
import io.github.magisk317.mipush.manager.telemetry.TransitionStage
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration coverage for the manager's navigation-to-page lifecycle boundary.
 *
 * These tests deliberately exercise the manager-owned coordinator, route bridge, page performance
 * handle, and event remote/cache helpers without depending on a device or changing runtime APIs.
 */
class ManagerNavigationPageIntegrationTest {
    @Test
    fun `settled page activates page and refresh is recorded after first meaningful frame`() {
        val coordinator = PageActivationCoordinator()
        val recorder = TransitionPerformanceRecorder()
        val request = coordinator.requestNavigation(2, NavigationInputKind.CLICK)
            as NavigationRequestResult.Accepted
        val telemetryToken = recorder.begin(
            sourcePage = request.token.sourcePage,
            targetPage = request.token.targetPage,
            inputKind = io.github.magisk317.mipush.manager.telemetry.TransitionInputKind.CLICK,
            isCold = false,
            startedAtNanos = request.token.startedAtNanos,
        )
        val handle = PagePerformanceHandle(telemetryToken, recorder)

        assertFalse(coordinator.activationFor(2).isActive)
        assertTrue(coordinator.onPagerSettled(2, request.token))
        assertTrue(coordinator.activationFor(2).isActive)

        handle.pagerSettled(2, 10)
        handle.firstComposition(20)
        handle.firstMeaningfulFrame(30)
        handle.cachePresented("memory", timestampNanos = 31)
        handle.dataReady(40)

        val snapshot = recorder.snapshot(telemetryToken)!!
        assertEquals(30L, snapshot.completedStages[TransitionStage.FIRST_MEANINGFUL_FRAME])
        assertEquals(31L, snapshot.completedStages[TransitionStage.CACHE_PRESENTED])
        assertEquals(40L, snapshot.completedStages[TransitionStage.DATA_READY])
        assertTrue(snapshot.finished)
    }

    @Test
    fun `leaving a page cancels its cancellable work but preserves newer page work`() {
        val coordinator = PageActivationCoordinator()
        val first = coordinator.requestNavigation(1, NavigationInputKind.CLICK)
            as NavigationRequestResult.Accepted
        assertTrue(coordinator.onPagerSettled(1, first.token))
        val oldScope = coordinator.beginPageRequest(query = "old", token = first.token)!!
        var oldCancelled = 0
        val oldWork = coordinator.registerPageWork(oldScope, PageWorkKind.REFRESH) { oldCancelled++ }!!

        val second = coordinator.requestNavigation(2, NavigationInputKind.SWIPE)
            as NavigationRequestResult.Accepted
        assertEquals(1, oldCancelled)
        assertTrue(oldWork.isCancelled)

        assertTrue(coordinator.onPagerSettled(2, second.token))
        val newScope = coordinator.beginPageRequest(token = second.token)!!
        var newCancelled = 0
        val newWork = coordinator.registerPageWork(newScope, PageWorkKind.FIRST_BUSINESS_READ) { newCancelled++ }!!
        assertFalse(newWork.isCancelled)
        assertEquals(0, newCancelled)
    }

    @Test
    fun `stale page result cannot publish after a newer navigation`() {
        val coordinator = PageActivationCoordinator()
        val first = coordinator.requestNavigation(1, NavigationInputKind.CLICK)
            as NavigationRequestResult.Accepted
        assertTrue(coordinator.onPagerSettled(1, first.token))
        val oldScope = coordinator.beginPageRequest(query = "old", token = first.token)!!
        val second = coordinator.requestNavigation(2, NavigationInputKind.CLICK)
            as NavigationRequestResult.Accepted

        val published = mutableListOf<String>()
        if (coordinator.isCurrent(oldScope)) published += "old-result"
        assertFalse(coordinator.isCurrent(oldScope))

        assertTrue(coordinator.onPagerSettled(2, second.token))
        val newScope = coordinator.beginPageRequest(query = "new", token = second.token)!!
        if (coordinator.isCurrent(newScope)) published += "new-result"
        assertEquals(listOf("new-result"), published)
    }

    @Test
    fun `event cache merge replaces duplicate and keeps newest events first`() {
        val existing = listOf(event(id = 1L, time = 100L), event(id = 2L, time = 200L))
        val incoming = listOf(event(id = 1L, time = 300L), event(id = 3L, time = 150L))

        val merged = mergeEventSnapshots(existing, incoming)

        assertEquals(listOf(1L, 2L, 3L), merged.map(EventInfoForDisplay::id))
        assertEquals(300L, merged.first().receiveDate.time)
        assertEquals("event-1", merged.first().title)
    }

    @Test
    fun `runtime unavailable event read recovers without publishing an empty page`() = kotlinx.coroutines.runBlocking {
        var available = false
        val source = RemoteEventListSource(
            pageLoader = {
                if (!available) {
                    ManagerRuntimeResult.Unavailable(ManagerRuntimeAvailability.Disconnected)
                } else {
                    ManagerRuntimeResult.Success(
                        ManagerEventPageDto(
                            items = listOf(summary(id = 7L, userId = 0, time = 700L)),
                        ),
                    )
                }
            },
            userIdProvider = { 0 },
        )

        val unavailable = source.load(EventListRequest())
        assertEquals(EventReadResult.Unavailable(io.github.magisk317.mipush.manager.events.EventReadStatus.DISCONNECTED), unavailable)

        available = true
        val recovered = source.load(EventListRequest())
        assertTrue(recovered is EventReadResult.Available)
        assertEquals(listOf(7L), (recovered as EventReadResult.Available).value.map { it.id })
    }

    @Test
    fun `deep route maps to one top level navigation entry and route pager sync is idempotent`() {
        val coordinator = PageActivationCoordinator()
        val bridge = TopLevelRoutePagerSynchronizer()
        val deepRoute = AppDestinations.EventsList.ROUTE

        val first = coordinator.onRouteChanged(deepRoute)
        assertNotNull(first)
        assertEquals(2, coordinator.state.selectedPage)
        assertEquals(2, bridge.targetPageFor(deepRoute, currentPage = 0, isNavigating = false))
        assertNull(bridge.targetPageFor(deepRoute, currentPage = 2, isNavigating = false))
        assertNull(coordinator.onRouteChanged(deepRoute))
        assertEquals(1L, coordinator.token?.sequence)
    }

    @Test
    fun `deep route branch changes do not create another transition while pager is settling`() {
        val coordinator = PageActivationCoordinator()
        assertNotNull(coordinator.onRouteChanged(AppDestinations.AppsList.ROUTE))

        val detailRoute = AppDestinations.AppDetails.route("com.example.app")
        assertNull(coordinator.onRouteChanged(detailRoute))
        assertEquals(0, coordinator.state.settledPage)
        assertEquals(1L, coordinator.token?.sequence)
    }

    private fun event(id: Long, time: Long): EventInfoForDisplay = EventInfoForDisplay(
        id = id,
        packageName = "com.example",
        configOptions = emptySet(),
        channel = "default",
        receiveDate = Date(time),
        title = "event-$id",
        content = "content",
    )

    private fun summary(id: Long, userId: Int, time: Long) = ManagerEventSummaryDto(
        id = id,
        userId = userId,
        packageName = "com.example",
        channel = "default",
        receiveDateMs = time,
        title = "event-$id",
        content = "content",
    )
}
