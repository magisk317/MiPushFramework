package io.github.magisk317.mipush.manager.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CohortAggregationTest {
    @Test
    fun `reports percentiles and terminal rates independently of release aggregation`() {
        val aggregator = NavigationCohortAggregator()
        aggregator.add(finished("cold", NavigationCohort.COLD_FIRST_NAVIGATION, 0, 10_000_000))
        aggregator.add(finished("warm-1", NavigationCohort.WARM_ROUND_TRIP, 0, 10_000_000))
        aggregator.add(finished("warm-2", NavigationCohort.WARM_ROUND_TRIP, 0, 20_000_000))
        aggregator.add(finished("warm-3", NavigationCohort.WARM_ROUND_TRIP, 0, 100_000_000))
        aggregator.add(cancelled("cancel", NavigationCohort.WARM_ROUND_TRIP, 0, 30_000_000))
        aggregator.add(finished("timeout", NavigationCohort.RETURN_OVERVIEW, 0, 40_000_000, "timeout"))

        val debug = aggregator.report(releaseAggregationEnabled = false)
        val release = aggregator.report(releaseAggregationEnabled = true)
        val warm = debug.reports.getValue(NavigationCohort.WARM_ROUND_TRIP)

        assertEquals(4, warm.sampleCount)
        assertEquals(30, warm.p50Millis)
        assertEquals(100, warm.p95Millis)
        assertEquals(100, warm.p99Millis)
        assertEquals(0.0, warm.failureRate)
        assertEquals(0.25, warm.cancellationRate)
        assertEquals(0.0, warm.timeoutRate)
        assertEquals(1, debug.reports.getValue(NavigationCohort.COLD_FIRST_NAVIGATION).sampleCount)
        assertEquals(1.0, release.reports.getValue(NavigationCohort.RETURN_OVERVIEW).failureRate)
        assertEquals(1.0, release.reports.getValue(NavigationCohort.RETURN_OVERVIEW).timeoutRate)
        assertTrue(debug.detailedDurationsMillis.isNotEmpty())
        assertTrue(release.detailedDurationsMillis.isEmpty())
    }

    @Test
    fun `outlet full unavailable and failure are bounded and do not throw`() {
        val results = ArrayDeque(listOf(
            TelemetryPublishResult.FULL,
            TelemetryPublishResult.UNAVAILABLE,
        ))
        val publisher = NavigationCohortReportPublisher(
            outlet = NavigationCohortTelemetryOutlet { results.removeFirst() },
            maxDiagnostics = 2,
        )
        val report = NavigationCohortReportSet(emptyMap())

        assertEquals(TelemetryPublishResult.FULL, publisher.publish(report))
        assertEquals(TelemetryPublishResult.UNAVAILABLE, publisher.publish(report))
        assertEquals(TelemetryPublishResult.FAILED, publisher.publish(report))
        assertEquals(2, publisher.diagnostics().size)
        assertEquals(TelemetryPublishResult.UNAVAILABLE, publisher.diagnostics().first().result)
        assertFalse(publisher.diagnostics().any { it.message.contains("payload") })
    }

    private fun finished(
        id: String,
        cohort: NavigationCohort,
        startedAt: Long,
        finishedAt: Long,
        result: String? = null,
    ): List<TransitionPerformanceEvent> = buildList {
        add(event(id, cohort, TransitionEventKind.STAGE_COMPLETED, startedAt, TransitionStage.PAGER_SETTLED))
        if (result != null) add(event(id, cohort, TransitionEventKind.BINDER_CALL, startedAt, result = result))
        add(event(id, cohort, TransitionEventKind.FINISHED, finishedAt))
    }

    private fun cancelled(id: String, cohort: NavigationCohort, startedAt: Long, cancelledAt: Long) =
        listOf(
            event(id, cohort, TransitionEventKind.STAGE_COMPLETED, startedAt, TransitionStage.PAGER_SETTLED),
            event(id, cohort, TransitionEventKind.CANCELLED, cancelledAt),
        )

    private fun event(
        id: String,
        cohort: NavigationCohort,
        kind: TransitionEventKind,
        timestamp: Long,
        stage: TransitionStage? = null,
        result: String? = null,
    ) = TransitionPerformanceEvent(
        tokenId = id,
        sequence = 1,
        cohort = cohort,
        kind = kind,
        stage = stage,
        timestampNanos = timestamp,
        result = result,
    )
}
