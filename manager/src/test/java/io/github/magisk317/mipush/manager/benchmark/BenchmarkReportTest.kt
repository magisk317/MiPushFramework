package io.github.magisk317.mipush.manager.benchmark

import io.github.magisk317.mipush.manager.telemetry.NavigationCohort
import io.github.magisk317.mipush.manager.telemetry.TransitionEventKind
import io.github.magisk317.mipush.manager.telemetry.TransitionPerformanceEvent
import io.github.magisk317.mipush.manager.telemetry.TransitionStage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Validates: Requirements 13.3-13.5 */
class BenchmarkReportTest {
    @Test
    fun `report emits cohort percentiles stages binder frames and gpu exclusion`() {
        val script = FixedNavigationActionScript.create(config(rounds = 3))
        val result = NavigationBenchmarkResult(
            script.config,
            script.actions.mapIndexed { index, action ->
                NavigationBenchmarkActionResult(
                    action = action,
                    transitionToken = action.transitionToken,
                    status = BenchmarkActionStatus.SUCCESS,
                    startedAtNanos = 0,
                    finishedAtNanos = (index + 1).toLong() * 100_000_000L,
                )
            },
        )
        val cold = script.actions.first()
        val events = listOf(
            stage(cold.transitionToken, TransitionStage.FIRST_COMPOSITION, 10),
            stage(cold.transitionToken, TransitionStage.FIRST_MEANINGFUL_FRAME, 30),
            stage(cold.transitionToken, TransitionStage.DATA_READY, 80),
            TransitionPerformanceEvent(
                tokenId = cold.transitionToken,
                sequence = 1,
                cohort = NavigationCohort.COLD_FIRST_NAVIGATION,
                kind = TransitionEventKind.BINDER_CALL,
                timestampNanos = 40_000_000,
                startedAtNanos = 0,
                durationMillis = 25,
                result = "success",
            ),
            TransitionPerformanceEvent(
                tokenId = cold.transitionToken,
                sequence = 1,
                cohort = NavigationCohort.COLD_FIRST_NAVIGATION,
                kind = TransitionEventKind.BINDER_CALL,
                timestampNanos = 50_000_000,
                startedAtNanos = 0,
                durationMillis = 5,
                result = "timeout",
            ),
        )
        val report = result.report(
            events = events,
            frameObservations = listOf(
                BenchmarkFrameObservation(cold.transitionToken, slowFrameCount = 2, missedVsyncCount = 1, gpuExclusionObservations = 3),
            ),
        )

        val coldReport = report.cohorts[NavigationCohort.COLD_FIRST_NAVIGATION]!!
        assertEquals(1, coldReport.sampleCount)
        assertEquals(100L, coldReport.transitionP50Millis)
        assertEquals(10L, coldReport.firstCompositionP50Millis)
        assertEquals(30L, coldReport.firstMeaningfulFrameP50Millis)
        assertEquals(80L, coldReport.firstDataReadyP50Millis)
        assertEquals(25L, coldReport.binderP95Millis)
        assertEquals(25L, coldReport.binderP99Millis)
        assertEquals(1, coldReport.binderTimeoutCount)
        assertEquals(2, coldReport.uiSlowFrameCount)
        assertEquals(1, coldReport.missedVsyncCount)
        assertEquals(3, coldReport.gpuExclusionObservations)
        assertEquals(1, coldReport.attribution.sampleCount)
        assertTrue(coldReport.attribution.counts.containsKey(LongTailAttribution.DATA_WAIT))
        assertTrue(coldReport.attribution.counts.keys.none { it == LongTailAttribution.BINDER_WAIT_RESPONSE })
    }

    @Test
    fun `missing stage data is reported as other rather than attributed to gpu`() {
        val script = FixedNavigationActionScript.create(config(rounds = 3))
        val action = script.actions.first()
        val result = NavigationBenchmarkResult(
            script.config,
            script.actions.map {
                NavigationBenchmarkActionResult(it, it.transitionToken, BenchmarkActionStatus.SUCCESS, 0, 1_000_000_000L)
            },
        )
        val report = result.report(
            frameObservations = listOf(BenchmarkFrameObservation(action.transitionToken, gpuExclusionObservations = 9)),
        )
        val cold = report.cohorts[NavigationCohort.COLD_FIRST_NAVIGATION]!!
        assertEquals(1, cold.attribution.counts[LongTailAttribution.OTHER_RECORDED_STAGE])
        assertEquals(9, cold.gpuExclusionObservations)
    }

    private fun stage(token: String, stage: TransitionStage, millis: Long) =
        TransitionPerformanceEvent(
            tokenId = token,
            sequence = 1,
            cohort = NavigationCohort.COLD_FIRST_NAVIGATION,
            kind = TransitionEventKind.STAGE_COMPLETED,
            stage = stage,
            timestampNanos = millis * 1_000_000L,
            startedAtNanos = 0,
        )

    private fun config(rounds: Int) = NavigationBenchmarkConfig(
        runId = "run-report",
        rounds = rounds,
        environment = BenchmarkEnvironment("device", "debug", "fixture", "fixed-v1"),
    )
}
