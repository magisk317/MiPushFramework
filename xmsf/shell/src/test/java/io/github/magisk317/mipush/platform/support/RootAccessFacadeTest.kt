package io.github.magisk317.mipush.platform.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RootAccessFacadeTest {
    @Test
    fun `root command trusts the probe when grant state is unknown`() {
        val runner = RecordingRunner(
            "id -u" to BoundedShellResult(exitCode = 1, stderr = listOf("denied")),
        )
        val facade = RootAccessFacade(
            runner = runner,
            rootGrantState = { null },
            requestRootGrant = {},
        )

        val result = facade.runRootCommand("id")

        assertTrue(result.skipped)
        assertEquals(listOf("id -u"), runner.commands)
    }

    @Test
    fun `refresh trusts root probe when libsu reports denied`() {
        val runner = RecordingRunner(
            "id -u" to BoundedShellResult(0, stdout = listOf("0")),
        )
        val facade = RootAccessFacade(
            runner = runner,
            rootGrantState = { false },
            requestRootGrant = {},
        )

        assertTrue(facade.refreshRootAccessIfGranted())
        assertTrue(facade.hasCachedRootAccess())
        assertEquals(listOf("id -u"), runner.commands)
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
    fun `denied root request asks and probes only once`() {
        var requestCount = 0
        val runner = RecordingRunner(
            "id -u" to BoundedShellResult(exitCode = 1, stderr = listOf("denied")),
        )
        val facade = RootAccessFacade(
            runner = runner,
            rootGrantState = { false },
            requestRootGrant = { requestCount += 1 },
        )

        assertFalse(facade.requestRootAccess())

        assertEquals(1, requestCount)
        assertEquals(listOf("id -u"), runner.commands)
        assertFalse(facade.hasCachedRootAccess())
    }

    @Test
    fun `refresh keeps access when grant state is denied but probe succeeds`() {
        var grantState: Boolean? = true
        val runner = RecordingRunner(
            "id -u" to BoundedShellResult(0, stdout = listOf("0")),
        )
        val facade = RootAccessFacade(
            runner = runner,
            rootGrantState = { grantState },
            requestRootGrant = {},
        )

        assertTrue(facade.refreshRootAccessIfGranted())
        assertTrue(facade.hasCachedRootAccess())

        grantState = false

        assertTrue(facade.refreshRootAccessIfGranted())
        assertTrue(facade.hasCachedRootAccess())
        assertEquals(listOf("id -u", "id -u"), runner.commands)
    }

    @Test
    fun `refresh revokes cached root access when probe fails`() {
        val runner = RecordingRunner(
            "id -u" to BoundedShellResult(0, stdout = listOf("0")),
        )
        val facade = RootAccessFacade(
            runner = runner,
            rootGrantState = { false },
            requestRootGrant = {},
        )

        assertTrue(facade.refreshRootAccessIfGranted())
        runner.setResponse("id -u", BoundedShellResult(exitCode = 1, stderr = listOf("denied")))

        assertFalse(facade.refreshRootAccessIfGranted())
        assertFalse(facade.hasCachedRootAccess())
        assertEquals(listOf("id -u", "id -u"), runner.commands)
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
        private val responses = responses.toMap().toMutableMap()
        val commands = mutableListOf<String>()

        fun setResponse(command: String, response: BoundedShellResult) {
            responses[command] = response
        }

        override fun run(command: String, mode: ShellCommandMode, timeoutMs: Long): BoundedShellResult {
            commands += command
            return responses[command] ?: BoundedShellResult(0)
        }
    }
}
