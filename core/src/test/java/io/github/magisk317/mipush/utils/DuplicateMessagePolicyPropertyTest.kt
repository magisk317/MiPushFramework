package io.github.magisk317.mipush.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Property-based tests for DuplicateMessagePolicy.
 *
 * **Validates: Requirements 1.2, 7.7**
 *
 * Property 4: Eligibility determination is deterministic —
 * verify `DuplicateMessagePolicy.checkAndMark` produces consistent results
 * for the same input sequence.
 */
class DuplicateMessagePolicyPropertyTest {

    @BeforeEach
    fun setUp() {
        DuplicateMessagePolicy.clearAllForTests()
    }

    /**
     * Property 4: Determinism — for any randomly generated sequence of
     * (messageId, timestamp) pairs, replaying the exact same sequence
     * always produces the same results.
     *
     * **Validates: Requirements 7.7**
     */
    @RepeatedTest(100)
    fun `checkAndMark is deterministic for same input sequence`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        // Generate a random sequence of calls
        val sequenceLength = rng.nextInt(5, 50)
        val messageIds = (1..5).map { "msg-$it" } + listOf(null, "", "  ")
        val calls = (1..sequenceLength).map {
            val id = messageIds[rng.nextInt(messageIds.size)]
            val timestamp = rng.nextLong(0, 200_000L)
            Pair(id, timestamp)
        }

        // First run: record results
        DuplicateMessagePolicy.clearAllForTests()
        val firstRunResults = calls.map { (id, ts) ->
            DuplicateMessagePolicy.checkAndMark(id, ts)
        }

        // Second run: replay same sequence, expect identical results
        DuplicateMessagePolicy.clearAllForTests()
        val secondRunResults = calls.map { (id, ts) ->
            DuplicateMessagePolicy.checkAndMark(id, ts)
        }

        assertEquals(
            firstRunResults,
            secondRunResults,
            "Determinism violated for seed=$seed: same input sequence produced different results"
        )
    }

    /**
     * Property 4 (extended): Determinism holds across multiple replays.
     * Running the same sequence 3 times always yields identical results.
     *
     * **Validates: Requirements 7.7**
     */
    @RepeatedTest(50)
    fun `checkAndMark determinism holds across multiple replays`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        val sequenceLength = rng.nextInt(10, 30)
        val messageIds = (1..10).map { "id-${rng.nextInt(1000)}" }
        val calls = (1..sequenceLength).map {
            val id = messageIds[rng.nextInt(messageIds.size)]
            val timestamp = rng.nextLong(0, 300_000L)
            Pair(id, timestamp)
        }

        val allResults = (1..3).map {
            DuplicateMessagePolicy.clearAllForTests()
            calls.map { (id, ts) -> DuplicateMessagePolicy.checkAndMark(id, ts) }
        }

        // All runs must produce identical results
        for (i in 1 until allResults.size) {
            assertEquals(
                allResults[0],
                allResults[i],
                "Determinism violated on run ${i + 1} for seed=$seed"
            )
        }
    }

    /**
     * Property: null/blank messageId always returns false regardless of state.
     *
     * **Validates: Requirements 1.2**
     */
    @RepeatedTest(50)
    fun `null or blank messageId always returns false`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        // Pre-populate with some random state
        repeat(rng.nextInt(0, 20)) {
            DuplicateMessagePolicy.checkAndMark("pre-${rng.nextInt(100)}", rng.nextLong(0, 100_000L))
        }

        val blankInputs = listOf(null, "", "  ", "\t", "\n")
        for (input in blankInputs) {
            val result = DuplicateMessagePolicy.checkAndMark(input, rng.nextLong(0, 200_000L))
            assertEquals(
                false,
                result,
                "Expected false for blank/null input '$input' with seed=$seed"
            )
        }
    }
}
