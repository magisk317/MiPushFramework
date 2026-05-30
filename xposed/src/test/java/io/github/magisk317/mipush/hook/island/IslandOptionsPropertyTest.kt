package io.github.magisk317.mipush.hook.island

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Property-based exploration test for the enableFloat bug condition.
 *
 * **Validates: Requirements 1.1, 1.2, 1.3**
 *
 * Bug Condition: When enabled=true AND focusNotification=true AND enableFloat=false,
 * canInjectFocusPayload SHOULD return false (user disabled floating display).
 *
 * This test exhaustively enumerates all combinations of the remaining parameters
 * (timeoutSecs, firstFloat, showNotification) to confirm the property holds universally.
 *
 * EXPECTED: This test FAILS on unfixed code — failure confirms the bug exists.
 */
class IslandOptionsPropertyTest {

    /**
     * Property 1: Bug Condition — enableFloat=false implies canInjectFocusPayload=false
     *
     * For ALL inputs satisfying: enabled=true AND focusNotification=true AND enableFloat=false,
     * canInjectFocusPayload must return false.
     */
    @TestFactory
    fun `property - enableFloat=false should prevent focus payload injection`(): List<DynamicTest> {
        val timeoutValues = listOf(1, 5, 10, 60)
        val booleans = listOf(true, false)

        return timeoutValues.flatMap { timeout ->
            booleans.flatMap { firstFloat ->
                booleans.map { showNotification ->
                    val options = IslandOptions(
                        enabled = true,
                        timeoutSecs = timeout,
                        firstFloat = firstFloat,
                        enableFloat = false,  // Bug condition: float disabled
                        showNotification = showNotification,
                        focusNotification = true,
                    )
                    DynamicTest.dynamicTest(
                        "canInjectFocusPayload=false when enableFloat=false " +
                            "(timeout=$timeout, firstFloat=$firstFloat, showNotification=$showNotification)"
                    ) {
                        assertFalse(options.canInjectFocusPayload) {
                            "BUG: canInjectFocusPayload returned true when enableFloat=false. " +
                                "Options: $options"
                        }
                    }
                }
            }
        }
    }

    // ========== Preservation Property Tests ==========

    /**
     * Property 2a: Preservation — enabled=false implies canInjectFocusPayload=false
     *
     * **Validates: Requirements 3.2**
     *
     * For ALL combinations where enabled=false, canInjectFocusPayload must return false,
     * regardless of enableFloat value.
     */
    @TestFactory
    fun `preservation - enabled=false always prevents focus payload injection`(): List<DynamicTest> {
        val timeoutValues = listOf(1, 5, 10)
        val booleans = listOf(true, false)

        return timeoutValues.flatMap { timeout ->
            booleans.flatMap { firstFloat ->
                booleans.flatMap { enableFloat ->
                    booleans.flatMap { showNotification ->
                        booleans.map { focusNotification ->
                            val options = IslandOptions(
                                enabled = false,  // Preservation: main switch disabled
                                timeoutSecs = timeout,
                                firstFloat = firstFloat,
                                enableFloat = enableFloat,
                                showNotification = showNotification,
                                focusNotification = focusNotification,
                            )
                            DynamicTest.dynamicTest(
                                "canInjectFocusPayload=false when enabled=false " +
                                    "(timeout=$timeout, firstFloat=$firstFloat, enableFloat=$enableFloat, " +
                                    "showNotification=$showNotification, focusNotification=$focusNotification)"
                            ) {
                                assertFalse(options.canInjectFocusPayload) {
                                    "REGRESSION: canInjectFocusPayload returned true when enabled=false. " +
                                        "Options: $options"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Property 2b: Preservation — focusNotification=false implies canInjectFocusPayload=false
     *
     * **Validates: Requirements 3.3**
     *
     * For ALL combinations where focusNotification=false, canInjectFocusPayload must return false,
     * regardless of enableFloat value.
     */
    @TestFactory
    fun `preservation - focusNotification=false always prevents focus payload injection`(): List<DynamicTest> {
        val timeoutValues = listOf(1, 5, 10)
        val booleans = listOf(true, false)

        return timeoutValues.flatMap { timeout ->
            booleans.flatMap { firstFloat ->
                booleans.flatMap { enableFloat ->
                    booleans.map { showNotification ->
                        val options = IslandOptions(
                            enabled = true,
                            timeoutSecs = timeout,
                            firstFloat = firstFloat,
                            enableFloat = enableFloat,
                            showNotification = showNotification,
                            focusNotification = false,  // Preservation: focus notification disabled
                        )
                        DynamicTest.dynamicTest(
                            "canInjectFocusPayload=false when focusNotification=false " +
                                "(timeout=$timeout, firstFloat=$firstFloat, enableFloat=$enableFloat, " +
                                "showNotification=$showNotification)"
                        ) {
                            assertFalse(options.canInjectFocusPayload) {
                                "REGRESSION: canInjectFocusPayload returned true when focusNotification=false. " +
                                    "Options: $options"
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Property 2c: Preservation — enableFloat=true AND enabled=true AND focusNotification=true
     * implies canInjectFocusPayload=true
     *
     * **Validates: Requirements 3.1**
     *
     * For ALL combinations where enableFloat=true, enabled=true, and focusNotification=true,
     * canInjectFocusPayload must return true.
     */
    @TestFactory
    fun `preservation - all conditions met should allow focus payload injection`(): List<DynamicTest> {
        val timeoutValues = listOf(1, 5, 10)
        val booleans = listOf(true, false)

        return timeoutValues.flatMap { timeout ->
            booleans.flatMap { firstFloat ->
                booleans.map { showNotification ->
                    val options = IslandOptions(
                        enabled = true,
                        timeoutSecs = timeout,
                        firstFloat = firstFloat,
                        enableFloat = true,  // Preservation: float enabled
                        showNotification = showNotification,
                        focusNotification = true,
                    )
                    DynamicTest.dynamicTest(
                        "canInjectFocusPayload=true when enableFloat=true, enabled=true, focusNotification=true " +
                            "(timeout=$timeout, firstFloat=$firstFloat, showNotification=$showNotification)"
                    ) {
                        assertTrue(options.canInjectFocusPayload) {
                            "REGRESSION: canInjectFocusPayload returned false when all conditions met. " +
                                "Options: $options"
                        }
                    }
                }
            }
        }
    }
}
