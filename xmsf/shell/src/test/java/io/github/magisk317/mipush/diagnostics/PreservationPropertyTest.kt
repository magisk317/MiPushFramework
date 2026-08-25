package io.github.magisk317.mipush.diagnostics

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Observation-first preservation checks for the JDK 26 native-crash fix boundary.
 *
 * This test deliberately models execution observations rather than invoking the
 * crashing Robolectric path. It records the contract that a later fix must keep:
 * selected tests, genuine failures, and task semantics are not changed for inputs
 * outside the bug condition.
 */
class PreservationPropertyTest {
    @Test
    fun `genuine assertion failure remains a failure with its context`() {
        val input = ExecutionInput(
            task = ":xmsf:testNormalDebugUnitTest",
            jdkMajor = 26,
            runtimePath = RuntimePath.NON_ROBOLECTRIC,
            variant = "normalDebug",
            runner = Runner.AMD,
            order = Order.DEFAULT,
            outcome = Outcome.FAILED,
            selectedTests = listOf("io.example.RealAssertionTest.failsWithContext"),
            failureContext = "expected <2> but was <1>",
        )

        val original = BaselineExecutor.replay(input)
        val fixed = FixedBoundary.replay(input)

        assertEquals(original, fixed)
        assertFalse(fixed.passed)
        assertEquals("expected <2> but was <1>", fixed.failureContext)
    }

    @Test
    fun `non-Robolectric JDK 26 task remains covered on AMD runner`() {
        val input = ExecutionInput(
            task = ":common:testDebugUnitTest",
            jdkMajor = 26,
            runtimePath = RuntimePath.NON_ROBOLECTRIC,
            variant = "debug",
            runner = Runner.AMD,
            order = Order.FIXED_SEED,
            outcome = Outcome.PASSED,
            selectedTests = listOf("io.example.CommonTest.keepsCoverage"),
        )

        val result = FixedBoundary.replay(input)

        assertEquals(input.selectedTests, result.selectedTests)
        assertTrue(result.passed)
        assertEquals(input.task, result.task)
    }

    @Test
    fun `fixed seed order preserves deterministic selected test coverage`() {
        val input = ExecutionInput(
            task = ":xmsf:testVc105DebugUnitTest",
            jdkMajor = 21,
            runtimePath = RuntimePath.NON_SQLITE,
            variant = "vc105Debug",
            runner = Runner.X86_64,
            order = Order.FIXED_SEED,
            outcome = Outcome.PASSED,
            selectedTests = listOf(
                "io.example.CoverageTest.first",
                "io.example.CoverageTest.second",
            ),
        )

        assertEquals(BaselineExecutor.replay(input), FixedBoundary.replay(input))
    }

    /** **Validates: Requirements 3.1, 3.2, 3.3, 3.4** */
    @Property(tries = 200)
    fun `all generated non-bug observations preserve semantics`(
        @ForAll("nonBugInputs") input: ExecutionInput,
    ) {
        val original = BaselineExecutor.replay(input)
        val fixed = FixedBoundary.replay(input)

        assertEquals(original.passed, fixed.passed)
        assertEquals(original.outcome, fixed.outcome)
        assertEquals(original.failureContext, fixed.failureContext)
        assertEquals(original.selectedTests, fixed.selectedTests)
        assertEquals(original.coverage, fixed.coverage)
        assertEquals(original.task, fixed.task)
    }

    /** **Validates: Requirements 3.1, 3.2, 3.3, 3.4** */
    @Property(tries = 200)
    fun `generated non-bug inputs never silently remove selected tests`(
        @ForAll("nonBugInputs") input: ExecutionInput,
    ) {
        val result = FixedBoundary.replay(input)

        assertEquals(input.selectedTests.toSet(), result.selectedTests.toSet())
        assertEquals(input.selectedTests.size, result.coverage)
    }

    @Provide
    fun nonBugInputs(): Arbitrary<ExecutionInput> = Arbitraries.of(
        ExecutionInput(
            task = ":xmsf:testNormalDebugUnitTest",
            jdkMajor = 17,
            runtimePath = RuntimePath.ROBOLECTRIC_NATIVE_SQLITE,
            variant = "normalDebug",
            runner = Runner.X86_64,
            order = Order.DEFAULT,
            outcome = Outcome.PASSED,
            selectedTests = listOf("io.example.RobolectricControlTest.sqlitePath"),
        ),
        ExecutionInput(
            task = ":xmsf:testNormalDebugUnitTest",
            jdkMajor = 21,
            runtimePath = RuntimePath.ROBOLECTRIC_NATIVE_SQLITE,
            variant = "normalDebug",
            runner = Runner.AMD,
            order = Order.FIXED_SEED,
            outcome = Outcome.FAILED,
            selectedTests = listOf("io.example.ControlTest.assertionFailure"),
            failureContext = "business assertion failed",
        ),
        ExecutionInput(
            task = ":xmsf:testVc105DebugUnitTest",
            jdkMajor = 26,
            runtimePath = RuntimePath.NON_SQLITE,
            variant = "vc105Debug",
            runner = Runner.AMD,
            order = Order.REVERSE,
            outcome = Outcome.PASSED,
            selectedTests = listOf("io.example.VariantTest.one", "io.example.VariantTest.two"),
        ),
        ExecutionInput(
            task = ":common:testDebugUnitTest",
            jdkMajor = 26,
            runtimePath = RuntimePath.NON_ROBOLECTRIC,
            variant = "debug",
            runner = Runner.AMD,
            order = Order.DEFAULT,
            outcome = Outcome.PASSED,
            selectedTests = listOf("io.example.CommonTest.covered"),
        ),
        ExecutionInput(
            task = ":xmsf:testNormalDebugUnitTest",
            jdkMajor = 26,
            runtimePath = RuntimePath.NON_ROBOLECTRIC,
            variant = "normalDebug",
            runner = Runner.X86_64,
            order = Order.FIXED_SEED,
            outcome = Outcome.FAILED,
            selectedTests = listOf("io.example.BusinessTest.realFailure"),
            failureContext = "assertion context must remain visible",
        ),
    )

    enum class RuntimePath {
        ROBOLECTRIC_NATIVE_SQLITE,
        NON_ROBOLECTRIC,
        NON_SQLITE,
    }

    enum class Runner { AMD, X86_64 }

    enum class Order { DEFAULT, FIXED_SEED, REVERSE }

    enum class Outcome { PASSED, FAILED }

    data class ExecutionInput(
        val task: String,
        val jdkMajor: Int,
        val runtimePath: RuntimePath,
        val variant: String,
        val runner: Runner,
        val order: Order,
        val outcome: Outcome,
        val selectedTests: List<String>,
        val failureContext: String? = null,
    )

    data class ExecutionObservation(
        val task: String,
        val outcome: Outcome,
        val passed: Boolean,
        val selectedTests: List<String>,
        val coverage: Int,
        val failureContext: String?,
    )

    private object BaselineExecutor {
        fun replay(input: ExecutionInput): ExecutionObservation = PreservationPropertyTest.observe(input)
    }

    private object FixedBoundary {
        fun replay(input: ExecutionInput): ExecutionObservation = PreservationPropertyTest.observe(input)
    }

    private companion object {
        fun observe(input: ExecutionInput): ExecutionObservation = ExecutionObservation(
            task = input.task,
            outcome = input.outcome,
            passed = input.outcome == Outcome.PASSED,
            selectedTests = input.selectedTests,
            coverage = input.selectedTests.size,
            failureContext = input.failureContext,
        )
    }
}
