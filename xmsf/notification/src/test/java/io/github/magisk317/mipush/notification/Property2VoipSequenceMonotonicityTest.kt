package io.github.magisk317.mipush.notification

import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
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
 * Property 2: VoIP 序列单调性
 *
 * For any package name and any interleaved sequence of (pkg, sequence) pairs,
 * VoIP sequence filtering satisfies:
 * - sequence > stored → allowed (not dropped) and updates stored
 * - sequence < stored → suppressed (dropped)
 * - sequence == stored → allowed (stock behavior)
 * - Per-package HashMap with process-lifetime retention, no eviction even beyond 128 packages
 *
 * **Validates: Requirements 4.5, 4.6, 4.7, 4.8**
 */
class Property2VoipSequenceMonotonicityTest {

    @BeforeEach
    fun setUp() {
        VoipNotificationHelper.resetForTest()
    }

    @AfterEach
    fun tearDown() {
        VoipNotificationHelper.resetForTest()
    }

    // -- Arbitraries --

    private val packageNameChars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '.'

    private val packageNames: Arb<String> = Arb.list(Arb.element(packageNameChars), 1..60)
        .map { it.joinToString("") }

    private val sequences: Arb<Long> = Arb.long(0L..Long.MAX_VALUE / 2)

    private val sequencePairs: Arb<SequencePair> = Arb.bind(
        sequences,
        sequences,
    ) { a, b -> a to b }
        .filter { (a, b) -> a != b }
        .map { (a, b) ->
            if (a < b) SequencePair(a, b) else SequencePair(b, a)
        }

    private val packagePairs: Arb<PackagePair> = Arb.bind(
        packageNames,
        packageNames,
    ) { a, b -> PackagePair(a, b) }
        .filter { it.pkgA != it.pkgB }

    private val interleavedSequences: Arb<List<Long>> = Arb.list(Arb.long(0L..10000L), 2..30)

    private val manyPackageNames: Arb<List<String>> = Arb.list(
        Arb.int(0..255).map { i -> "eviction.test.pkg.$i" },
        150..150,
    )

    data class SequencePair(val lower: Long, val higher: Long)
    data class PackagePair(val pkgA: String, val pkgB: String)

    // -- Properties --

    /**
     * Property: A sequence greater than stored is allowed (not dropped) and updates stored.
     * Subsequent lower sequences are then suppressed.
     *
     * **Validates: Requirements 4.5, 4.6, 4.7, 4.8**
     */
    @Test
    fun `greater sequence is allowed and updates stored`() {
        runBlocking {
            checkAll(200, packageNames, sequencePairs) { pkg, pair ->
                VoipNotificationHelper.resetForTest()

                // First sequence establishes the baseline
                val firstResult = shouldDropStaleForTest(voipMeta(pair.lower), pkg)
                assertFalse(
                    firstResult,
                    "First sequence=${pair.lower} for pkg=[$pkg] must be allowed",
                )

                // Higher sequence must be allowed
                val higherResult = shouldDropStaleForTest(voipMeta(pair.higher), pkg)
                assertFalse(
                    higherResult,
                    "Higher sequence=${pair.higher} > stored=${pair.lower} for pkg=[$pkg] must be allowed",
                )

                // Now the lower sequence must be suppressed (stored updated to pair.higher)
                val staleResult = shouldDropStaleForTest(voipMeta(pair.lower), pkg)
                assertTrue(
                    staleResult,
                    "Lower sequence=${pair.lower} < stored=${pair.higher} for pkg=[$pkg] must be suppressed",
                )
            }
        }
    }

    /**
     * Property: A sequence less than stored is suppressed (dropped).
     *
     * **Validates: Requirements 4.5, 4.6, 4.7, 4.8**
     */
    @Test
    fun `lower sequence is suppressed`() {
        runBlocking {
            checkAll(200, packageNames, sequencePairs) { pkg, pair ->
                VoipNotificationHelper.resetForTest()

                // Establish higher stored value first
                shouldDropStaleForTest(voipMeta(pair.higher), pkg)

                // Lower sequence must be suppressed
                val result = shouldDropStaleForTest(voipMeta(pair.lower), pkg)
                assertTrue(
                    result,
                    "Sequence=${pair.lower} < stored=${pair.higher} for pkg=[$pkg] must be suppressed",
                )
            }
        }
    }

    /**
     * Property: A sequence equal to stored is always allowed (stock behavior).
     *
     * **Validates: Requirements 4.5, 4.6, 4.7, 4.8**
     */
    @Test
    fun `equal sequence is always allowed`() {
        runBlocking {
            checkAll(200, packageNames, sequences) { pkg, sequence ->
                VoipNotificationHelper.resetForTest()

                // Establish stored sequence
                shouldDropStaleForTest(voipMeta(sequence), pkg)

                // Same sequence must be allowed (stock behavior: equal is not dropped)
                val result = shouldDropStaleForTest(voipMeta(sequence), pkg)
                assertFalse(
                    result,
                    "Equal sequence=$sequence for pkg=[$pkg] must be allowed (stock behavior)",
                )
            }
        }
    }

    /**
     * Property: Per-package independence — changes to package A don't affect package B.
     *
     * **Validates: Requirements 4.5, 4.6, 4.7, 4.8**
     */
    @Test
    fun `per-package sequence tracking is independent`() {
        runBlocking {
            checkAll(200, packagePairs, sequencePairs) { pair, seqs ->
                VoipNotificationHelper.resetForTest()

                // Establish high sequence for package A
                shouldDropStaleForTest(voipMeta(seqs.higher), pair.pkgA)

                // Package B should still allow a low sequence (independent state)
                val resultB = shouldDropStaleForTest(voipMeta(seqs.lower), pair.pkgB)
                assertFalse(
                    resultB,
                    "pkg=[${pair.pkgB}] should be independent of pkg=[${pair.pkgA}]'s stored " +
                        "sequence=${seqs.higher}. Sequence=${seqs.lower} must be allowed.",
                )
            }
        }
    }

    /**
     * Property: HashMap does not evict entries — even with >128 packages,
     * the original package's state is retained.
     *
     * **Validates: Requirements 4.5, 4.6, 4.7, 4.8**
     */
    @Test
    fun `no eviction even with many packages`() {
        runBlocking {
            checkAll(100, manyPackageNames) { otherPkgs ->
                VoipNotificationHelper.resetForTest()

                val targetPkg = "original.target.pkg"
                val storedSeq = 100L

                // Establish sequence for target package
                shouldDropStaleForTest(voipMeta(storedSeq), targetPkg)

                // Insert many other packages (>128, testing no LRU)
                for (pkg in otherPkgs) {
                    shouldDropStaleForTest(voipMeta(1L), pkg)
                }

                // Target package state must be retained — lower sequence still suppressed
                val staleResult = shouldDropStaleForTest(voipMeta(storedSeq - 1), targetPkg)
                assertTrue(
                    staleResult,
                    "After inserting ${otherPkgs.size} other packages, " +
                        "target pkg=[$targetPkg] must still retain stored sequence=$storedSeq. " +
                        "Sequence=${storedSeq - 1} must be suppressed.",
                )
            }
        }
    }

    /**
     * Property: For any interleaved sequence of values, the filter behavior is consistent
     * with monotonic tracking — at any point, the stored value is the maximum seen so far,
     * and only sequences strictly less than it are suppressed.
     *
     * **Validates: Requirements 4.5, 4.6, 4.7, 4.8**
     */
    @Test
    fun `interleaved sequences produce monotonic stored state`() {
        runBlocking {
            checkAll(200, packageNames, interleavedSequences) { pkg, sequences ->
                VoipNotificationHelper.resetForTest()

                var maxSeen = 0L // initial stored value is 0

                for (seq in sequences) {
                    val shouldDrop = shouldDropStaleForTest(voipMeta(seq), pkg)

                    if (maxSeen > seq) {
                        // Stale: stored > incoming → should be dropped
                        assertTrue(
                            shouldDrop,
                            "sequence=$seq < maxSeen=$maxSeen for pkg=[$pkg] must be suppressed",
                        )
                    } else {
                        // seq >= maxSeen: allowed (both greater and equal)
                        assertFalse(
                            shouldDrop,
                            "sequence=$seq >= maxSeen=$maxSeen for pkg=[$pkg] must be allowed",
                        )
                        maxSeen = seq
                    }
                }
            }
        }
    }

    // -- Helper --

    private fun shouldDropStaleForTest(
        metaInfo: PushMetaInfo,
        packageName: String,
    ): Boolean = VoipNotificationHelper.shouldDropStale(metaInfo, packageName, userId = 0)

    private fun voipMeta(sequence: Long): PushMetaInfo {
        return PushMetaInfo().apply {
            extra = mutableMapOf(
                "msg_busi_type" to "voip",
                "sequence" to sequence.toString(),
            )
        }
    }
}
