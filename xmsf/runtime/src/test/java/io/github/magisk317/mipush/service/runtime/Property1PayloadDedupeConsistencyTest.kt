package io.github.magisk317.mipush.service.runtime

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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

    @BeforeEach
    fun setUp() {
        StockMiPushPayloadDeduper.reset()
    }

    @AfterEach
    fun tearDown() {
        StockMiPushPayloadDeduper.reset()
    }

    // --- Arbitraries ---

    private val packageNameChars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '.'

    private val packageNames: Arb<String> = Arb.list(Arb.element(packageNameChars), 3..50)
        .map { it.joinToString("") }

    private val payloads: Arb<ByteArray> = Arb.byteArray(Arb.int(1..64), Arb.byte())

    private val startTimestamps: Arb<Long> = Arb.long(1_000L..1_000_000_000L)

    private val withinWindowDeltas: Arb<Long> = Arb.long(1L..StockMiPushPayloadDeduper.DEDUP_WINDOW_MS)

    private val expiredDeltas: Arb<Long> = Arb.long(
        (StockMiPushPayloadDeduper.DEDUP_WINDOW_MS + 1)..(StockMiPushPayloadDeduper.DEDUP_WINDOW_MS * 10),
    )

    private val packagePairs: Arb<PackagePair> = Arb.bind(
        packageNames,
        packageNames,
    ) { a, b -> PackagePair(a, b) }
        .filter { it.pkgA != it.pkgB }

    private val payloadPairs: Arb<PayloadPair> = Arb.bind(
        payloads,
        payloads,
    ) { a, b -> PayloadPair(a, b) }
        .filter { !it.payloadA.contentEquals(it.payloadB) }

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
    @Test
    fun `first submission is always admitted`() {
        runBlocking {
            checkAll(300, packageNames, payloads, startTimestamps) { pkg, payload, startTime ->
                StockMiPushPayloadDeduper.reset()
                val result = StockMiPushPayloadDeduper.shouldDrop(pkg, payload, startTime, userId = 0)
                assertFalse(
                    result,
                    "First submission of pkg=[$pkg] must not be dropped",
                )
            }
        }
    }

    /**
     * Requirement 2.2: Same key within 60s window returns DUPLICATE (dropped).
     *
     * **Validates: Requirements 2.2**
     */
    @Test
    fun `same key within 60s window is dropped`() {
        runBlocking {
            checkAll(300, packageNames, payloads, startTimestamps, withinWindowDeltas) { pkg, payload, startTime, delta ->
                StockMiPushPayloadDeduper.reset()
                // First submission — admitted
                assertFalse(StockMiPushPayloadDeduper.shouldDrop(pkg, payload, startTime, userId = 0))
                // Same key within window — dropped
                val secondTime = startTime + delta
                assertTrue(
                    StockMiPushPayloadDeduper.shouldDrop(pkg, payload, secondTime, userId = 0),
                    "Same (pkg=[$pkg], payload) within ${delta}ms (<=60s) must be dropped",
                )
            }
        }
    }

    /**
     * Requirement 2.3: Same key after 60s — stock expired-hit: still DUPLICATE on first hit,
     * but entry is removed so next submission becomes NEW.
     *
     * **Validates: Requirements 2.3, 2.4**
     */
    @Test
    fun `expired hit still reports duplicate then clears entry`() {
        runBlocking {
            checkAll(300, packageNames, payloads, startTimestamps, expiredDeltas) { pkg, payload, startTime, expiredDelta ->
                StockMiPushPayloadDeduper.reset()
                // First submission — admitted
                assertFalse(StockMiPushPayloadDeduper.shouldDrop(pkg, payload, startTime, userId = 0))

                // After 60s: expired hit — still reports duplicate (stock behavior)
                val expiredTime = startTime + expiredDelta
                assertTrue(
                    StockMiPushPayloadDeduper.shouldDrop(pkg, payload, expiredTime, userId = 0),
                    "Expired hit at +${expiredDelta}ms must still report duplicate (stock behavior)",
                )

                // After expired entry removal: next same key is admitted as NEW
                assertFalse(
                    StockMiPushPayloadDeduper.shouldDrop(pkg, payload, expiredTime + 1, userId = 0),
                    "After expired entry is removed, same key must be admitted as NEW",
                )
            }
        }
    }

    /**
     * Requirement 2.5: Different payload for same package uses independent key.
     *
     * **Validates: Requirements 2.5**
     */
    @Test
    fun `different payloads for same package are independent`() {
        runBlocking {
            checkAll(300, packageNames, payloadPairs, startTimestamps) { pkg, payloads, startTime ->
                StockMiPushPayloadDeduper.reset()
                // Submit payload A
                assertFalse(StockMiPushPayloadDeduper.shouldDrop(pkg, payloads.payloadA, startTime, userId = 0))
                // Submit payload B — should be admitted independently
                assertFalse(
                    StockMiPushPayloadDeduper.shouldDrop(pkg, payloads.payloadB, startTime + 1, userId = 0),
                    "Different payload for same pkg=[$pkg] must use independent key",
                )
            }
        }
    }

    /**
     * Requirement 2.6: Same payload for different packages uses independent key.
     *
     * **Validates: Requirements 2.6**
     */
    @Test
    fun `same payload for different packages are independent`() {
        runBlocking {
            checkAll(300, packagePairs, payloads, startTimestamps) { pkgs, payload, startTime ->
                StockMiPushPayloadDeduper.reset()
                // Submit for package A
                assertFalse(StockMiPushPayloadDeduper.shouldDrop(pkgs.pkgA, payload, startTime, userId = 0))
                // Submit same payload for package B — should be admitted independently
                assertFalse(
                    StockMiPushPayloadDeduper.shouldDrop(pkgs.pkgB, payload, startTime + 1, userId = 0),
                    "Same payload for different packages (${pkgs.pkgA} vs ${pkgs.pkgB}) must use independent keys",
                )
            }
        }
    }

    /**
     * Full lifecycle property: exercises the complete dedup state machine across
     * arbitrary sequences of submissions, verifying all transitions are consistent.
     *
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6**
     */
    @Test
    fun `full dedup lifecycle is consistent across arbitrary sequences`() {
        runBlocking {
            checkAll(200, packageNames, payloads, startTimestamps, withinWindowDeltas, expiredDeltas) { pkg, payload, startTime, withinDelta, expiredDelta ->
                StockMiPushPayloadDeduper.reset()

                // Phase 1: First submission — NEW
                assertFalse(
                    StockMiPushPayloadDeduper.shouldDrop(pkg, payload, startTime, userId = 0),
                    "Phase 1: First submission must be NEW",
                )

                // Phase 2: Within window — DUPLICATE
                assertTrue(
                    StockMiPushPayloadDeduper.shouldDrop(pkg, payload, startTime + withinDelta, userId = 0),
                    "Phase 2: Within-window resubmission must be DUPLICATE",
                )

                // Phase 3: Expired hit — still DUPLICATE (stock behavior), removes entry
                val expiredTime = startTime + expiredDelta
                assertTrue(
                    StockMiPushPayloadDeduper.shouldDrop(pkg, payload, expiredTime, userId = 0),
                    "Phase 3: Expired hit must still be DUPLICATE",
                )

                // Phase 4: After removal — NEW again
                assertFalse(
                    StockMiPushPayloadDeduper.shouldDrop(pkg, payload, expiredTime + 1, userId = 0),
                    "Phase 4: After expired removal, must be NEW",
                )

                // Phase 5: New entry within window — DUPLICATE again
                assertTrue(
                    StockMiPushPayloadDeduper.shouldDrop(pkg, payload, expiredTime + 2, userId = 0),
                    "Phase 5: Within window of new entry, must be DUPLICATE again",
                )
            }
        }
    }
}
