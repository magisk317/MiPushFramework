package io.github.magisk317.mipush.manager.telemetry

import java.util.ArrayDeque

/** Navigation cohorts used for separating cold, return, warm, and unavailable samples. */
enum class NavigationCohort {
    COLD_FIRST_NAVIGATION,
    RETURN_OVERVIEW,
    WARM_ROUND_TRIP,
    BINDER_UNAVAILABLE,
}

enum class TransitionInputKind {
    CLICK,
    SWIPE,
    RESTORE,
    DEEP_ROUTE,
}

enum class TransitionStage {
    REQUESTED,
    PAGER_SETTLED,
    FIRST_COMPOSITION,
    FIRST_MEANINGFUL_FRAME,
    CACHE_PRESENTED,
    DATA_READY,
}

enum class TransitionEventKind {
    STAGE_COMPLETED,
    CACHE_RESULT,
    BINDER_CALL,
    FINISHED,
    CANCELLED,
    STALE_DIAGNOSTIC,
}

data class TransitionToken(
    val id: String,
    val sequence: Long,
    val sourcePage: Int,
    val targetPage: Int,
    val inputKind: TransitionInputKind,
    val startedAtNanos: Long,
    val isCold: Boolean,
    var cohort: NavigationCohort,
)

data class TransitionPerformanceEvent(
    val tokenId: String,
    val sequence: Long,
    val cohort: NavigationCohort,
    val kind: TransitionEventKind,
    val stage: TransitionStage? = null,
    val timestampNanos: Long,
    val startedAtNanos: Long? = null,
    val durationMillis: Long? = null,
    val cacheSource: String? = null,
    val operation: String? = null,
    val priority: String? = null,
    val result: String? = null,
    val availability: String? = null,
)

/**
 * The only shape allowed to leave the manager navigation recorder.
 *
 * Recorder state (sequence, cohort, event kind and timestamps) remains internal diagnostic data;
 * it is deliberately not part of the structured telemetry contract. Nullable values are omitted
 * by [fields], so callers cannot accidentally emit an unapproved field with an empty value.
 */
data class ManagerNavigationTelemetryEvent(
    val token: String,
    val sourcePage: Int,
    val targetPage: Int,
    val stage: String? = null,
    val durationMillis: Long? = null,
    val cacheSource: String? = null,
    val operation: String? = null,
    val priority: String? = null,
    val result: String? = null,
    val availability: String? = null,
    val process: String,
    val buildVariant: String,
) {
    fun fields(): Map<String, Any> = buildMap {
        put("token", token)
        put("source_page", sourcePage)
        put("target_page", targetPage)
        stage?.let { put("stage", it) }
        durationMillis?.let { put("duration_ms", it) }
        cacheSource?.let { put("cache_source", it) }
        operation?.let { put("operation", it) }
        priority?.let { put("priority", it) }
        result?.let { put("result", it) }
        availability?.let { put("availability", it) }
        put("process", process)
        put("build_variant", buildVariant)
    }

    companion object {
        val APPROVED_FIELDS: Set<String> = setOf(
            "token", "source_page", "target_page", "stage", "duration_ms",
            "cache_source", "operation", "priority", "result", "availability",
            "process", "build_variant",
        )
    }
}

data class TransitionSnapshot(
    val token: TransitionToken,
    val completedStages: Map<TransitionStage, Long>,
    val finished: Boolean,
    val cancelled: Boolean,
)

/**
 * Manager-only, in-memory transition recorder.
 *
 * Record methods only mutate bounded memory. They intentionally have no DataStore, file, Binder,
 * coroutine or remote telemetry dependencies; a separate consumer may drain [events] later.
 */
class TransitionPerformanceRecorder(
    private val maxBufferedEvents: Int = DEFAULT_MAX_BUFFERED_EVENTS,
    private val maxTextLength: Int = MAX_TEXT_LENGTH,
    private val process: String = DEFAULT_PROCESS,
    private val buildVariant: String = DEFAULT_BUILD_VARIANT,
    private val clockNanos: () -> Long = System::nanoTime,
) {
    init {
        require(maxBufferedEvents > 0) { "maxBufferedEvents must be positive" }
        require(maxTextLength > 0) { "maxTextLength must be positive" }
    }

    private val lock = Any()
    private val bufferedEvents = ArrayDeque<TransitionPerformanceEvent>(maxBufferedEvents)
    private val states = LinkedHashMap<String, MutableTransitionState>()
    private var nextSequence = 0L
    private var currentTokenId: String? = null

    fun begin(
        sourcePage: Int,
        targetPage: Int,
        inputKind: TransitionInputKind,
        isCold: Boolean,
        startedAtNanos: Long = clockNanos(),
    ): TransitionToken = synchronized(lock) {
        nextSequence += 1
        val cohort = when {
            isCold -> NavigationCohort.COLD_FIRST_NAVIGATION
            targetPage == OVERVIEW_PAGE && sourcePage != OVERVIEW_PAGE -> NavigationCohort.RETURN_OVERVIEW
            else -> NavigationCohort.WARM_ROUND_TRIP
        }
        val token = TransitionToken(
            id = "transition-$nextSequence",
            sequence = nextSequence,
            sourcePage = sourcePage,
            targetPage = targetPage,
            inputKind = inputKind,
            startedAtNanos = startedAtNanos,
            isCold = isCold,
            cohort = cohort,
        )
        states[token.id] = MutableTransitionState(token)
        currentTokenId = token.id
        token
    }

    fun markPagerSettled(token: TransitionToken, page: Int, timestampNanos: Long = clockNanos()) =
        markStage(token, TransitionStage.PAGER_SETTLED, timestampNanos) { page == token.targetPage }

    fun markFirstComposition(token: TransitionToken, timestampNanos: Long = clockNanos()) =
        markStage(token, TransitionStage.FIRST_COMPOSITION, timestampNanos)

    fun markFirstMeaningfulFrame(token: TransitionToken, timestampNanos: Long = clockNanos()) =
        markStage(token, TransitionStage.FIRST_MEANINGFUL_FRAME, timestampNanos)

    fun markDataReady(token: TransitionToken, timestampNanos: Long = clockNanos()) =
        markStage(token, TransitionStage.DATA_READY, timestampNanos)

    fun markCacheResult(
        token: TransitionToken,
        source: String,
        ageMillis: Long,
        timestampNanos: Long = clockNanos(),
    ) = synchronized(lock) {
        val state = currentStateOrRecordStale(token, TransitionEventKind.CACHE_RESULT, timestampNanos)
            ?: return@synchronized
        if (state.finished || state.cancelled || state.completedStages.containsKey(TransitionStage.CACHE_PRESENTED)) {
            return@synchronized
        }
        val previousTimestamp = state.completedStages.values.lastOrNull()
        if (previousTimestamp != null && timestampNanos < previousTimestamp) return@synchronized
        state.completedStages[TransitionStage.CACHE_PRESENTED] = timestampNanos
        append(
            TransitionPerformanceEvent(
                tokenId = token.id,
                sequence = token.sequence,
                cohort = token.cohort,
                kind = TransitionEventKind.STAGE_COMPLETED,
                stage = TransitionStage.CACHE_PRESENTED,
                timestampNanos = timestampNanos,
            ),
        )
        append(
            TransitionPerformanceEvent(
                tokenId = token.id,
                sequence = token.sequence,
                cohort = token.cohort,
                kind = TransitionEventKind.CACHE_RESULT,
                timestampNanos = timestampNanos,
                durationMillis = ageMillis.coerceAtLeast(0),
                cacheSource = sanitizeText(source),
            ),
        )
    }

    fun markBinderCall(
        token: TransitionToken,
        operation: String,
        priority: String,
        durationMillis: Long,
        result: String,
        availability: String? = null,
        timestampNanos: Long = clockNanos(),
    ) = synchronized(lock) {
        val unavailable = result.equals("unavailable", ignoreCase = true) ||
            availability.equals("unavailable", ignoreCase = true)
        if (unavailable) token.cohort = NavigationCohort.BINDER_UNAVAILABLE
        val state = currentStateOrRecordStale(token, TransitionEventKind.BINDER_CALL, timestampNanos)
            ?: return@synchronized
        if (state.finished || state.cancelled) return@synchronized
        append(
            TransitionPerformanceEvent(
                tokenId = token.id,
                sequence = token.sequence,
                cohort = token.cohort,
                kind = TransitionEventKind.BINDER_CALL,
                timestampNanos = timestampNanos,
                durationMillis = durationMillis.coerceAtLeast(0),
                operation = sanitizeText(operation),
                priority = sanitizeText(priority),
                result = sanitizeText(result),
                availability = availability?.let(::sanitizeText),
            ),
        )
    }

    fun finish(token: TransitionToken, timestampNanos: Long = clockNanos()) = synchronized(lock) {
        val state = currentStateOrRecordStale(token, TransitionEventKind.FINISHED, timestampNanos)
            ?: return@synchronized
        if (state.finished || state.cancelled) return@synchronized
        if (!isFinishable(state)) return@synchronized
        state.finished = true
        append(
            TransitionPerformanceEvent(
                tokenId = token.id,
                sequence = token.sequence,
                cohort = token.cohort,
                kind = TransitionEventKind.FINISHED,
                timestampNanos = timestampNanos,
            ),
        )
    }

    fun cancel(token: TransitionToken, timestampNanos: Long = clockNanos()) = synchronized(lock) {
        val state = currentStateOrRecordStale(token, TransitionEventKind.CANCELLED, timestampNanos)
            ?: return@synchronized
        if (state.finished || state.cancelled) return@synchronized
        state.cancelled = true
        append(
            TransitionPerformanceEvent(
                tokenId = token.id,
                sequence = token.sequence,
                cohort = token.cohort,
                kind = TransitionEventKind.CANCELLED,
                timestampNanos = timestampNanos,
            ),
        )
    }

    fun snapshot(token: TransitionToken): TransitionSnapshot? = synchronized(lock) {
        states[token.id]?.let { state ->
            TransitionSnapshot(token, state.completedStages.toMap(), state.finished, state.cancelled)
        }
    }

    /** Returns a copy; draining never performs external I/O. */
    fun events(): List<TransitionPerformanceEvent> = synchronized(lock) { bufferedEvents.toList() }

    /**
     * Returns manager navigation events in the privacy-reviewed structured format.
     * Internal state is converted here rather than exposing it as telemetry, which prevents
     * sequence/cohort/event-kind/timestamp fields from becoming an accidental public contract.
     */
    fun structuredEvents(): List<ManagerNavigationTelemetryEvent> = synchronized(lock) {
        bufferedEvents.mapNotNull { event ->
            val token = states[event.tokenId]?.token ?: return@mapNotNull null
            ManagerNavigationTelemetryEvent(
                token = sanitizeText(token.id),
                sourcePage = token.sourcePage,
                targetPage = token.targetPage,
                stage = event.stage?.name?.let(::sanitizeText),
                durationMillis = event.durationMillis,
                cacheSource = event.cacheSource?.let(::sanitizeText),
                operation = event.operation?.let(::sanitizeText),
                priority = event.priority?.let(::sanitizeText),
                result = event.result?.let(::sanitizeText),
                availability = event.availability?.let(::sanitizeText),
                process = sanitizeText(process),
                buildVariant = sanitizeText(buildVariant),
            )
        }
    }

    fun clearEvents() = synchronized(lock) { bufferedEvents.clear() }

    private fun markStage(
        token: TransitionToken,
        stage: TransitionStage,
        timestampNanos: Long,
        valid: () -> Boolean = { true },
    ) = synchronized(lock) {
        val state = currentStateOrRecordStale(token, TransitionEventKind.STAGE_COMPLETED, timestampNanos)
            ?: return@synchronized
        if (state.finished || state.cancelled || !valid()) return@synchronized
        if (state.completedStages.containsKey(stage)) return@synchronized
        val previousTimestamp = state.completedStages.values.lastOrNull()
        if (previousTimestamp != null && timestampNanos < previousTimestamp) return@synchronized
        state.completedStages[stage] = timestampNanos
        append(
            TransitionPerformanceEvent(
                tokenId = token.id,
                sequence = token.sequence,
                cohort = token.cohort,
                kind = TransitionEventKind.STAGE_COMPLETED,
                stage = stage,
                timestampNanos = timestampNanos,
            ),
        )
        if (isFinishable(state)) {
            state.finished = true
            append(
                TransitionPerformanceEvent(
                    tokenId = token.id,
                    sequence = token.sequence,
                    cohort = token.cohort,
                    kind = TransitionEventKind.FINISHED,
                    timestampNanos = timestampNanos,
                ),
            )
        }
    }

    private fun currentStateOrRecordStale(
        token: TransitionToken,
        originalKind: TransitionEventKind,
        timestampNanos: Long,
    ): MutableTransitionState? {
        val state = states[token.id]
        if (state == null || currentTokenId != token.id) {
            append(
                TransitionPerformanceEvent(
                    tokenId = token.id,
                    sequence = token.sequence,
                    cohort = token.cohort,
                    kind = TransitionEventKind.STALE_DIAGNOSTIC,
                    timestampNanos = timestampNanos,
                    result = "stale_${originalKind.name.lowercase()}",
                ),
            )
            return null
        }
        return state
    }

    private fun isFinishable(state: MutableTransitionState): Boolean {
        return state.completedStages.containsKey(TransitionStage.PAGER_SETTLED) &&
            state.completedStages.containsKey(TransitionStage.FIRST_MEANINGFUL_FRAME) &&
            (state.completedStages.containsKey(TransitionStage.CACHE_PRESENTED) ||
                state.completedStages.containsKey(TransitionStage.DATA_READY))
    }

    private fun append(event: TransitionPerformanceEvent) {
        val enriched = event.copy(
            startedAtNanos = event.startedAtNanos ?: states[event.tokenId]?.token?.startedAtNanos,
        )
        if (bufferedEvents.size == maxBufferedEvents) bufferedEvents.removeFirst()
        bufferedEvents.addLast(enriched)
    }

    private class MutableTransitionState(val token: TransitionToken) {
        val completedStages = LinkedHashMap<TransitionStage, Long>()
        var finished = false
        var cancelled = false
    }

    private fun sanitizeText(value: String): String {
        val bounded = value.take(maxTextLength)
        return if (SENSITIVE_TEXT_MARKERS.any { marker -> value.contains(marker, ignoreCase = true) } ||
            PACKAGE_NAME_PATTERN.matches(value)
        ) {
            REDACTED_TEXT.take(maxTextLength)
        } else {
            bounded
        }
    }

    companion object {
        const val DEFAULT_MAX_BUFFERED_EVENTS = 256
        const val OVERVIEW_PAGE = 0
        const val MAX_TEXT_LENGTH = 64
        const val DEFAULT_PROCESS = "manager"
        const val DEFAULT_BUILD_VARIANT = "unknown"
        const val REDACTED_TEXT = "redacted"
        private val SENSITIVE_TEXT_MARKERS = listOf(
            "payload", "event content", "application list", "binder parameter",
            "package name", "query", "filter", "user identifier",
        )
        private val PACKAGE_NAME_PATTERN = Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*){2,}""")
    }
}
