package io.github.magisk317.mipush.notification

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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

    private val sourceGroups: Arb<String?> = Arb.choice<String?>(
        Arb.constant(null),
        Arb.constant(""),
        Arb.list(Arb.element(('a'..'z') + ('A'..'Z')), 1..50).map { it.joinToString("") },
    )

    private val strategies: Arb<Int> = Arb.choice(
        Arb.element(1, 2, 3),
        Arb.int(-10..100),
    )

    private val skipForceGroupInputs: Arb<SkipForceGroupInput> = Arb.bind(
        sourceGroups,
        strategies,
    ) { group, strategy -> SkipForceGroupInput(group, strategy) }

    data class SkipForceGroupInput(
        val sourceGroup: String?,
        val strategy: Int,
    )

    /**
     * Property: strategy=2 always skips force grouping regardless of sourceGroup.
     *
     * **Validates: Requirements 15.2, 15.3**
     */
    @Test
    fun `strategy 2 always skips force grouping`() {
        runBlocking {
            checkAll(200, sourceGroups) { sourceGroup ->
                assertTrue(
                    AndroidWGroupStrategy.shouldSkipForceGroup(sourceGroup, 2),
                    "Strategy 2 must always skip force grouping, sourceGroup=$sourceGroup",
                )
            }
        }
    }

    /**
     * Property: strategy=3 never skips force grouping regardless of sourceGroup.
     *
     * **Validates: Requirements 15.2, 15.3**
     */
    @Test
    fun `strategy 3 never skips force grouping`() {
        runBlocking {
            checkAll(200, sourceGroups) { sourceGroup ->
                assertFalse(
                    AndroidWGroupStrategy.shouldSkipForceGroup(sourceGroup, 3),
                    "Strategy 3 must never skip force grouping, sourceGroup=$sourceGroup",
                )
            }
        }
    }

    /**
     * Property: for strategy=1 (default) and any other strategy value,
     * shouldSkipForceGroup returns true iff sourceGroup is non-null and non-empty.
     *
     * **Validates: Requirements 15.2, 15.3**
     */
    @Test
    fun `default strategy skips only when sourceGroup is present`() {
        runBlocking {
            checkAll(200, skipForceGroupInputs) { input ->
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
        }
    }

    /**
     * Property: the decision function is consistent — for any (sourceGroup, strategy)
     * the result always matches the documented specification.
     *
     * **Validates: Requirements 15.2, 15.3**
     */
    @Test
    fun `shouldSkipForceGroup matches specification for all inputs`() {
        runBlocking {
            checkAll(300, skipForceGroupInputs) { input ->
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
    }
}
