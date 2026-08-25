package io.github.magisk317.mipush.manager.telemetry

import java.util.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Task 6.4 coverage for recorder ordering, privacy, failure isolation, and aggregation.
 *
 * Validates: Requirements 9.1-9.6, 10.1-10.5
 */
class TransitionTelemetryTask64Test {
    @Test
    fun `stage timestamps remain non decreasing for arbitrary event order`() {
        val random = Random(6_400L)
        repeat(100) { example ->
            val recorder = TransitionPerformanceRecorder()
            val token = recorder.begin(0, 1, TransitionInputKind.SWIPE, false, 1)
            val timestamps = (0..5).map { 10L + it * 10L }.shuffled(random)
            recorder.markPagerSettled(token, 1, timestamps[0])
            recorder.markFirstComposition(token, timestamps[1])
            recorder.markFirstMeaningfulFrame(token, timestamps[2])
            recorder.markCacheResult(token, "memory", 0, timestamps[3])
            recorder.markDataReady(token, timestamps[4])
            recorder.finish(token, timestamps[5])

            val completed = recorder.snapshot(token)!!.completedStages.values.toList()
            assertTrue(
                completed.zipWithNext().all { (previous, next) -> previous <= next },
                "example $example recorded stages out of order: $completed",
            )
        }
    }

    @Test
    fun `duplicate completion retains first valid timestamp and terminal state is unique`() {
        val recorder = TransitionPerformanceRecorder()
        val token = recorder.begin(0, 1, TransitionInputKind.CLICK, false, 1)

        recorder.markPagerSettled(token, 1, 10)
        recorder.markPagerSettled(token, 1, 11)
        recorder.markFirstMeaningfulFrame(token, 20)
        recorder.markCacheResult(token, "memory", 2, 30)
        recorder.markCacheResult(token, "disk", 3, 31)
        recorder.markDataReady(token, 40)
        recorder.finish(token, 50)
        recorder.finish(token, 51)

        val snapshot = recorder.snapshot(token)!!
        assertEquals(10, snapshot.completedStages[TransitionStage.PAGER_SETTLED])
        assertEquals(30, snapshot.completedStages[TransitionStage.CACHE_PRESENTED])
        assertTrue(snapshot.finished)
        assertFalse(snapshot.cancelled)
        assertEquals(1, recorder.events().count { it.kind == TransitionEventKind.FINISHED })
    }

    @Test
    fun `stale token events are diagnostics and cannot complete current token`() {
        val recorder = TransitionPerformanceRecorder()
        val stale = recorder.begin(0, 1, TransitionInputKind.CLICK, false, 1)
        val current = recorder.begin(1, 2, TransitionInputKind.CLICK, false, 2)

        recorder.markPagerSettled(stale, 1, 3)
        recorder.markFirstMeaningfulFrame(stale, 4)
        recorder.markDataReady(stale, 5)

        assertTrue(recorder.snapshot(current)!!.completedStages.isEmpty())
        assertTrue(
            recorder.events().filter { it.tokenId == stale.id }.all {
                it.kind == TransitionEventKind.STALE_DIAGNOSTIC
            },
        )
        assertEquals(3, recorder.events().count { it.kind == TransitionEventKind.STALE_DIAGNOSTIC })
    }

    @Test
    fun `structured events enforce whitelist redaction and maximum text length`() {
        val recorder = TransitionPerformanceRecorder(
            maxTextLength = 8,
            process = "manager-process-name-that-is-too-long",
            buildVariant = "debug-variant-that-is-too-long",
        )
        val token = recorder.begin(0, 1, TransitionInputKind.CLICK, false, 1)
        recorder.markBinderCall(
            token = token,
            operation = "payload com.example.secret query=user filter=all",
            priority = "VISIBLE_PAGE_PRIORITY_TOO_LONG",
            durationMillis = 3,
            result = "success",
            availability = "available",
            timestampNanos = 2,
        )

        val fields = recorder.structuredEvents().single().fields()
        assertTrue(fields.keys.all { it in ManagerNavigationTelemetryEvent.APPROVED_FIELDS })
        assertEquals("redacted", fields["operation"])
        assertTrue(fields.values.filterIsInstance<String>().all { it.length <= 8 })
        assertFalse(fields.values.any { it.toString().contains("example.com") })
    }

    @Test
    fun `telemetry outlet failures are isolated and diagnostics remain bounded`() {
        val publisher = NavigationCohortReportPublisher(
            outlet = NavigationCohortTelemetryOutlet { throw IllegalStateException("outlet exploded") },
            maxDiagnostics = 2,
        )
        val report = NavigationCohortReportSet(emptyMap())

        repeat(5) { assertEquals(TelemetryPublishResult.FAILED, publisher.publish(report)) }

        assertEquals(2, publisher.diagnostics().size)
        assertTrue(publisher.diagnostics().all { it.result == TelemetryPublishResult.FAILED })
        assertTrue(publisher.diagnostics().all { it.message.length <= NavigationCohortReportPublisher.MAX_DIAGNOSTIC_LENGTH })
    }

    @Test
    fun `percentiles and rates are invariant when release aggregation switches`() {
        val random = Random(64L)
        repeat(50) { round ->
            val aggregator = NavigationCohortAggregator()
            val durations = (0 until 20).map { random.nextInt(1_000).toLong() }
            durations.forEachIndexed { index, duration ->
                val tokenId = "token-$round-$index"
                val startedAt = index * 2_000_000_000L
                val eventTime = startedAt + duration * 1_000_000L
                aggregator.add(
                    TransitionPerformanceEvent(
                        tokenId = tokenId,
                        sequence = index.toLong(),
                        cohort = NavigationCohort.WARM_ROUND_TRIP,
                        kind = TransitionEventKind.FINISHED,
                        timestampNanos = eventTime,
                        startedAtNanos = startedAt,
                    ),
                )
            }

            val detailedReport = aggregator.report(releaseAggregationEnabled = false)
            val releaseReport = aggregator.report(releaseAggregationEnabled = true)
            val detailed = detailedReport.reports[NavigationCohort.WARM_ROUND_TRIP]
            val release = releaseReport.reports[NavigationCohort.WARM_ROUND_TRIP]
            assertNotNull(detailed)
            assertEquals(detailed, release)
            assertEquals(
                durations.sorted(),
                detailedReport.detailedDurationsMillis[NavigationCohort.WARM_ROUND_TRIP].orEmpty().sorted(),
            )
            assertTrue(releaseReport.detailedDurationsMillis.isEmpty())
        }
    }

    @Test
    fun `aggregation ignores stale diagnostics and counts each terminal token once`() {
        val aggregator = NavigationCohortAggregator()
        val startedAt = 1_000_000L
        val finished = TransitionPerformanceEvent(
            tokenId = "token-1",
            sequence = 1,
            cohort = NavigationCohort.COLD_FIRST_NAVIGATION,
            kind = TransitionEventKind.FINISHED,
            timestampNanos = startedAt + 12_000_000L,
            startedAtNanos = startedAt,
        )
        aggregator.add(
            listOf(
                finished,
                finished.copy(kind = TransitionEventKind.STALE_DIAGNOSTIC),
                finished.copy(kind = TransitionEventKind.FINISHED, timestampNanos = startedAt + 50_000_000L),
            ),
        )

        val report = aggregator.report().reports[NavigationCohort.COLD_FIRST_NAVIGATION]!!
        assertEquals(1, report.sampleCount)
        assertEquals(12L, report.p50Millis)
    }
}
