package io.github.magisk317.mipush.manager.benchmark

import io.github.magisk317.mipush.manager.telemetry.NavigationCohort
import kotlin.math.round

/** Repeated benchmark executions collected under one fixed device/build/data condition. */
data class NavigationBenchmarkEvidence(
    val executions: List<NavigationBenchmarkResult>,
) {
    init {
        require(executions.isNotEmpty()) { "at least one benchmark execution is required" }
        val first = executions.first().config
        require(executions.all { it.config.environment == first.environment }) {
            "all executions in an evidence set must use the same environment"
        }
        require(executions.all { it.config.rounds == first.rounds }) {
            "all executions in an evidence set must use the same round count"
        }
    }

    val environment: BenchmarkEnvironment
        get() = executions.first().config.environment

    val rounds: Int
        get() = executions.first().config.rounds

    /** A single execution is useful for diagnostics, but is never resolution evidence. */
    val hasIndependentRepeat: Boolean
        get() = executions.size >= MIN_INDEPENDENT_EXECUTIONS

    companion object {
        const val MIN_INDEPENDENT_EXECUTIONS = 2
    }
}

data class NavigationCohortComparison(
    val cohort: NavigationCohort,
    val baseline: NavigationBenchmarkMetrics,
    val candidate: NavigationBenchmarkMetrics,
) {
    val p95DeltaMillis: Long?
        get() = candidate.p95Millis?.let { candidateP95 -> baseline.p95Millis?.let { candidateP95 - it } }

    val p99DeltaMillis: Long?
        get() = candidate.p99Millis?.let { candidateP99 -> baseline.p99Millis?.let { candidateP99 - it } }
}

data class NavigationBenchmarkMetrics(
    val sampleCount: Int,
    val p50Millis: Long?,
    val p95Millis: Long?,
    val p99Millis: Long?,
    val failureCount: Int,
    val timeoutCount: Int,
) {
    val complete: Boolean
        get() = sampleCount > 0
}

enum class NavigationBenchmarkComparisonStatus {
    PASSED,
    CONDITION_MISMATCH,
    MISSING_COHORT,
    WARM_PATH_REGRESSION,
    REGRESSION,
    INSUFFICIENT_EVIDENCE,
}

data class NavigationBenchmarkComparison(
    val status: NavigationBenchmarkComparisonStatus,
    val sameConditions: Boolean,
    val warmPathNonRegression: Boolean,
    val resolutionClaimAllowed: Boolean,
    val comparisons: Map<NavigationCohort, NavigationCohortComparison>,
    val message: String,
) {
    val passed: Boolean
        get() = status == NavigationBenchmarkComparisonStatus.PASSED
}

/**
 * Compares before/after Manager benchmark evidence without treating a best-case run as proof.
 *
 * The comparison is deliberately independent of Android and runtime installation. It consumes
 * results emitted by [FixedNavigationActionScript] and only compares runs with identical device,
 * build variant, dataset, action script, and round count. A comparison may still be useful with
 * one execution per side, but [NavigationBenchmarkComparison.resolutionClaimAllowed] remains
 * false until both sides contain at least two independent executions.
 */
object NavigationBenchmarkComparator {
    private val requiredCohorts = setOf(
        NavigationCohort.COLD_FIRST_NAVIGATION,
        NavigationCohort.RETURN_OVERVIEW,
        NavigationCohort.WARM_ROUND_TRIP,
    )

    /**
     * [maxRegressionPercent] applies to p95 and is inclusive: a candidate at the threshold is
     * not a regression. P99 is reported for diagnosis but is not used as the pass/fail gate.
     */
    fun compare(
        baseline: NavigationBenchmarkEvidence,
        candidate: NavigationBenchmarkEvidence,
        maxRegressionPercent: Double = 0.0,
    ): NavigationBenchmarkComparison {
        require(maxRegressionPercent >= 0.0) { "maxRegressionPercent must not be negative" }
        val sameConditions = baseline.environment == candidate.environment &&
            baseline.rounds == candidate.rounds
        if (!sameConditions) {
            return comparison(
                status = NavigationBenchmarkComparisonStatus.CONDITION_MISMATCH,
                sameConditions = false,
                warmPathNonRegression = false,
                resolutionClaimAllowed = false,
                comparisons = emptyMap(),
                message = "baseline and candidate must use identical device, build, dataset, action script, and rounds",
            )
        }

        val baselineMetrics = metricsByCohort(baseline)
        val candidateMetrics = metricsByCohort(candidate)
        val missing = requiredCohorts.filter { cohort ->
            baselineMetrics[cohort]?.complete != true || candidateMetrics[cohort]?.complete != true
        }
        if (missing.isNotEmpty()) {
            return comparison(
                status = NavigationBenchmarkComparisonStatus.MISSING_COHORT,
                sameConditions = true,
                warmPathNonRegression = false,
                resolutionClaimAllowed = false,
                comparisons = emptyMap(),
                message = "missing required cohort(s): ${missing.joinToString()}",
            )
        }

        val comparisons = requiredCohorts.associateWith { cohort ->
            NavigationCohortComparison(cohort, baselineMetrics.getValue(cohort), candidateMetrics.getValue(cohort))
        }
        val regressions = comparisons.values.filter { comparison ->
            exceedsP95Budget(comparison.baseline.p95Millis, comparison.candidate.p95Millis, maxRegressionPercent)
        }
        val warmPathNonRegression = NavigationCohort.WARM_ROUND_TRIP !in regressions.map { it.cohort }
        val repeated = baseline.hasIndependentRepeat && candidate.hasIndependentRepeat
        val status = when {
            !warmPathNonRegression -> NavigationBenchmarkComparisonStatus.WARM_PATH_REGRESSION
            regressions.isNotEmpty() -> NavigationBenchmarkComparisonStatus.REGRESSION
            !repeated -> NavigationBenchmarkComparisonStatus.INSUFFICIENT_EVIDENCE
            else -> NavigationBenchmarkComparisonStatus.PASSED
        }
        val message = when (status) {
            NavigationBenchmarkComparisonStatus.PASSED -> "all required cohorts passed and warm path did not regress"
            NavigationBenchmarkComparisonStatus.INSUFFICIENT_EVIDENCE ->
                "metrics passed, but one-off executions cannot support a resolution claim; provide at least two executions per side"
            NavigationBenchmarkComparisonStatus.WARM_PATH_REGRESSION -> "warm_round_trip p95 regressed"
            NavigationBenchmarkComparisonStatus.REGRESSION ->
                "one or more required cohort p95 values regressed: ${regressions.joinToString { it.cohort.name }}"
            else -> status.name.lowercase()
        }
        return comparison(
            status = status,
            sameConditions = true,
            warmPathNonRegression = warmPathNonRegression,
            resolutionClaimAllowed = status == NavigationBenchmarkComparisonStatus.PASSED,
            comparisons = comparisons,
            message = message,
        )
    }

    private fun metricsByCohort(evidence: NavigationBenchmarkEvidence): Map<NavigationCohort, NavigationBenchmarkMetrics> =
        evidence.executions
            .flatMap { it.actions }
            .groupBy { it.action.cohort }
            .mapValues { (_, actions) ->
                val durations = actions.map { durationMillis(it) }.sorted()
                NavigationBenchmarkMetrics(
                    sampleCount = actions.size,
                    p50Millis = percentile(durations, P50),
                    p95Millis = percentile(durations, P95),
                    p99Millis = percentile(durations, P99),
                    failureCount = actions.count { it.status == BenchmarkActionStatus.FAILURE },
                    timeoutCount = actions.count { it.status == BenchmarkActionStatus.TIMEOUT },
                )
            }

    private fun durationMillis(result: NavigationBenchmarkActionResult): Long =
        ((result.finishedAtNanos - result.startedAtNanos).coerceAtLeast(0L)) / NANOS_PER_MILLISECOND

    private fun percentile(sorted: List<Long>, quantile: Double): Long? {
        if (sorted.isEmpty()) return null
        val index = round((sorted.size - 1) * quantile).toInt().coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun exceedsP95Budget(baseline: Long?, candidate: Long?, maxRegressionPercent: Double): Boolean {
        if (baseline == null || candidate == null) return true
        if (baseline == 0L) return candidate > 0L
        val allowed = baseline.toDouble() * (1.0 + maxRegressionPercent / 100.0)
        return candidate.toDouble() > allowed
    }

    private fun comparison(
        status: NavigationBenchmarkComparisonStatus,
        sameConditions: Boolean,
        warmPathNonRegression: Boolean,
        resolutionClaimAllowed: Boolean,
        comparisons: Map<NavigationCohort, NavigationCohortComparison>,
        message: String,
    ) = NavigationBenchmarkComparison(
        status = status,
        sameConditions = sameConditions,
        warmPathNonRegression = warmPathNonRegression,
        resolutionClaimAllowed = resolutionClaimAllowed,
        comparisons = comparisons,
        message = message,
    )

    private const val NANOS_PER_MILLISECOND = 1_000_000L
    private const val P50 = 0.50
    private const val P95 = 0.95
    private const val P99 = 0.99
}
