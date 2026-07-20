package io.github.magisk317.mipush.common.process

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class BoundedProcessRunnerTest {
    @Test
    fun `captures stdout stderr and exit code`() {
        val result = BoundedProcessRunner.run(
            command = listOf("sh", "-c", "printf output; printf error >&2; exit 7"),
        )

        assertEquals(7, result.exitCode)
        assertEquals("output", result.stdout)
        assertEquals("error", result.stderr)
        assertFalse(result.isSuccess)
        assertFalse(result.timedOut)
    }

    @Test
    fun `writes standard input before awaiting completion`() {
        val result = BoundedProcessRunner.run(
            command = listOf("sh", "-c", "read value; printf '%s' \"${'$'}value\""),
            standardInput = "shared-input\n",
        )

        assertTrue(result.isSuccess)
        assertEquals("shared-input", result.stdout)
    }

    @Test
    fun `terminates a process after timeout`() {
        val startedAt = System.nanoTime()

        val result = BoundedProcessRunner.run(
            command = listOf("sh", "-c", "while :; do :; done"),
            timeoutMillis = 100,
        )

        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        assertTrue(result.timedOut)
        assertFalse(result.isSuccess)
        assertTrue(elapsedMillis < 5_000, "timeout took ${elapsedMillis}ms")
    }

    @Test
    fun `caps captured output while continuing to drain the process`() {
        val result = BoundedProcessRunner.run(
            command = listOf("sh", "-c", "yes x | head -c 4096"),
            maxOutputBytes = 1024,
        )

        assertTrue(result.isSuccess)
        assertEquals(1024, result.stdout.toByteArray().size)
        assertTrue(result.stdoutTruncated)
    }

    @Test
    fun `rejects non positive output limit`() {
        val result = BoundedProcessRunner.run(
            command = listOf("sh", "-c", "printf ignored"),
            maxOutputBytes = 0,
        )

        assertFalse(result.isSuccess)
        assertTrue(result.stderr.contains("positive"))
    }
}
