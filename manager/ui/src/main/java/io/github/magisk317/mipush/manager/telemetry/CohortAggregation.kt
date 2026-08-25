package io.github.magisk317.mipush.manager.telemetry

import java.util.ArrayDeque

/** A report that is safe to emit from the manager telemetry boundary. */
data class NavigationCohortReport(
    val cohort: NavigationCohort,
    val sampleCount: Int,
    val p50Millis: Long?,
    val p95Millis: Long?,
    val p99Millis: Long?,
    val failureRate: Double,
    val cancellationRate: Double,
    val timeoutRate: Double,
)

data class NavigationCohortReportSet(
    val reports: Map<NavigationCohort, NavigationCohortReport>,
    /** Detailed samples are optional; the aggregate metrics above are never optional. */
    val detailedDurationsMillis: Map<NavigationCohort, List<Long>> = emptyMap(),
)

/**
 * Aggregates recorder events without doing I/O or depending on the telemetry outlet.
 *
 * A transition is counted once, when it reaches a terminal event. Binder failures are
 * classified on their owning transition, while stale diagnostics are deliberately ignored.
 * The release switch only controls detailed samples; percentile and rate metrics are always
 * retained and reported.
 */
class NavigationCohortAggregator(
    private val maxSamplesPerCohort: Int = DEFAULT_MAX_SAMPLES_PER_COHORT,
) {
    init {
        require(maxSamplesPerCohort > 0) { "maxSamplesPerCohort must be positive" }
    }

    private val lock = Any()
    private val states = LinkedHashMap<String, MutableSample>()

    fun add(events: Iterable<TransitionPerformanceEvent>) = synchronized(lock) {
        events.forEach(::accept)
    }

    fun add(event: TransitionPerformanceEvent) = synchronized(lock) {
        accept(event)
    }

    fun report(releaseAggregationEnabled: Boolean = false): NavigationCohortReportSet =
        synchronized(lock) {
            val reports = NavigationCohort.values().associateWith { cohort ->
                val samples = states.values.filter { it.cohort == cohort && it.terminal }
                val durations = samples.mapNotNull { it.durationMillis }.sorted()
                NavigationCohortReport(
                    cohort = cohort,
                    sampleCount = samples.size,
                    p50Millis = percentile(durations, 0.50),
                    p95Millis = percentile(durations, 0.95),
                    p99Millis = percentile(durations, 0.99),
                    failureRate = rate(samples.count { it.failure }, samples.size),
                    cancellationRate = rate(samples.count { it.cancelled }, samples.size),
                    timeoutRate = rate(samples.count { it.timeout }, samples.size),
                )
            }
            val detailed = if (releaseAggregationEnabled) emptyMap() else {
                states.values.asSequence()
                    .filter { it.terminal && it.durationMillis != null }
                    .groupBy { it.cohort }
                    .mapValues { (_, samples) -> samples.mapNotNull { it.durationMillis } }
            }
            NavigationCohortReportSet(reports, detailed)
        }

    fun clear() = synchronized(lock) { states.clear() }

    private fun accept(event: TransitionPerformanceEvent) {
        if (event.kind == TransitionEventKind.STALE_DIAGNOSTIC) return
        val state = states.getOrPut(event.tokenId) {
            MutableSample(event.cohort, event.startedAtNanos ?: event.timestampNanos)
        }
        // Binder-unavailable can reclassify a transition before it finishes.
        state.cohort = event.cohort
        when (event.kind) {
            TransitionEventKind.BINDER_CALL -> {
                val result = event.result.orEmpty().lowercase()
                val timedOut = result in TIMEOUT_RESULTS
                state.failure = state.failure || timedOut || result in FAILURE_RESULTS
                state.timeout = state.timeout || timedOut
            }
            TransitionEventKind.CANCELLED -> {
                terminal(state, event.timestampNanos)
                state.cancelled = true
            }
            TransitionEventKind.FINISHED -> terminal(state, event.timestampNanos)
            else -> Unit
        }
    }

    private fun terminal(state: MutableSample, timestampNanos: Long) {
        if (state.terminal) return
        state.terminal = true
        state.durationMillis = ((timestampNanos - state.startedAtNanos).coerceAtLeast(0L)) / NANOS_PER_MILLISECOND
        val terminalForCohort = states.values.filter { it.terminal && it.cohort == state.cohort }
        if (terminalForCohort.size > maxSamplesPerCohort) {
            val oldest = terminalForCohort.firstOrNull { it !== state }
            oldest?.let { states.values.remove(it) }
        }
    }

    private fun percentile(sorted: List<Long>, quantile: Double): Long? {
        if (sorted.isEmpty()) return null
        val index = kotlin.math.round((sorted.size - 1) * quantile).toInt()
        return sorted[index.coerceIn(sorted.indices)]
    }

    private fun rate(count: Int, total: Int): Double = if (total == 0) 0.0 else count.toDouble() / total

    private class MutableSample(
        var cohort: NavigationCohort,
        val startedAtNanos: Long,
        var terminal: Boolean = false,
        var durationMillis: Long? = null,
        var failure: Boolean = false,
        var cancelled: Boolean = false,
        var timeout: Boolean = false,
    )

    companion object {
        const val DEFAULT_MAX_SAMPLES_PER_COHORT = 4096
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private val FAILURE_RESULTS = setOf("failure", "failed", "error", "unavailable", "validation_failure", "busy")
        private val TIMEOUT_RESULTS = setOf("timeout", "timed_out", "request_timeout")
    }
}

enum class TelemetryPublishResult { PUBLISHED, FULL, UNAVAILABLE, FAILED }

fun interface NavigationCohortTelemetryOutlet {
    fun publish(report: NavigationCohortReportSet): TelemetryPublishResult
}

data class TelemetryOutletDiagnostic(
    val result: TelemetryPublishResult,
    val message: String,
)

/**
 * Publishes reports defensively. Outlet failures are converted into bounded diagnostics and
 * never escape into navigation, Compose, or Binder call paths.
 */
class NavigationCohortReportPublisher(
    private val outlet: NavigationCohortTelemetryOutlet,
    private val maxDiagnostics: Int = DEFAULT_MAX_DIAGNOSTICS,
) {
    init {
        require(maxDiagnostics > 0) { "maxDiagnostics must be positive" }
    }

    private val lock = Any()
    private val diagnostics = ArrayDeque<TelemetryOutletDiagnostic>(maxDiagnostics)

    fun publish(report: NavigationCohortReportSet): TelemetryPublishResult = synchronized(lock) {
        val published = runCatching { outlet.publish(report) }
        val result = published.getOrElse { throwable ->
            record(TelemetryPublishResult.FAILED, throwable.message ?: throwable::class.simpleName.orEmpty())
            return@synchronized TelemetryPublishResult.FAILED
        }
        if (result != TelemetryPublishResult.PUBLISHED) {
            record(result, "telemetry outlet ${result.name.lowercase()}")
        }
        result
    }

    fun diagnostics(): List<TelemetryOutletDiagnostic> = synchronized(lock) { diagnostics.toList() }

    private fun record(result: TelemetryPublishResult, message: String) {
        if (diagnostics.size == maxDiagnostics) diagnostics.removeFirst()
        diagnostics.addLast(TelemetryOutletDiagnostic(result, message.take(MAX_DIAGNOSTIC_LENGTH)))
    }

    companion object {
        const val DEFAULT_MAX_DIAGNOSTICS = 64
        const val MAX_DIAGNOSTIC_LENGTH = 128
    }
}
