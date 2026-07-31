package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.common.manager.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.common.manager.ManagerRootAccessState
import io.github.magisk317.mipush.common.manager.ManagerRootSubjectStatus
import io.github.magisk317.mipush.common.manager.ManagerRootTarget
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairResult
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.feature.wizard.permission.PermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.PermissionOperator
import io.github.magisk317.mipush.feature.wizard.permission.RootPermissionOperator
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequestPermissionViewModelTest {
    @Test
    fun `root silent check refreshes without requesting authorization`() {
        val gateway = FakePermissionGateway()

        val granted = RootPermissionOperator().requestPermissionSilently(gateway)

        assertFalse(granted)
        assertEquals(1, gateway.rootRefreshCount)
        assertEquals(0, gateway.rootRequestCount)
    }

    @Test
    fun `root request is re-evaluated after the grant result`() = runBlocking {
        val gateway = FakePermissionGateway()
        val rootPermission = permissionInfo(RootPermissionOperator())

        val states = requestPermissionAndEvaluateStates(
            permissionInfo = rootPermission,
            permissionInfos = listOf(rootPermission),
            permissionGateway = gateway,
        )

        assertEquals(1, gateway.rootRequestCount)
        assertTrue(states.getValue(0))
    }

    @Test
    fun `manager and runtime root requests stay independent`() {
        val gateway = FakePermissionGateway()

        val managerSnapshot = gateway.requestRootAccess(ManagerRootTarget.MANAGER)

        assertTrue(managerSnapshot.manager.isGranted)
        assertFalse(managerSnapshot.runtime.isGranted)

        val runtimeSnapshot = gateway.requestRootAccess(ManagerRootTarget.RUNTIME)

        assertTrue(runtimeSnapshot.manager.isGranted)
        assertTrue(runtimeSnapshot.runtime.isGranted)
    }

    private fun permissionInfo(operator: PermissionOperator): PermissionInfo = object : PermissionInfo {
        override val permissionOperator: PermissionOperator = operator
        override val permissionTitle: String = "Root"
        override val permissionDescription: String = "Root access"
    }

    private class FakePermissionGateway : ManagerPermissionGateway {
        var rootRefreshCount = 0
        var rootRequestCount = 0
        private var rootGranted = false
        private var managerRootGranted = false

        override fun getRootAccessSnapshot(refresh: Boolean): ManagerRootAccessSnapshot = snapshot()

        override fun requestRootAccess(target: ManagerRootTarget): ManagerRootAccessSnapshot {
            when (target) {
                ManagerRootTarget.MANAGER -> managerRootGranted = true
                ManagerRootTarget.RUNTIME -> rootGranted = true
            }
            return snapshot()
        }

        override fun hasCachedRootAccess(): Boolean = rootGranted

        override fun refreshRootAccessIfGranted(): Boolean {
            rootRefreshCount += 1
            return rootGranted
        }

        override fun requestRootAccess(): Boolean {
            rootRequestCount += 1
            rootGranted = true
            return true
        }

        override fun repairXSpaceUserSupport(): ManagerXSpaceRepairResult = unsupportedRepair()

        override fun setDualAppEnabled(enabled: Boolean): ManagerXSpaceRepairResult = unsupportedRepair()

        override fun isDualAppInstalled(): Boolean = false

        override fun launchAppOps(context: Context, permission: String, tips: CharSequence): Boolean = false

        override fun isUsageStatsAllowedByRoot(packageName: String): Boolean = false

        override fun requestIgnoreBatteryOptimizations(context: Context): Boolean = false

        override fun grantNotificationPermission(context: Context): Boolean = false

        private fun unsupportedRepair() = ManagerXSpaceRepairResult(ManagerXSpaceRepairStage.ROOT_MISSING)

        private fun snapshot() = ManagerRootAccessSnapshot(
            userId = 999,
            manager = ManagerRootSubjectStatus(
                target = ManagerRootTarget.MANAGER,
                packageName = "io.github.magisk317.mipush",
                userId = 999,
                state = managerRootGranted.toState(),
            ),
            runtime = ManagerRootSubjectStatus(
                target = ManagerRootTarget.RUNTIME,
                packageName = "com.xiaomi.xmsf",
                userId = 999,
                state = rootGranted.toState(),
            ),
        )

        private fun Boolean.toState() = if (this) {
            ManagerRootAccessState.GRANTED
        } else {
            ManagerRootAccessState.NOT_GRANTED
        }
    }
}
