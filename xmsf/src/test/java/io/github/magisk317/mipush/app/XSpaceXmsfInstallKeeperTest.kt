package io.github.magisk317.mipush.app

import io.github.magisk317.mipush.platform.support.BoundedShellResult
import io.github.magisk317.mipush.common.Constants
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
    fun `toggle off with both packages absent is already synchronized`() {
        val runner = RecordingRootRunner(
            "cmd user list" to xspaceUsers(),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.SERVICE_APP_NAME) to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME) to BoundedShellResult(0),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
            isDualAppEnabled = false,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.ALREADY_SYNCHRONIZED, result.stage)
    }

    @Test
    fun `toggle off uninstalls both packages when present`() {
        val runner = RecordingRootRunner(
            "cmd user list" to xspaceUsers(),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.SERVICE_APP_NAME) to packageListed(Constants.SERVICE_APP_NAME),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME) to packageListed(Constants.MANAGER_APP_NAME),
            XSpaceXmsfInstallKeeper.uninstallCommand(Constants.SERVICE_APP_NAME) to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.uninstallCommand(Constants.MANAGER_APP_NAME) to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.SERVICE_APP_NAME) to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME) to BoundedShellResult(0),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
            isDualAppEnabled = false,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.UNINSTALL_SUCCEEDED, result.stage)
    }

    @Test
    fun `toggle on with both packages present is already synchronized`() {
        val runner = RecordingRootRunner(
            "cmd user list" to xspaceUsers(),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.SERVICE_APP_NAME) to packageListed(Constants.SERVICE_APP_NAME),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME) to packageListed(Constants.MANAGER_APP_NAME),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
            isDualAppEnabled = true,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.ALREADY_SYNCHRONIZED, result.stage)
    }

    @Test
    fun `toggle on installs both packages when absent`() {
        val runner = RecordingRootRunner(
            "cmd user list" to xspaceUsers(),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.SERVICE_APP_NAME) to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME) to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.installExistingCommand(Constants.SERVICE_APP_NAME) to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.installExistingCommand(Constants.MANAGER_APP_NAME) to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.SERVICE_APP_NAME) to packageListed(Constants.SERVICE_APP_NAME),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME) to packageListed(Constants.MANAGER_APP_NAME),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
            isDualAppEnabled = true,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.INSTALL_EXISTING_SUCCEEDED, result.stage)
    }

    @Test
    fun `toggle on installs missing packages when only some present`() {
        val runner = RecordingRootRunner(
            "cmd user list" to xspaceUsers(),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.SERVICE_APP_NAME) to packageListed(Constants.SERVICE_APP_NAME),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME) to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.installExistingCommand(Constants.MANAGER_APP_NAME) to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.SERVICE_APP_NAME) to packageListed(Constants.SERVICE_APP_NAME),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME) to packageListed(Constants.MANAGER_APP_NAME),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
            isDualAppEnabled = true,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.INSTALL_EXISTING_SUCCEEDED, result.stage)
    }

    @Test
    fun `package list parser requires exact package entry`() {
        assertTrue(XSpaceXmsfInstallKeeper.hasXSpaceUser("UserInfo{999:XSpace:801010} running"))
        assertTrue(XSpaceXmsfInstallKeeper.isPackageListed(packageListed(Constants.MANAGER_APP_NAME), Constants.MANAGER_APP_NAME))
        assertFalse(XSpaceXmsfInstallKeeper.isPackageListed(BoundedShellResult(0), Constants.MANAGER_APP_NAME))
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

        fun packageListed(packageName: String): BoundedShellResult =
            BoundedShellResult(0, stdout = listOf("package:$packageName"))
    }
}
