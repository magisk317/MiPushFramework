package io.github.magisk317.mipush.manager.benchmark

import io.github.magisk317.mipush.manager.telemetry.NavigationCohort
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NavigationBenchmarkTest {
    @Test
    fun `fixed script visits all pages and makes four switches per round`() {
        val script = FixedNavigationActionScript.create(config(rounds = 3))

        assertEquals(12, script.actions.size)
        (1..3).forEach { round ->
            val actions = script.actionsForRound(round)
            assertEquals(4, actions.size)
            assertEquals(
                setOf(BenchmarkPage.OVERVIEW, BenchmarkPage.APPLICATIONS, BenchmarkPage.EVENTS,
                    BenchmarkPage.SETTINGS),
                actions.flatMap { listOf(it.from, it.to) }.toSet(),
            )
            assertEquals(BenchmarkPage.OVERVIEW, actions.last().to)
        }
        assertEquals(NavigationCohort.COLD_FIRST_NAVIGATION, script.actions.first().cohort)
        assertEquals(3, script.actions.count { it.cohort == NavigationCohort.RETURN_OVERVIEW })
        assertTrue(script.actions.drop(1).any { it.cohort == NavigationCohort.WARM_ROUND_TRIP })
    }

    @Test
    fun `configuration accepts only three through five rounds`() {
        assertThrows<IllegalArgumentException> { config(rounds = 2) }
        assertThrows<IllegalArgumentException> { config(rounds = 6) }
        assertEquals(5, FixedNavigationActionScript.create(config(rounds = 5)).config.rounds)
    }

    @Test
    fun `execution preserves token and associates every result with the fixed action`() = runBlocking {
        val script = FixedNavigationActionScript.create(config(rounds = 3))
        val result = script.execute(NavigationBenchmarkExecutor { action ->
            NavigationBenchmarkActionResult(
                action = action,
                transitionToken = action.transitionToken,
                status = BenchmarkActionStatus.SUCCESS,
                startedAtNanos = action.switchIndex.toLong(),
                finishedAtNanos = action.switchIndex.toLong() + 1,
            )
        })

        assertEquals(12, result.actions.size)
        assertEquals(result.actions.map { it.action.transitionToken }, result.actions.map { it.transitionToken })
        assertEquals(
            setOf(
                NavigationCohort.COLD_FIRST_NAVIGATION,
                NavigationCohort.RETURN_OVERVIEW,
                NavigationCohort.WARM_ROUND_TRIP,
            ),
            result.cohorts,
        )
    }

    @Test
    fun `comparison requires same conditions and all three navigation cohorts`() {
        val baseline = evidence(config(rounds = 3), 10L)
        val candidate = evidence(
            config(rounds = 3).copy(environment = config(rounds = 3).environment.copy(device = "other-device")),
            9L,
        )

        val comparison = NavigationBenchmarkComparator.compare(baseline, candidate)

        assertEquals(NavigationBenchmarkComparisonStatus.CONDITION_MISMATCH, comparison.status)
        assertFalse(comparison.resolutionClaimAllowed)
    }

    @Test
    fun `comparison rejects one-off resolution claims and preserves warm path gate`() {
        val baseline = evidence(config(rounds = 3), 10L)
        val candidate = evidence(config(rounds = 3), 10L)

        val comparison = NavigationBenchmarkComparator.compare(baseline, candidate)

        assertEquals(NavigationBenchmarkComparisonStatus.INSUFFICIENT_EVIDENCE, comparison.status)
        assertTrue(comparison.warmPathNonRegression)
        assertFalse(comparison.resolutionClaimAllowed)
    }

    @Test
    fun `comparison passes repeated same-condition evidence only when warm path does not regress`() {
        val baselineConfig = config(rounds = 3)
        val candidateConfig = baselineConfig.copy(runId = "after")
        val baseline = NavigationBenchmarkEvidence(
            listOf(
                evidence(baselineConfig, 10L).executions.single(),
                evidence(baselineConfig.copy(runId = "baseline-2"), 10L).executions.single(),
            ),
        )
        val candidate = NavigationBenchmarkEvidence(
            listOf(
                evidence(candidateConfig, 9L).executions.single(),
                evidence(candidateConfig.copy(runId = "after-2"), 9L).executions.single(),
            ),
        )

        val comparison = NavigationBenchmarkComparator.compare(baseline, candidate)

        assertEquals(NavigationBenchmarkComparisonStatus.PASSED, comparison.status)
        assertTrue(comparison.sameConditions)
        assertTrue(comparison.warmPathNonRegression)
        assertTrue(comparison.resolutionClaimAllowed)
        assertEquals(-1L, comparison.comparisons.getValue(NavigationCohort.WARM_ROUND_TRIP).p95DeltaMillis)
    }

    @Test
    fun `comparison reports warm path regression even when other cohorts improve`() {
        val baseline = NavigationBenchmarkEvidence(
            listOf(
                evidence(config(rounds = 3), 10L, warmMillis = 10L).executions.single(),
                evidence(config(rounds = 3).copy(runId = "baseline-2"), 10L, warmMillis = 10L).executions.single(),
            ),
        )
        val candidate = NavigationBenchmarkEvidence(
            listOf(
                evidence(config(rounds = 3).copy(runId = "after"), 9L, warmMillis = 20L).executions.single(),
                evidence(config(rounds = 3).copy(runId = "after-2"), 9L, warmMillis = 20L).executions.single(),
            ),
        )

        val comparison = NavigationBenchmarkComparator.compare(baseline, candidate)

        assertEquals(NavigationBenchmarkComparisonStatus.WARM_PATH_REGRESSION, comparison.status)
        assertFalse(comparison.warmPathNonRegression)
        assertFalse(comparison.resolutionClaimAllowed)
    }

    private fun evidence(
        config: NavigationBenchmarkConfig,
        millis: Long,
        warmMillis: Long = millis,
    ): NavigationBenchmarkEvidence {
        val script = FixedNavigationActionScript.create(config)
        val result = runBlocking {
            script.execute(NavigationBenchmarkExecutor { action ->
                val duration = if (action.cohort == NavigationCohort.WARM_ROUND_TRIP) warmMillis else millis
                NavigationBenchmarkActionResult(
                    action = action,
                    transitionToken = action.transitionToken,
                    status = BenchmarkActionStatus.SUCCESS,
                    startedAtNanos = 0L,
                    finishedAtNanos = duration * 1_000_000L,
                )
            })
        }
        return NavigationBenchmarkEvidence(listOf(result))
    }

    private fun config(rounds: Int) = NavigationBenchmarkConfig(
        runId = "run-1",
        rounds = rounds,
        environment = BenchmarkEnvironment(
            device = "test-device",
            buildVariant = "debug",
            dataset = "fixture",
            actionScript = "top-level-round-trip-v1",
        ),
    )
}
