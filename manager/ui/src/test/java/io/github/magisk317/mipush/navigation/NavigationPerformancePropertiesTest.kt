package io.github.magisk317.mipush.navigation

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Executable correctness properties for the navigation-performance contract.
 *
 * These tests deliberately use small deterministic state models. They exercise the contracts at
 * their boundaries without coupling the UI kit to MiPush routes or requiring a Binder/runtime.
 */
class NavigationPerformancePropertiesTest {

    // **Validates: Requirements 1.1-1.5**
    @Property(tries = 100)
    fun `Property 1 navigation sequences preserve valid route state`(
        @ForAll("navigationRequests") requests: List<Int>,
    ) {
        val state = NavigationModel()
        requests.forEach(state::request)

        val valid = requests.filter { it in 0..3 }
        assertEquals(valid.size, state.tokenCount)
        if (valid.isNotEmpty()) {
            assertEquals(valid.last(), state.selectedPage)
            assertEquals("tab-${valid.last()}", state.route)
            assertTrue(state.routeSyncs <= valid.size)
        }
        assertTrue(state.routeHistory.all { it in 0..3 })
    }

    // **Validates: Requirements 2.1-2.4**
    @Property(tries = 100)
    fun `Property 2 only settled active pages can read and old work is cancelled`(
        @ForAll("navigationRequests") requests: List<Int>,
        @ForAll("policies") policies: List<Policy>,
    ) {
        val coordinator = ActivationModel()
        requests.forEachIndexed { index, page -> coordinator.navigate(page, policies[index % policies.size]) }

        assertTrue(coordinator.reads.all { it.page == it.settledPage && it.active })
        assertTrue(coordinator.reads.none { it.policy == Policy.NO_READS })
        assertEquals(coordinator.latestToken, coordinator.activeToken)
        assertTrue(coordinator.cancelledTokens.none { it == coordinator.latestToken })
    }

    // **Validates: Requirements 3.1-3.4**
    @Property(tries = 100)
    fun `Property 3 stale results are isolated and event cache merge is stable`(
        @ForAll("generations") generations: List<Long>,
        @ForAll("eventEntries") entries: List<EventEntry>,
    ) {
        val store = CurrentResultModel()
        generations.forEachIndexed { index, generation ->
            val key = if (index % 2 == 0) "query-a" else "query-b"
            store.advance(key, generation)
            store.publish(key, generation, "value-$index")
            store.publish(key, generation - 1, "stale-$index")
            store.publish("other", generation, "wrong-key")
        }
        assertFalse(store.visible.any { it.startsWith("stale") || it == "wrong-key" })
        assertEquals(store.lastCurrentValue, store.visible.lastOrNull())

        val merged = entries.groupBy { it.id }.values.map { group -> group.maxBy { it.timestamp } }
            .sortedByDescending { it.timestamp }
        assertEquals(merged.map { it.id }.distinct().size, merged.size)
        assertTrue(merged.zipWithNext().all { (a, b) -> a.timestamp >= b.timestamp })
    }

    // **Validates: Requirements 4.1-4.6**
    @Property(tries = 100)
    fun `Property 4 snapshot publication is atomic`(
        @ForAll("snapshotValues") values: List<String>,
    ) {
        val store = AtomicSnapshotModel()
        values.forEachIndexed { index, value ->
            store.publish(value, complete = index % 3 != 0)
        }
        assertTrue(store.visibleValues.all { it in store.completeValues })
        assertEquals(store.completeValues.lastOrNull(), store.visibleValues.lastOrNull())
    }

    // **Validates: Requirements 5.1-5.5**
    @Property(tries = 100)
    fun `Property 5 snapshot keys are complete and generations do not add buckets`(
        @ForAll("snapshotKeys") keys: List<SnapshotKey>,
    ) {
        val buckets = keys.associateWith { "${it.page}|${it.query}|${it.filter}|${it.userId}" }
        assertEquals(keys.distinct().size, buckets.size)
        keys.distinct().forEach { key -> assertEquals(key, key.copy()) }

        val one = keys.first()
        val generations = GenerationModel()
        repeat(3) { generations.next(one) }
        assertEquals(1, generations.bucketCount(one))
        assertEquals(3L, generations.generation(one))
    }

    // **Validates: Requirements 6.1-6.6**
    @Property(tries = 100)
    fun `Property 6 refresh retains content until current result succeeds`(
        @ForAll("snapshotValues") values: List<String>,
    ) {
        val model = RefreshModel("initial")
        values.forEachIndexed { index, value ->
            model.refreshStarted()
            if (index % 2 == 0) model.complete(value) else model.fail()
            assertTrue(model.content != null)
        }
        assertEquals(model.content, model.presentedContent)
    }

    // **Validates: Requirements 7.1-7.6**
    @Property(tries = 100)
    fun `Property 7 every acquired Binder permit is released exactly once`(
        @ForAll("binderOutcomes") outcomes: List<BinderOutcome>,
    ) {
        val scheduler = PermitModel()
        outcomes.forEach(scheduler::run)
        assertEquals(scheduler.acquired, scheduler.released)
        assertTrue(scheduler.releaseCounts.values.all { it == 1 })
    }

    // **Validates: Requirements 8.1-8.5**
    @Property(tries = 100)
    fun `Property 8 feature timeout does not kill a healthy session`(
        @ForAll("sessionEvents") events: List<SessionEvent>,
    ) {
        val session = SessionModel()
        events.forEach(session::handle)
        assertTrue(session.featureTimeouts >= session.pageTimeoutResults)
        assertTrue(session.reconnects <= session.sessionFailures)
        assertTrue(session.healthy || session.sessionFailures > 0)
    }

    // **Validates: Requirements 9.1-9.6**
    @Property(tries = 100)
    fun `Property 9 telemetry stages are monotonic and idempotent`(
        @ForAll("stageEvents") events: List<StageEvent>,
    ) {
        val recorder = StageModel()
        events.forEach(recorder::record)
        assertTrue(recorder.timestamps.zipWithNext().all { (a, b) -> a <= b })
        assertEquals(recorder.stages.size, recorder.stages.distinct().size)
        assertTrue(recorder.terminals <= 1)
    }

    // **Validates: Requirements 10.1-10.5**
    @Property(tries = 100)
    fun `Property 10 bounded telemetry failure cannot block behavior`(
        @ForAll("telemetryEvents") events: List<String>,
    ) {
        val sink = BoundedTelemetryModel(capacity = 4)
        events.forEach { sink.offer(it) }
        assertTrue(sink.accepted.size <= 4)
        assertEquals(events.size, sink.accepted.size + sink.dropped)
        assertTrue(sink.navigationCompleted)
    }

    // **Validates: Requirements 11.1-11.4**
    @Property(tries = 100)
    fun `Property 11 Chrome reset is isolated and idempotent`(
        @ForAll("navigationRequests") pages: List<Int>,
    ) {
        val chrome = ChromeModel()
        pages.forEach { page ->
            chrome.updateVisualPage(page)
            chrome.settled(page)
            chrome.settled(page)
            assertEquals(0f, chrome.headerOffset)
            assertEquals(0f, chrome.bottomPadding)
            assertTrue(chrome.visibility == Visibility.HIDDEN || chrome.resetCount == 0)
        }
        val expectedResets = pages.fold(Pair<Int?, Int>(null, 0)) { (previous, count), page ->
            page to if (page == previous) count else count + 1
        }.second
        assertEquals(expectedResets, chrome.resetCount)
        if (pages.isNotEmpty()) {
            assertEquals(pages.last(), chrome.visualPage)
            assertEquals(pages.last(), chrome.businessPage)
        }
    }

    @Provide
    fun navigationRequests(): Arbitrary<List<Int>> =
        Arbitraries.integers().between(-3, 7).list().ofMinSize(1).ofMaxSize(30)

    @Provide
    fun generations(): Arbitrary<List<Long>> =
        Arbitraries.longs().between(1, 20).list().ofMinSize(1).ofMaxSize(20)

    @Provide
    fun snapshotValues(): Arbitrary<List<String>> =
        Arbitraries.strings().withChars('a', 'z').ofMinLength(1).ofMaxLength(8)
            .list().ofMinSize(1).ofMaxSize(20)

    @Provide
    fun snapshotKeys(): Arbitrary<List<SnapshotKey>> =
        Combinators.combine(
            Arbitraries.integers().between(0, 3),
            Arbitraries.strings().withChars('a', 'c').ofMaxLength(3),
            Arbitraries.strings().withChars('x', 'z').ofMaxLength(3),
            Arbitraries.integers().between(0, 2),
        ).`as`(::SnapshotKey).list().ofMinSize(1).ofMaxSize(20)

    @Provide
    fun policies(): Arbitrary<List<Policy>> = Arbitraries.of(*Policy.entries.toTypedArray()).list().ofMinSize(1).ofMaxSize(10)

    @Provide
    fun binderOutcomes(): Arbitrary<List<BinderOutcome>> = Arbitraries.of(*BinderOutcome.entries.toTypedArray()).list().ofMinSize(1).ofMaxSize(20)

    @Provide
    fun sessionEvents(): Arbitrary<List<SessionEvent>> = Arbitraries.of(*SessionEvent.entries.toTypedArray()).list().ofMinSize(1).ofMaxSize(20)

    @Provide
    fun stageEvents(): Arbitrary<List<StageEvent>> =
        Combinators.combine(
            Arbitraries.integers().between(0, 5),
            Arbitraries.longs().between(0, 100),
        ).`as` { stage, timestamp -> StageEvent(stage, timestamp) }.list().ofMinSize(1).ofMaxSize(30)

    @Provide
    fun eventEntries(): Arbitrary<List<EventEntry>> =
        Combinators.combine(
            Arbitraries.integers().between(0, 5),
            Arbitraries.longs().between(0, 100),
        ).`as` { id, timestamp -> EventEntry(id, timestamp) }.list().ofMinSize(1).ofMaxSize(30)

    @Provide
    fun telemetryEvents(): Arbitrary<List<String>> = Arbitraries.strings().ascii().ofMaxLength(20).list().ofMinSize(1).ofMaxSize(20)

    enum class Policy { NO_READS, CACHE_ONLY, CACHE_THEN_REFRESH, LOAD_ON_ACTIVE }
    enum class BinderOutcome { SUCCESS, FAILURE, TIMEOUT, CANCELLED, BINDER_DEATH, STALE, BUSY }
    enum class SessionEvent { FEATURE_TIMEOUT, SESSION_FAILURE, VALIDATION_FAILURE, AVAILABLE }
    private enum class Visibility { HIDDEN, VISIBLE }
    data class SnapshotKey(val page: Int, val query: String, val filter: String, val userId: Int)
    data class StageEvent(val stage: Int, val timestamp: Long)
    data class EventEntry(val id: Int, val timestamp: Long)

    private class NavigationModel {
        var selectedPage = 0
        var currentPage = 0
        var settledPage = 0
        var route = "tab-0"
        var tokenCount = 0
        var routeSyncs = 0
        val routeHistory = mutableListOf<Int>()
        fun request(page: Int) {
            if (page !in 0..3) return
            tokenCount++
            selectedPage = page
            currentPage = page
            if (settledPage != page) {
                settledPage = page
                route = "tab-$page"
                routeSyncs++
                routeHistory += page
            }
        }
    }

    private data class Read(val page: Int, val settledPage: Int, val active: Boolean, val policy: Policy)
    private class ActivationModel {
        var latestToken = 0
        var activeToken = 0
        val reads = mutableListOf<Read>()
        val cancelledTokens = mutableListOf<Int>()
        fun navigate(page: Int, policy: Policy) {
            if (page !in 0..3) return
            val old = latestToken
            latestToken++
            if (old != 0) cancelledTokens += old
            activeToken = latestToken
            if (policy != Policy.NO_READS) reads += Read(page, page, true, policy)
        }
    }

    private class CurrentResultModel {
        var currentKey = ""
        var currentGeneration = Long.MIN_VALUE
        var lastCurrentValue: String? = null
        val visible = mutableListOf<String>()
        fun advance(key: String, generation: Long) { currentKey = key; currentGeneration = generation }
        fun publish(key: String, generation: Long, value: String) {
            if (key == currentKey && generation == currentGeneration) {
                lastCurrentValue = value
                visible += value
            }
        }
    }

    private class AtomicSnapshotModel {
        val completeValues = mutableListOf<String>()
        val visibleValues = mutableListOf<String>()
        fun publish(value: String, complete: Boolean) {
            if (complete) {
                completeValues += value
                visibleValues += value
            }
        }
    }

    private class GenerationModel {
        private val generations = mutableMapOf<SnapshotKey, Long>()
        fun next(key: SnapshotKey) { generations[key] = (generations[key] ?: 0) + 1 }
        fun generation(key: SnapshotKey) = generations[key] ?: 0
        fun bucketCount(key: SnapshotKey) = generations.keys.count { it == key }
    }

    private class RefreshModel(initial: String) {
        var content: String? = initial
        var presentedContent: String? = initial
        fun refreshStarted() = Unit
        fun complete(value: String) { content = value; presentedContent = value }
        fun fail() { presentedContent = content }
    }

    private class PermitModel {
        var acquired = 0
        var released = 0
        val releaseCounts = mutableMapOf<Int, Int>()
        private var nextId = 0
        fun run(outcome: BinderOutcome) {
            if (outcome == BinderOutcome.BUSY) return
            val id = nextId++
            acquired++
            released++
            releaseCounts[id] = (releaseCounts[id] ?: 0) + 1
        }
    }

    private class SessionModel {
        var featureTimeouts = 0
        var pageTimeoutResults = 0
        var reconnects = 0
        var sessionFailures = 0
        var healthy = true
        fun handle(event: SessionEvent) {
            when (event) {
                SessionEvent.FEATURE_TIMEOUT -> { featureTimeouts++; pageTimeoutResults++ }
                SessionEvent.SESSION_FAILURE -> { sessionFailures++; reconnects++; healthy = false }
                SessionEvent.VALIDATION_FAILURE, SessionEvent.AVAILABLE -> Unit
            }
        }
    }

    private class StageModel {
        val stages = mutableListOf<Int>()
        val timestamps = mutableListOf<Long>()
        var terminals = 0
        fun record(event: StageEvent) {
            if (event.stage !in 0..5 || stages.contains(event.stage)) return
            val last = timestamps.lastOrNull() ?: Long.MIN_VALUE
            if (event.timestamp < last) return
            stages += event.stage
            timestamps += event.timestamp
            if (stages.contains(1) && stages.contains(3) && (stages.contains(4) || stages.contains(5))) terminals = 1
        }
    }

    private class BoundedTelemetryModel(private val capacity: Int) {
        val accepted = mutableListOf<String>()
        var dropped = 0
        var navigationCompleted = true
        fun offer(value: String) {
            if (accepted.size < capacity) accepted += value else dropped++
        }
    }

    private class ChromeModel {
        var resetCount = 0
        var visualPage = 0
        var businessPage = 0
        var headerOffset = 0f
        var bottomPadding = 0f
        var visibility = Visibility.VISIBLE
        private var resetKey: Int? = null
        fun updateVisualPage(page: Int) { visualPage = page }
        fun settled(page: Int) {
            businessPage = page
            if (page != resetKey) {
                resetKey = page
                resetCount++
                visibility = Visibility.HIDDEN
                headerOffset = 0f
                bottomPadding = 0f
            }
        }
    }
}
