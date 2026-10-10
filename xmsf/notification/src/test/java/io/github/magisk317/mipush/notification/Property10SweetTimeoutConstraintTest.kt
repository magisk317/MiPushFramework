package io.github.magisk317.mipush.notification

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Property 10: Sweet timeout 约束
 *
 * For any timeout value, [SweetNotificationCoordinator.clampDuration] clamps
 * the result to [180, 7200] seconds. Values below 180 become 180, values
 * above 7200 become 7200, and values already in range are unchanged.
 *
 * **Validates: Requirements 5.7, 5.8**
 */
class Property10SweetTimeoutConstraintTest {

    private val anyTimeoutValues: Arb<Int> = Arb.int(Int.MIN_VALUE / 2..Int.MAX_VALUE / 2)
    private val belowMinValues: Arb<Int> = Arb.int(Int.MIN_VALUE / 2..179)
    private val aboveMaxValues: Arb<Int> = Arb.int(7201..Int.MAX_VALUE / 2)
    private val inRangeValues: Arb<Int> = Arb.int(180..7200)

    /**
     * Property: The result of clampDuration is always in [180, 7200] for any input.
     *
     * **Validates: Requirements 5.7, 5.8**
     */
    @Test
    fun `result is always in valid range for any integer input`() {
        runBlocking {
            checkAll(200, anyTimeoutValues) { timeout ->
                val result = SweetNotificationCoordinator.clampDuration(timeout)
                assertTrue(
                    result in 180..7200,
                    "clampDuration($timeout) = $result, expected in [180, 7200]",
                )
            }
        }
    }

    /**
     * Property: null input is clamped to the minimum (180).
     *
     * **Validates: Requirements 5.7, 5.8**
     */
    @Test
    fun `null input clamps to minimum`() {
        val result = SweetNotificationCoordinator.clampDuration(null)
        assertEquals(
            180,
            result,
            "clampDuration(null) should return 180 (minimum)",
        )
    }

    /**
     * Property: Values below 180 are clamped to exactly 180.
     *
     * **Validates: Requirements 5.7, 5.8**
     */
    @Test
    fun `values below minimum become 180`() {
        runBlocking {
            checkAll(200, belowMinValues) { timeout ->
                val result = SweetNotificationCoordinator.clampDuration(timeout)
                assertEquals(
                    180,
                    result,
                    "clampDuration($timeout) should be 180 for values < 180",
                )
            }
        }
    }

    /**
     * Property: Values above 7200 are clamped to exactly 7200.
     *
     * **Validates: Requirements 5.7, 5.8**
     */
    @Test
    fun `values above maximum become 7200`() {
        runBlocking {
            checkAll(200, aboveMaxValues) { timeout ->
                val result = SweetNotificationCoordinator.clampDuration(timeout)
                assertEquals(
                    7200,
                    result,
                    "clampDuration($timeout) should be 7200 for values > 7200",
                )
            }
        }
    }

    /**
     * Property: Values already in [180, 7200] are unchanged (identity).
     *
     * **Validates: Requirements 5.7, 5.8**
     */
    @Test
    fun `values in range are unchanged`() {
        runBlocking {
            checkAll(200, inRangeValues) { timeout ->
                val result = SweetNotificationCoordinator.clampDuration(timeout)
                assertEquals(
                    timeout,
                    result,
                    "clampDuration($timeout) should return the same value when in [180, 7200]",
                )
            }
        }
    }
}
