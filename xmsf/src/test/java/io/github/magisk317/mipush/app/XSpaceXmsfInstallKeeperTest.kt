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
    fun `matching manager and xmsf install state is already synchronized`() {
        val runner = RecordingRootRunner(
            "cmd user list" to xspaceUsers(),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME) to packageListed(Constants.MANAGER_APP_NAME),
            XSpaceXmsfInstallKeeper.listPackageCommand() to packageListed(Constants.SERVICE_APP_NAME),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.ALREADY_SYNCHRONIZED, result.stage)
        assertEquals(
            listOf(
                "cmd user list",
                XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME),
                XSpaceXmsfInstallKeeper.listPackageCommand(),
            ),
            runner.commands,
        )
    }

    @Test
    fun `missing xmsf with manager module installed triggers install existing`() {
        val runner = RecordingRootRunner(
            "cmd user list" to xspaceUsers(),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME) to packageListed(Constants.MANAGER_APP_NAME),
            XSpaceXmsfInstallKeeper.listPackageCommand() to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.installExistingCommand() to BoundedShellResult(
                0,
                stdout = listOf("Package com.xiaomi.xmsf installed for user: 999"),
            ),
            XSpaceXmsfInstallKeeper.listPackageCommand() to packageListed(Constants.SERVICE_APP_NAME),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.INSTALL_EXISTING_SUCCEEDED, result.stage)
        assertEquals(
            listOf(
                "cmd user list",
                XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME),
                XSpaceXmsfInstallKeeper.listPackageCommand(),
                XSpaceXmsfInstallKeeper.installExistingCommand(),
                XSpaceXmsfInstallKeeper.listPackageCommand(),
            ),
            runner.commands,
        )
    }

    @Test
    fun `install existing failure is reported`() {
        val runner = RecordingRootRunner(
            "cmd user list" to xspaceUsers(),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME) to packageListed(Constants.MANAGER_APP_NAME),
            XSpaceXmsfInstallKeeper.listPackageCommand() to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.installExistingCommand() to BoundedShellResult(1),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.INSTALL_EXISTING_FAILED, result.stage)
    }

    @Test
    fun `missing manager module and missing xmsf is already clean`() {
        val runner = RecordingRootRunner(
            "cmd user list" to xspaceUsers(),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME) to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.listPackageCommand() to BoundedShellResult(0),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.MODULE_ABSENT_XMSF_ABSENT, result.stage)
        assertEquals(
            listOf(
                "cmd user list",
                XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME),
                XSpaceXmsfInstallKeeper.listPackageCommand(),
            ),
            runner.commands,
        )
    }

    @Test
    fun `missing manager module uninstalls xmsf from xspace`() {
        val runner = RecordingRootRunner(
            "cmd user list" to xspaceUsers(),
            XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME) to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.listPackageCommand() to packageListed(Constants.SERVICE_APP_NAME),
            XSpaceXmsfInstallKeeper.uninstallCommand() to BoundedShellResult(0),
            XSpaceXmsfInstallKeeper.listPackageCommand() to BoundedShellResult(0),
        )

        val result = XSpaceXmsfInstallKeeper.repairNow(
            hasRootAccess = { true },
            runRootCommand = runner::run,
        )

        assertEquals(XSpaceXmsfInstallKeeper.Stage.UNINSTALL_SUCCEEDED, result.stage)
        assertEquals(
            listOf(
                "cmd user list",
                XSpaceXmsfInstallKeeper.listPackageCommand(Constants.MANAGER_APP_NAME),
                XSpaceXmsfInstallKeeper.listPackageCommand(),
                XSpaceXmsfInstallKeeper.uninstallCommand(),
                XSpaceXmsfInstallKeeper.listPackageCommand(),
            ),
            runner.commands,
        )
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
