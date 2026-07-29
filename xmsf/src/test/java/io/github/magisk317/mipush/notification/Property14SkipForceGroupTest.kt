package io.github.magisk317.mipush.notification

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
 * Property 14: skipForceGroup 分组旁路
 *
 * For any notification on Android 16+, when carrying the miui_skipForceGroup flag,
 * force grouping is skipped; when not carrying the flag and conditions are met,
 * grouping is applied per strategy.
 *
 * **Validates: Requirements 15.2, 15.3**
 *
 * Tests the pure decision function [AndroidWGroupStrategy.shouldSkipForceGroup]:
 * - strategy=2 → always true (skip force grouping)
 * - strategy=3 → always false (allow force grouping)
 * - strategy=1 or any other value → true only if sourceGroup is non-null and non-empty
 */
class Property14SkipForceGroupTest {

    @Provide
    fun skipForceGroupInputs(): Arbitrary<SkipForceGroupInput> = Combinators.combine(
        sourceGroups(),
        strategies(),
    ).`as` { group, strategy -> SkipForceGroupInput(group, strategy) }

    @Provide
    fun sourceGroups(): Arbitrary<String?> = Arbitraries.oneOf(
        Arbitraries.just(null),
        Arbitraries.just(""),
        Arbitraries.strings().ofMinLength(1).ofMaxLength(50).alpha(),
    )

    @Provide
    fun strategies(): Arbitrary<Int> = Arbitraries.oneOf(
        Arbitraries.of(1, 2, 3),
        Arbitraries.integers().between(-10, 100),
    )

    data class SkipForceGroupInput(
        val sourceGroup: String?,
        val strategy: Int,
    )

    /**
     * Property: strategy=2 always skips force grouping regardless of sourceGroup.
     *
     * **Validates: Requirements 15.2, 15.3**
     */
    @Property(tries = 200)
    fun `strategy 2 always skips force grouping`(
        @ForAll("sourceGroups") sourceGroup: String?,
    ) {
        assertTrue(
            AndroidWGroupStrategy.shouldSkipForceGroup(sourceGroup, 2),
            "Strategy 2 must always skip force grouping, sourceGroup=$sourceGroup",
        )
    }

    /**
     * Property: strategy=3 never skips force grouping regardless of sourceGroup.
     *
     * **Validates: Requirements 15.2, 15.3**
     */
    @Property(tries = 200)
    fun `strategy 3 never skips force grouping`(
        @ForAll("sourceGroups") sourceGroup: String?,
    ) {
        assertFalse(
            AndroidWGroupStrategy.shouldSkipForceGroup(sourceGroup, 3),
            "Strategy 3 must never skip force grouping, sourceGroup=$sourceGroup",
        )
    }

    /**
     * Property: for strategy=1 (default) and any other strategy value,
     * shouldSkipForceGroup returns true iff sourceGroup is non-null and non-empty.
     *
     * **Validates: Requirements 15.2, 15.3**
     */
    @Property(tries = 200)
    fun `default strategy skips only when sourceGroup is present`(
        @ForAll("skipForceGroupInputs") input: SkipForceGroupInput,
    ) {
        // Only test strategies that fall into the "else" branch (not 2 or 3)
        val strategy = if (input.strategy == 2 || input.strategy == 3) 1 else input.strategy
        val expected = !input.sourceGroup.isNullOrEmpty()
        assertEquals(
            expected,
            AndroidWGroupStrategy.shouldSkipForceGroup(input.sourceGroup, strategy),
            "Default/fallback strategy (value=$strategy) must skip iff sourceGroup is " +
                "non-null and non-empty. sourceGroup=${input.sourceGroup}",
        )
    }

    /**
     * Property: the decision function is consistent — for any (sourceGroup, strategy)
     * the result always matches the documented specification.
     *
     * **Validates: Requirements 15.2, 15.3**
     */
    @Property(tries = 300)
    fun `shouldSkipForceGroup matches specification for all inputs`(
        @ForAll("skipForceGroupInputs") input: SkipForceGroupInput,
    ) {
        val expected = when (input.strategy) {
            2 -> true
            3 -> false
            else -> !input.sourceGroup.isNullOrEmpty()
        }
        assertEquals(
            expected,
            AndroidWGroupStrategy.shouldSkipForceGroup(input.sourceGroup, input.strategy),
            "Mismatch for sourceGroup=${input.sourceGroup}, strategy=${input.strategy}",
        )
    }
}
