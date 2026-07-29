package io.github.magisk317.mipush.service.runtime

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.lifecycle.AfterProperty
import net.jqwik.api.lifecycle.BeforeProperty
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Property 1: Payload 去重一致性
 *
 * For any (action, pkg, payload, timestamps) sequence, verifies:
 * - First submission of (pkg + MD5(payload)) → not dropped (NEW), records timestamp
 * - Same key within 60s → dropped (DUPLICATE)
 * - Same key after 60s → dropped (stock expired-hit behavior) AND removes old entry
 * - After expired entry removed, next same key → not dropped (NEW)
 * - Different pkg or different payload → independent keys
 *
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6**
 */
class Property1PayloadDedupeConsistencyTest {

    @BeforeProperty
    fun setUp() {
        StockMiPushPayloadDeduper.reset()
    }

    @AfterProperty
    fun tearDown() {
        StockMiPushPayloadDeduper.reset()
    }

    // --- Arbitraries ---

    @Provide
    fun packageNames(): Arbitrary<String> = Arbitraries.strings()
        .ofMinLength(3)
        .ofMaxLength(50)
        .alpha()
        .numeric()
        .withChars('.')

    @Provide
    fun payloads(): Arbitrary<ByteArray> = Arbitraries.bytes()
        .array(ByteArray::class.java)
        .ofMinSize(1)
        .ofMaxSize(64)

    @Provide
    fun startTimestamps(): Arbitrary<Long> = Arbitraries.longs()
        .between(1_000L, 1_000_000_000L)

    @Provide
    fun withinWindowDeltas(): Arbitrary<Long> = Arbitraries.longs()
        .between(1L, StockMiPushPayloadDeduper.DEDUP_WINDOW_MS)

    @Provide
    fun expiredDeltas(): Arbitrary<Long> = Arbitraries.longs()
        .between(StockMiPushPayloadDeduper.DEDUP_WINDOW_MS + 1, StockMiPushPayloadDeduper.DEDUP_WINDOW_MS * 10)

    @Provide
    fun packagePairs(): Arbitrary<PackagePair> = Combinators.combine(
        packageNames(),
        packageNames(),
    ).filter { a, b -> a != b }
        .`as` { a, b -> PackagePair(a, b) }

    @Provide
    fun payloadPairs(): Arbitrary<PayloadPair> = Combinators.combine(
        payloads(),
        payloads(),
    ).filter { a, b -> !a.contentEquals(b) }
        .`as` { a, b -> PayloadPair(a, b) }

    data class PackagePair(val pkgA: String, val pkgB: String)
    data class PayloadPair(val payloadA: ByteArray, val payloadB: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PayloadPair) return false
            return payloadA.contentEquals(other.payloadA) && payloadB.contentEquals(other.payloadB)
        }
        override fun hashCode(): Int = 31 * payloadA.contentHashCode() + payloadB.contentHashCode()
    }

    // --- Properties ---

    /**
     * Requirement 2.1: First submission of any (pkg, payload) is admitted (not dropped).
     *
     * **Validates: Requirements 2.1**
     */
    @Property(tries = 300)
    fun `first submission is always admitted`(
        @ForAll("packageNames") pkg: String,
        @ForAll("payloads") payload: ByteArray,
        @ForAll("startTimestamps") startTime: Long,
    ) {
        StockMiPushPayloadDeduper.reset()
        val result = StockMiPushPayloadDeduper.shouldDrop(pkg, payload, startTime)
        assertFalse(
            result,
            "First submission of pkg=[$pkg] must not be dropped",
        )
    }

    /**
     * Requirement 2.2: Same key within 60s window returns DUPLICATE (dropped).
     *
     * **Validates: Requirements 2.2**
     */
    @Property(tries = 300)
    fun `same key within 60s window is dropped`(
        @ForAll("packageNames") pkg: String,
        @ForAll("payloads") payload: ByteArray,
        @ForAll("startTimestamps") startTime: Long,
        @ForAll("withinWindowDeltas") delta: Long,
    ) {
        StockMiPushPayloadDeduper.reset()
        // First submission — admitted
        assertFalse(StockMiPushPayloadDeduper.shouldDrop(pkg, payload, startTime))
        // Same key within window — dropped
        val secondTime = startTime + delta
        assertTrue(
            StockMiPushPayloadDeduper.shouldDrop(pkg, payload, secondTime),
            "Same (pkg=[$pkg], payload) within ${delta}ms (<=60s) must be dropped",
        )
    }

    /**
     * Requirement 2.3: Same key after 60s — stock expired-hit: still DUPLICATE on first hit,
     * but entry is removed so next submission becomes NEW.
     *
     * **Validates: Requirements 2.3, 2.4**
     */
    @Property(tries = 300)
    fun `expired hit still reports duplicate then clears entry`(
        @ForAll("packageNames") pkg: String,
        @ForAll("payloads") payload: ByteArray,
        @ForAll("startTimestamps") startTime: Long,
        @ForAll("expiredDeltas") expiredDelta: Long,
    ) {
        StockMiPushPayloadDeduper.reset()
        // First submission — admitted
        assertFalse(StockMiPushPayloadDeduper.shouldDrop(pkg, payload, startTime))

        // After 60s: expired hit — still reports duplicate (stock behavior)
        val expiredTime = startTime + expiredDelta
        assertTrue(
            StockMiPushPayloadDeduper.shouldDrop(pkg, payload, expiredTime),
            "Expired hit at +${expiredDelta}ms must still report duplicate (stock behavior)",
        )

        // After expired entry removal: next same key is admitted as NEW
        assertFalse(
            StockMiPushPayloadDeduper.shouldDrop(pkg, payload, expiredTime + 1),
            "After expired entry is removed, same key must be admitted as NEW",
        )
    }

    /**
     * Requirement 2.5: Different payload for same package uses independent key.
     *
     * **Validates: Requirements 2.5**
     */
    @Property(tries = 300)
    fun `different payloads for same package are independent`(
        @ForAll("packageNames") pkg: String,
        @ForAll("payloadPairs") payloads: PayloadPair,
        @ForAll("startTimestamps") startTime: Long,
    ) {
        StockMiPushPayloadDeduper.reset()
        // Submit payload A
        assertFalse(StockMiPushPayloadDeduper.shouldDrop(pkg, payloads.payloadA, startTime))
        // Submit payload B — should be admitted independently
        assertFalse(
            StockMiPushPayloadDeduper.shouldDrop(pkg, payloads.payloadB, startTime + 1),
            "Different payload for same pkg=[$pkg] must use independent key",
        )
    }

    /**
     * Requirement 2.6: Same payload for different packages uses independent key.
     *
     * **Validates: Requirements 2.6**
     */
    @Property(tries = 300)
    fun `same payload for different packages are independent`(
        @ForAll("packagePairs") pkgs: PackagePair,
        @ForAll("payloads") payload: ByteArray,
        @ForAll("startTimestamps") startTime: Long,
    ) {
        StockMiPushPayloadDeduper.reset()
        // Submit for package A
        assertFalse(StockMiPushPayloadDeduper.shouldDrop(pkgs.pkgA, payload, startTime))
        // Submit same payload for package B — should be admitted independently
        assertFalse(
            StockMiPushPayloadDeduper.shouldDrop(pkgs.pkgB, payload, startTime + 1),
            "Same payload for different packages (${pkgs.pkgA} vs ${pkgs.pkgB}) must use independent keys",
        )
    }

    /**
     * Full lifecycle property: exercises the complete dedup state machine across
     * arbitrary sequences of submissions, verifying all transitions are consistent.
     *
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6**
     */
    @Property(tries = 200)
    fun `full dedup lifecycle is consistent across arbitrary sequences`(
        @ForAll("packageNames") pkg: String,
        @ForAll("payloads") payload: ByteArray,
        @ForAll("startTimestamps") startTime: Long,
        @ForAll("withinWindowDeltas") withinDelta: Long,
        @ForAll("expiredDeltas") expiredDelta: Long,
    ) {
        StockMiPushPayloadDeduper.reset()

        // Phase 1: First submission — NEW
        assertFalse(
            StockMiPushPayloadDeduper.shouldDrop(pkg, payload, startTime),
            "Phase 1: First submission must be NEW",
        )

        // Phase 2: Within window — DUPLICATE
        assertTrue(
            StockMiPushPayloadDeduper.shouldDrop(pkg, payload, startTime + withinDelta),
            "Phase 2: Within-window resubmission must be DUPLICATE",
        )

        // Phase 3: Expired hit — still DUPLICATE (stock behavior), removes entry
        val expiredTime = startTime + expiredDelta
        assertTrue(
            StockMiPushPayloadDeduper.shouldDrop(pkg, payload, expiredTime),
            "Phase 3: Expired hit must still be DUPLICATE",
        )

        // Phase 4: After removal — NEW again
        assertFalse(
            StockMiPushPayloadDeduper.shouldDrop(pkg, payload, expiredTime + 1),
            "Phase 4: After expired removal, must be NEW",
        )

        // Phase 5: New entry within window — DUPLICATE again
        assertTrue(
            StockMiPushPayloadDeduper.shouldDrop(pkg, payload, expiredTime + 2),
            "Phase 5: Within window of new entry, must be DUPLICATE again",
        )
    }
}
