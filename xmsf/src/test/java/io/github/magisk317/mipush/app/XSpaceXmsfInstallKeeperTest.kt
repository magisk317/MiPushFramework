package io.github.magisk317.mipush.app

import io.github.magisk317.mipush.platform.support.BoundedShellResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XSpaceXmsfInstallKeeperTest {
    @Test
    fun `root missing skips without package commands`() {
        val runner = RecordingRootRunner()

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { false },
            runRootCommand = runner::run,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.ROOT_MISSING, result.stage)
        assertEquals(emptyList<String>(), runner.commands)
    }

    @Test
    fun `missing xspace user skips install existing`() {
        val runner = RecordingRootRunner(
            "cmd user list" to BoundedShellResult(0, stdout = listOf("UserInfo{0:Owner:13} running")),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.XSPACE_USER_NOT_FOUND, result.stage)
        assertEquals(listOf("cmd user list"), runner.commands)
    }

    @Test
    fun `data app path is already healthy`() {
        val runner = RecordingRootRunner(
            "cmd user list" to xspaceUsers(),
            XSpaceXmsfInstallKeeper.pmPathCommand() to BoundedShellResult(
                0,
                stdout = listOf("package:/data/app/~~token/com.xiaomi.xmsf/base.apk"),
            ),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.ALREADY_DATA_APP, result.stage)
        assertEquals(
            listOf("cmd user list", XSpaceXmsfInstallKeeper.pmPathCommand()),
            runner.commands,
        )
    }

    @Test
    fun `system path triggers install existing and verifies data app path`() {
        val pmPathCommand = XSpaceXmsfInstallKeeper.pmPathCommand()
        val runner = RecordingRootRunner(
            "cmd user list" to xspaceUsers(),
            pmPathCommand to BoundedShellResult(0, stdout = listOf("package:/system/priv-app/XMSF/base.apk")),
            XSpaceXmsfInstallKeeper.installExistingCommand() to BoundedShellResult(
                0,
                stdout = listOf("Package com.xiaomi.xmsf installed for user: 999"),
            ),
            pmPathCommand to BoundedShellResult(0, stdout = listOf("package:/data/app/~~token/com.xiaomi.xmsf/base.apk")),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.INSTALL_EXISTING_SUCCEEDED, result.stage)
        assertEquals(
            listOf(
                "cmd user list",
                XSpaceXmsfInstallKeeper.pmPathCommand(),
                XSpaceXmsfInstallKeeper.installExistingCommand(),
                XSpaceXmsfInstallKeeper.pmPathCommand(),
            ),
            runner.commands,
        )
    }

    @Test
    fun `install existing failure is reported`() {
        val runner = RecordingRootRunner(
            "cmd user list" to xspaceUsers(),
            XSpaceXmsfInstallKeeper.pmPathCommand() to BoundedShellResult(
                0,
                stdout = listOf("package:/system/priv-app/XMSF/base.apk"),
            ),
            XSpaceXmsfInstallKeeper.installExistingCommand() to BoundedShellResult(1),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.INSTALL_EXISTING_FAILED, result.stage)
    }

    @Test
    fun `path parser distinguishes system and data app records`() {
        assertTrue(XSpaceXmsfInstallKeeper.hasXSpaceUser("UserInfo{999:XSpace:801010} running"))
        assertTrue(XSpaceXmsfInstallKeeper.isDataAppPath("package:/data/app/~~id/com.xiaomi.xmsf/base.apk"))
        assertFalse(XSpaceXmsfInstallKeeper.isDataAppPath("package:/system/priv-app/XMSF/base.apk"))
    }

    private class RecordingRootRunner(
        vararg responses: Pair<String, BoundedShellResult>,
    ) {
        private val responseQueues = responses
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> ArrayDeque(values) }
        val commands = mutableListOf<String>()

        fun run(command: String, timeoutMs: Long): BoundedShellResult {
            commands += command
            return responseQueues[command]?.removeFirstOrNull() ?: BoundedShellResult(0)
        }
    }

    private companion object {
        fun xspaceUsers(): BoundedShellResult =
            BoundedShellResult(0, stdout = listOf("UserInfo{0:Owner:13} running", "UserInfo{999:XSpace:801010} running"))
    }
}
