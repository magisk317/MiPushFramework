package io.github.magisk317.mipush.platform.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RootAccessFacadeTest {
    @Test
    fun `root command is skipped when grant state is unknown and no cached grant exists`() {
        val runner = RecordingRunner()
        val facade = RootAccessFacade(
            runner = runner,
            rootGrantState = { null },
            requestRootGrant = {},
        )

        val result = facade.runRootCommand("id")

        assertTrue(result.skipped)
        assertEquals(emptyList<String>(), runner.commands)
    }

    @Test
    fun `request root probes access and caches success`() {
        var requested = false
        val runner = RecordingRunner(
            "id -u" to BoundedShellResult(0, stdout = listOf("0")),
        )
        val facade = RootAccessFacade(
            runner = runner,
            rootGrantState = { true },
            requestRootGrant = { requested = true },
        )

        assertTrue(facade.requestRootAccess())

        assertTrue(requested)
        assertTrue(facade.hasCachedRootAccess())
        assertEquals(listOf("id -u"), runner.commands)
    }

    @Test
    fun `root command returns stderr and timeout from bounded runner`() {
        val runner = RecordingRunner(
            "id -u" to BoundedShellResult(0, stdout = listOf("0")),
            "broken" to BoundedShellResult(exitCode = 2, stderr = listOf("denied")),
            "sleep" to BoundedShellResult.timedOut(),
        )
        val facade = RootAccessFacade(
            runner = runner,
            rootGrantState = { true },
            requestRootGrant = {},
        )

        val denied = facade.runRootCommand("broken")
        val timedOut = facade.runRootCommand("sleep")

        assertFalse(denied.isSuccess)
        assertEquals("denied", denied.stderrText)
        assertTrue(timedOut.timedOut)
    }

    private class RecordingRunner(
        vararg responses: Pair<String, BoundedShellResult>,
    ) : BoundedShellRunner {
        private val responses = responses.toMap()
        val commands = mutableListOf<String>()

        override fun run(command: String, mode: ShellCommandMode, timeoutMs: Long): BoundedShellResult {
            commands += command
            return responses[command] ?: BoundedShellResult(0)
        }
    }
}
