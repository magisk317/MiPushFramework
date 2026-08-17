package io.github.magisk317.mipush.manager.benchmark

import io.github.magisk317.mipush.manager.telemetry.NavigationCohort
import io.github.magisk317.mipush.manager.telemetry.TransitionEventKind
import io.github.magisk317.mipush.manager.telemetry.TransitionPerformanceEvent
import io.github.magisk317.mipush.manager.telemetry.TransitionStage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Validates: Requirements 13.1-13.7 */
class BenchmarkAcceptanceTest {
    @Test
    fun `report joins parsed results by token and ignores unrelated telemetry`() {
        val script = FixedNavigationActionScript.create(config(rounds = 3))
        val target = script.actions.first()
        val result = resultFor(script) { action, _ ->
            if (action == target) 120 else 10
        }
        val report = result.report(
            events = listOf(
                stage(target.transitionToken, TransitionStage.FIRST_COMPOSITION, 10),
                stage(target.transitionToken, TransitionStage.FIRST_MEANINGFUL_FRAME, 20),
                stage(target.transitionToken, TransitionStage.DATA_READY, 90),
                // This event must not contaminate the target action's report.
                stage("other-run-token", TransitionStage.DATA_READY, 1_000),
            ),
        )

        val cold = report.cohorts.getValue(NavigationCohort.COLD_FIRST_NAVIGATION)
        assertEquals(1, cold.sampleCount)
        assertEquals(120L, cold.transitionP50Millis)
        assertEquals(10L, cold.firstCompositionP50Millis)
        assertEquals(20L, cold.firstMeaningfulFrameP50Millis)
        assertEquals(90L, cold.firstDataReadyP50Millis)
    }

    @Test
    fun `report calculates cohort percentiles and attributes long tail to the dominant stage`() {
        val script = FixedNavigationActionScript.create(config(rounds = 3))
        val warmActions = script.actions.filter { it.cohort == NavigationCohort.WARM_ROUND_TRIP }
        val result = resultFor(script) { action, index ->
            if (action.cohort == NavigationCohort.WARM_ROUND_TRIP) {
                // Warm has 11 samples, yielding stable p50=60, p95=110 and p99=110.
                warmActions.indexOf(action).let { (it + 1) * 10L }
            } else {
                10L + index
            }
        }
        val target = warmActions.last()
        val report = result.report(
            events = listOf(
                stage(target.transitionToken, TransitionStage.FIRST_COMPOSITION, 10),
                stage(target.transitionToken, TransitionStage.FIRST_MEANINGFUL_FRAME, 20),
                stage(target.transitionToken, TransitionStage.DATA_READY, 220),
                TransitionPerformanceEvent(
                    tokenId = target.transitionToken,
                    sequence = 1,
                    cohort = NavigationCohort.WARM_ROUND_TRIP,
                    kind = TransitionEventKind.BINDER_CALL,
                    timestampNanos = 30_000_000,
                    startedAtNanos = 0,
                    durationMillis = 15,
                    result = "success",
                ),
            ),
        )

        val warm = report.cohorts.getValue(NavigationCohort.WARM_ROUND_TRIP)
        assertEquals(11, warm.sampleCount)
        assertEquals(60L, warm.transitionP50Millis)
        assertEquals(110L, warm.transitionP95Millis)
        assertEquals(110L, warm.transitionP99Millis)
        assertEquals(1, warm.attribution.sampleCount)
        assertEquals(1, warm.attribution.counts[LongTailAttribution.DATA_WAIT])
        assertFalse(warm.attribution.counts.containsKey(LongTailAttribution.COMPOSE_LAYOUT))
    }

    @Test
    fun `comparison requires all cohorts and rejects warm path regression`() {
        val script = FixedNavigationActionScript.create(config(rounds = 3))
        val baseline = evidence(script, executions = 2) { _, _ -> 100 }
        val candidate = evidence(script, executions = 2) { action, _ ->
            if (action.cohort == NavigationCohort.WARM_ROUND_TRIP) 101 else 100
        }

        val comparison = NavigationBenchmarkComparator.compare(baseline, candidate)

        assertEquals(NavigationBenchmarkComparisonStatus.WARM_PATH_REGRESSION, comparison.status)
        assertFalse(comparison.warmPathNonRegression)
        assertFalse(comparison.resolutionClaimAllowed)
        assertEquals(1, comparison.comparisons.getValue(NavigationCohort.WARM_ROUND_TRIP).p95DeltaMillis)
    }

    @Test
    fun `comparison passes equivalent warm path only with repeated evidence`() {
        val script = FixedNavigationActionScript.create(config(rounds = 3))
        val baseline = evidence(script, executions = 2) { _, _ -> 100 }
        val candidate = evidence(script, executions = 2) { _, _ -> 90 }

        val comparison = NavigationBenchmarkComparator.compare(baseline, candidate)

        assertEquals(NavigationBenchmarkComparisonStatus.PASSED, comparison.status)
        assertTrue(comparison.sameConditions)
        assertTrue(comparison.warmPathNonRegression)
        assertTrue(comparison.resolutionClaimAllowed)
    }

    @Test
    fun `every supported round count executes five switches and visits all pages`() {
        (NavigationBenchmarkConfig.MIN_ROUNDS..NavigationBenchmarkConfig.MAX_ROUNDS).forEach { rounds ->
            val script = FixedNavigationActionScript.create(config(rounds = rounds))
            val result = kotlinx.coroutines.runBlocking {
                script.execute(NavigationBenchmarkExecutor { action ->
                    NavigationBenchmarkActionResult(
                        action = action,
                        transitionToken = action.transitionToken,
                        status = BenchmarkActionStatus.SUCCESS,
                        startedAtNanos = 0,
                        finishedAtNanos = 1_000_000,
                    )
                })
            }

            assertEquals(rounds * FixedNavigationActionScript.ACTIONS_PER_ROUND, result.actions.size)
            (1..rounds).forEach { round ->
                val actions = script.actionsForRound(round)
                assertEquals(5, actions.size)
                assertEquals(BenchmarkPage.OVERVIEW, actions.first().from)
                assertEquals(BenchmarkPage.OVERVIEW, actions.last().to)
                assertEquals(BenchmarkPage.entries.toSet(), actions.flatMap { listOf(it.from, it.to) }.toSet())
            }
        }
    }

    private fun evidence(
        script: FixedNavigationActionScript,
        executions: Int,
        durationMillis: (NavigationBenchmarkAction, Int) -> Long,
    ) = NavigationBenchmarkEvidence(
        (0 until executions).map { execution ->
            resultFor(script, execution, durationMillis)
        },
    )

    private fun resultFor(
        script: FixedNavigationActionScript,
        execution: Int = 0,
        durationMillis: (NavigationBenchmarkAction, Int) -> Long,
    ) = NavigationBenchmarkResult(
        script.config,
        script.actions.mapIndexed { index, action ->
            val duration = durationMillis(action, index)
            NavigationBenchmarkActionResult(
                action = action,
                transitionToken = action.transitionToken,
                status = BenchmarkActionStatus.SUCCESS,
                startedAtNanos = execution.toLong() * 1_000_000_000L,
                finishedAtNanos = execution.toLong() * 1_000_000_000L + duration * 1_000_000L,
            )
        },
    )

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
        runId = "acceptance-$rounds",
        rounds = rounds,
        environment = BenchmarkEnvironment("device", "debug", "fixture", "fixed-v1"),
    )
}
