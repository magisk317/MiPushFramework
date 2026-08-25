package io.github.magisk317.mipush.manager.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransitionPerformanceRecorderTest {
    @Test
    fun `begin classifies navigation cohorts`() {
        val recorder = TransitionPerformanceRecorder()
        assertEquals(NavigationCohort.COLD_FIRST_NAVIGATION, recorder.begin(0, 1, TransitionInputKind.CLICK, true, 1).cohort)
        assertEquals(NavigationCohort.RETURN_OVERVIEW, recorder.begin(2, 0, TransitionInputKind.SWIPE, false, 2).cohort)
        assertEquals(NavigationCohort.WARM_ROUND_TRIP, recorder.begin(1, 2, TransitionInputKind.CLICK, false, 3).cohort)
    }

    @Test
    fun `duplicate stages retain first completion and out of order stages are ignored`() {
        val recorder = TransitionPerformanceRecorder()
        val token = recorder.begin(0, 1, TransitionInputKind.CLICK, false, 10)
        recorder.markPagerSettled(token, 1, 20)
        recorder.markPagerSettled(token, 1, 30)
        recorder.markFirstComposition(token, 19)
        recorder.markFirstComposition(token, 25)
        recorder.markFirstMeaningfulFrame(token, 40)
        recorder.markDataReady(token, 50)
        val snapshot = recorder.snapshot(token)!!
        assertEquals(20, snapshot.completedStages[TransitionStage.PAGER_SETTLED])
        assertEquals(25, snapshot.completedStages[TransitionStage.FIRST_COMPOSITION])
        assertEquals(40, snapshot.completedStages[TransitionStage.FIRST_MEANINGFUL_FRAME])
        assertTrue(snapshot.finished)
        assertEquals(1, recorder.events().count { it.stage == TransitionStage.PAGER_SETTLED })
    }

    @Test
    fun `invalid settle and stale token become diagnostics without changing current token`() {
        val recorder = TransitionPerformanceRecorder()
        val old = recorder.begin(0, 1, TransitionInputKind.CLICK, false, 1)
        val current = recorder.begin(0, 2, TransitionInputKind.CLICK, false, 2)
        recorder.markPagerSettled(current, 9, 3)
        recorder.markPagerSettled(old, 1, 4)
        assertTrue(recorder.snapshot(current)!!.completedStages.isEmpty())
        assertEquals(1, recorder.events().count { it.kind == TransitionEventKind.STALE_DIAGNOSTIC })
    }

    @Test
    fun `finished is emitted exactly once after settled frame and cache`() {
        val recorder = TransitionPerformanceRecorder()
        val token = recorder.begin(0, 1, TransitionInputKind.CLICK, false, 1)
        recorder.markPagerSettled(token, 1, 2)
        recorder.markFirstMeaningfulFrame(token, 3)
        recorder.markCacheResult(token, "memory", 12, 4)
        recorder.finish(token, 5)
        assertEquals(1, recorder.events().count { it.kind == TransitionEventKind.FINISHED })
        assertTrue(recorder.snapshot(token)!!.finished)
    }

    @Test
    fun `buffer remains bounded and unavailable binder changes cohort`() {
        val recorder = TransitionPerformanceRecorder(maxBufferedEvents = 3)
        val token = recorder.begin(0, 1, TransitionInputKind.CLICK, false, 1)
        recorder.markBinderCall(token, "events", "VISIBLE_PAGE", 8, "unavailable", "unavailable", 2)
        recorder.markPagerSettled(token, 1, 3)
        recorder.markFirstComposition(token, 4)
        recorder.markFirstMeaningfulFrame(token, 5)
        assertEquals(NavigationCohort.BINDER_UNAVAILABLE, token.cohort)
        assertEquals(3, recorder.events().size)
    }

    @Test
    fun `structured navigation events expose only approved manager fields`() {
        val recorder = TransitionPerformanceRecorder(process = "manager", buildVariant = "debug")
        val token = recorder.begin(0, 1, TransitionInputKind.CLICK, false, 1)
        recorder.markBinderCall(token, "event_page", "VISIBLE_PAGE", 12, "success", "available", 2)
        val event = recorder.structuredEvents().single()
        assertTrue(event.fields().keys.all { it in ManagerNavigationTelemetryEvent.APPROVED_FIELDS })
        assertEquals(ManagerNavigationTelemetryEvent.APPROVED_FIELDS - setOf("stage", "cache_source"), event.fields().keys)
        assertEquals("manager", event.fields()["process"])
        assertEquals("debug", event.fields()["build_variant"])
    }

    @Test
    fun `structured event text is bounded and sensitive fields have no representation`() {
        val recorder = TransitionPerformanceRecorder(maxTextLength = 5, process = "manager-process-that-is-too-long", buildVariant = "debug-variant-that-is-too-long")
        val token = recorder.begin(0, 1, TransitionInputKind.CLICK, false, 1)
        recorder.markBinderCall(token, "operation-with-payload-package.name-query=secret-user-42", "priority-too-long", 1, "result-too-long", "available-too-long", 2)
        val fields = recorder.structuredEvents().single().fields()
        assertTrue(fields.values.filterIsInstance<String>().all { it.length <= 5 })
        assertEquals("trans", fields["token"])
        assertEquals("manag", fields["process"])
        assertEquals("debug", fields["build_variant"])
    }

    @Test
    fun `page performance handle records requested through data ready exactly once`() {
        val recorder = TransitionPerformanceRecorder()
        val token = recorder.begin(0, 1, TransitionInputKind.CLICK, false, 1)
        val handle = PagePerformanceHandle(token, recorder)
        handle.pagerSettled(1, 2)
        handle.firstComposition(3)
        handle.firstComposition(4)
        handle.firstMeaningfulFrame(5)
        handle.cachePresented("memory", 7, 6)
        handle.dataReady(7)
        handle.finish(8)
        val snapshot = recorder.snapshot(token)!!
        assertEquals(3, snapshot.completedStages[TransitionStage.FIRST_COMPOSITION])
        assertEquals(5, snapshot.completedStages[TransitionStage.FIRST_MEANINGFUL_FRAME])
        assertEquals(6, snapshot.completedStages[TransitionStage.CACHE_PRESENTED])
        assertEquals(7, snapshot.completedStages[TransitionStage.DATA_READY])
        assertTrue(snapshot.finished)
        assertEquals(1, recorder.events().count { it.kind == TransitionEventKind.FINISHED })
    }

    @Test
    fun `page performance handle routes stale callbacks to diagnostics`() {
        val recorder = TransitionPerformanceRecorder()
        val oldToken = recorder.begin(0, 1, TransitionInputKind.CLICK, false, 1)
        val oldHandle = PagePerformanceHandle(oldToken, recorder)
        recorder.begin(1, 2, TransitionInputKind.SWIPE, false, 2)
        oldHandle.firstComposition(3)
        oldHandle.firstMeaningfulFrame(4)
        oldHandle.dataReady(5)
        assertTrue(recorder.snapshot(oldToken)!!.completedStages.isEmpty())
        assertTrue(recorder.events().any { it.tokenId == oldToken.id && it.kind == TransitionEventKind.STALE_DIAGNOSTIC })
    }
}
