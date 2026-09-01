package io.github.magisk317.mipush.notification

import com.xiaomi.xmpush.thrift.PushMetaInfo
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

    @BeforeProperty
    fun setUp() {
        VoipNotificationHelper.resetForTest()
    }

    @AfterProperty
    fun tearDown() {
        VoipNotificationHelper.resetForTest()
    }

    // -- Arbitraries --

    @Provide
    fun packageNames(): Arbitrary<String> = Arbitraries.strings()
        .ofMinLength(1)
        .ofMaxLength(60)
        .alpha()
        .numeric()
        .withChars('.')

    @Provide
    fun sequences(): Arbitrary<Long> = Arbitraries.longs().between(0, Long.MAX_VALUE / 2)

    @Provide
    fun sequencePairs(): Arbitrary<SequencePair> = Combinators.combine(
        sequences(),
        sequences(),
    ).filter { a, b -> a != b }
        .`as` { a, b ->
            if (a < b) SequencePair(a, b) else SequencePair(b, a)
        }

    @Provide
    fun packagePairs(): Arbitrary<PackagePair> = Combinators.combine(
        packageNames(),
        packageNames(),
    ).filter { a, b -> a != b }
        .`as` { a, b -> PackagePair(a, b) }

    @Provide
    fun interleavedSequences(): Arbitrary<List<Long>> = Arbitraries.longs()
        .between(0, 10000)
        .list()
        .ofMinSize(2)
        .ofMaxSize(30)

    @Provide
    fun manyPackageNames(): Arbitrary<List<String>> = Arbitraries.integers()
        .between(0, 255)
        .map { i -> "eviction.test.pkg.$i" }
        .list()
        .ofSize(150)

    data class SequencePair(val lower: Long, val higher: Long)
    data class PackagePair(val pkgA: String, val pkgB: String)

    // -- Properties --

    /**
     * Property: A sequence greater than stored is allowed (not dropped) and updates stored.
     * Subsequent lower sequences are then suppressed.
     *
     * **Validates: Requirements 4.5, 4.6, 4.7, 4.8**
     */
    @Property(tries = 200)
    fun `greater sequence is allowed and updates stored`(
        @ForAll("packageNames") pkg: String,
        @ForAll("sequencePairs") pair: SequencePair,
    ) {
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

    /**
     * Property: A sequence less than stored is suppressed (dropped).
     *
     * **Validates: Requirements 4.5, 4.6, 4.7, 4.8**
     */
    @Property(tries = 200)
    fun `lower sequence is suppressed`(
        @ForAll("packageNames") pkg: String,
        @ForAll("sequencePairs") pair: SequencePair,
    ) {
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

    /**
     * Property: A sequence equal to stored is always allowed (stock behavior).
     *
     * **Validates: Requirements 4.5, 4.6, 4.7, 4.8**
     */
    @Property(tries = 200)
    fun `equal sequence is always allowed`(
        @ForAll("packageNames") pkg: String,
        @ForAll("sequences") sequence: Long,
    ) {
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

    /**
     * Property: Per-package independence — changes to package A don't affect package B.
     *
     * **Validates: Requirements 4.5, 4.6, 4.7, 4.8**
     */
    @Property(tries = 200)
    fun `per-package sequence tracking is independent`(
        @ForAll("packagePairs") pair: PackagePair,
        @ForAll("sequencePairs") seqs: SequencePair,
    ) {
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

    /**
     * Property: HashMap does not evict entries — even with >128 packages,
     * the original package's state is retained.
     *
     * **Validates: Requirements 4.5, 4.6, 4.7, 4.8**
     */
    @Property(tries = 100)
    fun `no eviction even with many packages`(
        @ForAll("manyPackageNames") otherPkgs: List<String>,
    ) {
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

    /**
     * Property: For any interleaved sequence of values, the filter behavior is consistent
     * with monotonic tracking — at any point, the stored value is the maximum seen so far,
     * and only sequences strictly less than it are suppressed.
     *
     * **Validates: Requirements 4.5, 4.6, 4.7, 4.8**
     */
    @Property(tries = 200)
    fun `interleaved sequences produce monotonic stored state`(
        @ForAll("packageNames") pkg: String,
        @ForAll("interleavedSequences") sequences: List<Long>,
    ) {
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
