package io.github.magisk317.mipush.manager.benchmark

import io.github.magisk317.mipush.manager.telemetry.NavigationCohort
import io.github.magisk317.mipush.manager.telemetry.TransitionEventKind
import io.github.magisk317.mipush.manager.telemetry.TransitionPerformanceEvent
import io.github.magisk317.mipush.manager.telemetry.TransitionStage
import kotlin.math.round

/** UI-thread observations collected by the device harness for one transition. */
data class BenchmarkFrameObservation(
    val transitionToken: String,
    val slowFrameCount: Int = 0,
    val missedVsyncCount: Int = 0,
    /** GPU data is retained only as an exclusion signal, never as an attribution target. */
    val gpuExclusionObservations: Int = 0,
) {
    init {
        require(transitionToken.isNotBlank()) { "transitionToken must not be blank" }
        require(slowFrameCount >= 0) { "slowFrameCount must not be negative" }
        require(missedVsyncCount >= 0) { "missedVsyncCount must not be negative" }
        require(gpuExclusionObservations >= 0) { "gpuExclusionObservations must not be negative" }
    }
}

enum class LongTailAttribution {
    COMPOSE_LAYOUT,
    DATA_WAIT,
    BINDER_WAIT_RESPONSE,
    OTHER_RECORDED_STAGE,
}

data class LongTailAttributionReport(
    val sampleCount: Int,
    val counts: Map<LongTailAttribution, Int>,
) {
    init {
        require(sampleCount >= 0) { "sampleCount must not be negative" }
        require(counts.values.all { it >= 0 }) { "attribution counts must not be negative" }
    }
}

data class BenchmarkCohortReport(
    val cohort: NavigationCohort,
    val sampleCount: Int,
    val transitionP50Millis: Long?,
    val transitionP95Millis: Long?,
    val transitionP99Millis: Long?,
    val firstCompositionP50Millis: Long?,
    val firstMeaningfulFrameP50Millis: Long?,
    val firstDataReadyP50Millis: Long?,
    val binderP95Millis: Long?,
    val binderP99Millis: Long?,
    val binderTimeoutCount: Int,
    val uiSlowFrameCount: Int,
    val missedVsyncCount: Int,
    /** Exclusion-only GPU observations; never included in [attribution]. */
    val gpuExclusionObservations: Int,
    val attribution: LongTailAttributionReport,
)

data class BenchmarkReport(
    val runId: String,
    val environment: BenchmarkEnvironment,
    val cohorts: Map<NavigationCohort, BenchmarkCohortReport>,
)

/**
 * Produces the report required for device acceptance without performing I/O or controlling a
 * device. Events are joined by the fixed action's transition token, so unrelated recorder data
 * cannot contaminate a benchmark run.
 */
fun NavigationBenchmarkResult.report(
    events: Iterable<TransitionPerformanceEvent> = emptyList(),
    frameObservations: Iterable<BenchmarkFrameObservation> = emptyList(),
): BenchmarkReport {
    val eventsByToken = events.groupBy { it.tokenId }
    val framesByToken = frameObservations.groupBy { it.transitionToken }
    val reports = NavigationCohort.values().associateWith { cohort ->
        val samples = actions.filter { it.action.cohort == cohort }.map { actionResult ->
            BenchmarkSample(
                action = actionResult,
                events = eventsByToken[actionResult.transitionToken].orEmpty(),
                frame = framesByToken[actionResult.transitionToken].orEmpty().fold(null) { total, next ->
                    BenchmarkFrameObservation(
                        transitionToken = next.transitionToken,
                        slowFrameCount = (total?.slowFrameCount ?: 0) + next.slowFrameCount,
                        missedVsyncCount = (total?.missedVsyncCount ?: 0) + next.missedVsyncCount,
                        gpuExclusionObservations =
                            (total?.gpuExclusionObservations ?: 0) + next.gpuExclusionObservations,
                    )
                },
            )
        }
        samples.toReport(cohort)
    }
    return BenchmarkReport(config.runId, config.environment, reports)
}

private data class BenchmarkSample(
    val action: NavigationBenchmarkActionResult,
    val events: List<TransitionPerformanceEvent>,
    val frame: BenchmarkFrameObservation?,
) {
    val transitionMillis: Long
        get() = nanosToMillis(action.finishedAtNanos - action.startedAtNanos)

    fun stageMillis(stage: TransitionStage): Long? = events
        .asSequence()
        .filter { it.kind == TransitionEventKind.STAGE_COMPLETED && it.stage == stage }
        .map { event -> nanosToMillis(event.timestampNanos - (event.startedAtNanos ?: action.startedAtNanos)) }
        .filter { it >= 0 }
        .minOrNull()

    fun binderDurations(): List<Long> = events
        .asSequence()
        .filter { it.kind == TransitionEventKind.BINDER_CALL }
        .mapNotNull { it.durationMillis?.coerceAtLeast(0) }
        .toList()

    fun binderTimeoutCount(): Int = events.count {
        it.kind == TransitionEventKind.BINDER_CALL &&
            it.result.orEmpty().lowercase() in TIMEOUT_RESULTS
    }

    fun attributionCandidates(): Set<LongTailAttribution> {
        val composition = stageMillis(TransitionStage.FIRST_COMPOSITION)
        val meaningful = stageMillis(TransitionStage.FIRST_MEANINGFUL_FRAME)
        val dataReady = stageMillis(TransitionStage.DATA_READY)
        val composeDuration = if (composition != null && meaningful != null) {
            (meaningful - composition).coerceAtLeast(0)
        } else null
        val dataDuration = if (dataReady != null && meaningful != null) {
            (dataReady - meaningful).coerceAtLeast(0)
        } else null
        val binderDuration = binderDurations().sum()
        val candidates = listOfNotNull(
            composeDuration?.let { LongTailAttribution.COMPOSE_LAYOUT to it },
            dataDuration?.let { LongTailAttribution.DATA_WAIT to it },
            if (binderDurations().isNotEmpty()) {
                LongTailAttribution.BINDER_WAIT_RESPONSE to binderDuration
            } else null,
        )
        if (candidates.isEmpty()) return setOf(LongTailAttribution.OTHER_RECORDED_STAGE)
        val maximum = candidates.maxOf { it.second }
        return candidates.filter { it.second == maximum }.map { it.first }.toSet()
    }
}

private fun List<BenchmarkSample>.toReport(cohort: NavigationCohort): BenchmarkCohortReport {
    val transitionDurations = map { it.transitionMillis }.sorted()
    val composition = mapNotNull { it.stageMillis(TransitionStage.FIRST_COMPOSITION) }.sorted()
    val meaningful = mapNotNull { it.stageMillis(TransitionStage.FIRST_MEANINGFUL_FRAME) }.sorted()
    val dataReady = mapNotNull { it.stageMillis(TransitionStage.DATA_READY) }.sorted()
    val binder = flatMap { it.binderDurations() }.sorted()
    val tailThreshold = percentile(transitionDurations, P95)
    val tailSamples = if (tailThreshold == null) emptyList() else filter { it.transitionMillis >= tailThreshold }
    val attributionCounts = LongTailAttribution.values().associateWith { attribution ->
        tailSamples.count { attribution in it.attributionCandidates() }
    }.filterValues { it > 0 }
    val frame = fold(FrameTotals()) { total, sample ->
        FrameTotals(
            slow = total.slow + (sample.frame?.slowFrameCount ?: 0),
            missed = total.missed + (sample.frame?.missedVsyncCount ?: 0),
            gpu = total.gpu + (sample.frame?.gpuExclusionObservations ?: 0),
        )
    }
    return BenchmarkCohortReport(
        cohort = cohort,
        sampleCount = size,
        transitionP50Millis = percentile(transitionDurations, P50),
        transitionP95Millis = percentile(transitionDurations, P95),
        transitionP99Millis = percentile(transitionDurations, P99),
        firstCompositionP50Millis = percentile(composition, P50),
        firstMeaningfulFrameP50Millis = percentile(meaningful, P50),
        firstDataReadyP50Millis = percentile(dataReady, P50),
        binderP95Millis = percentile(binder, P95),
        binderP99Millis = percentile(binder, P99),
        binderTimeoutCount = sumOf { it.binderTimeoutCount() },
        uiSlowFrameCount = frame.slow,
        missedVsyncCount = frame.missed,
        gpuExclusionObservations = frame.gpu,
        attribution = LongTailAttributionReport(tailSamples.size, attributionCounts),
    )
}

private data class FrameTotals(val slow: Int = 0, val missed: Int = 0, val gpu: Int = 0)

private fun percentile(sorted: List<Long>, quantile: Double): Long? {
    if (sorted.isEmpty()) return null
    val index = round((sorted.size - 1) * quantile).toInt().coerceIn(sorted.indices)
    return sorted[index]
}

private fun nanosToMillis(nanos: Long): Long = nanos.coerceAtLeast(0) / NANOS_PER_MILLISECOND

private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val P50 = 0.50
private const val P95 = 0.95
private const val P99 = 0.99
private val TIMEOUT_RESULTS = setOf("timeout", "timed_out", "request_timeout")
