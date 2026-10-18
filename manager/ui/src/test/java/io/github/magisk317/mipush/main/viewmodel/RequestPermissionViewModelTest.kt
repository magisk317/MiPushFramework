package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.application.ManagerDualAppInstallationResult
import io.github.magisk317.mipush.manager.application.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRootAccessState
import io.github.magisk317.mipush.manager.application.ManagerRootSubjectStatus
import io.github.magisk317.mipush.manager.application.ManagerRootTarget
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairResult
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.feature.wizard.permission.PermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.PermissionOperator
import io.github.magisk317.mipush.feature.wizard.permission.RootPermissionOperator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RequestPermissionViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `root silent check refreshes without requesting authorization`() = runBlocking {
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
    fun `manager and runtime root requests stay independent`() = runBlocking {
        val gateway = FakePermissionGateway()

        val managerSnapshot = gateway.requestRootAccess(ManagerRootTarget.MANAGER)

        assertTrue(managerSnapshot.manager.isGranted)
        assertFalse(managerSnapshot.runtime.isGranted)

        val runtimeSnapshot = gateway.requestRootAccess(ManagerRootTarget.RUNTIME)

        assertTrue(runtimeSnapshot.manager.isGranted)
        assertTrue(runtimeSnapshot.runtime.isGranted)
    }

    @Test
    fun `evaluatePermissions refreshes state without launching any system page`() {
        val gateway = FakePermissionGateway()
        val infos = listOf(
            FakePermissionInfo("Usage stats", granted = true),
            FakePermissionInfo("Battery exemption", granted = false),
        )
        val viewModel = RequestPermissionViewModel(gateway)

        viewModel.evaluatePermissions(infos)
        awaitUntil { viewModel.permissionStates.value.size == infos.size }

        assertTrue(viewModel.permissionStates.value.getValue(0))
        assertFalse(viewModel.permissionStates.value.getValue(1))
        infos.forEach { info ->
            assertEquals(0, info.operator.silentCount)
            assertEquals(0, info.operator.requestCount)
        }
    }

    @Test
    fun `grantAll grants silently and skips satisfied items`() {
        val gateway = FakePermissionGateway()
        val silent = FakePermissionInfo("Battery exemption", granted = false, silentResult = true)
        val denied = FakePermissionInfo("Notification", granted = false, silentResult = false)
        val already = FakePermissionInfo("Doze whitelist", granted = true)
        val infos = listOf(silent, denied, already)
        val viewModel = RequestPermissionViewModel(gateway)

        viewModel.grantAll(infos)
        awaitUntil { !viewModel.grantingAll.value }

        assertEquals(1, silent.operator.silentCount)
        assertEquals(1, denied.operator.silentCount)
        assertEquals(0, already.operator.silentCount)
        infos.forEach { info -> assertEquals(0, info.operator.requestCount) }
        assertTrue(viewModel.permissionStates.value.getValue(0))
        assertFalse(viewModel.permissionStates.value.getValue(1))
        assertTrue(viewModel.permissionStates.value.getValue(2))
        assertEquals(2, viewModel.grantLog.value.size)
    }

    @Test
    fun `entering the app requires both root subjects then grants everything silently`() {
        val gateway = FakePermissionGateway()
        val battery = FakePermissionInfo("Battery exemption", granted = false, silentResult = true)
        val infos = listOf(battery)
        val viewModel = RequestPermissionViewModel(gateway)

        assertFalse(viewModel.allRequiredValidated.value)

        viewModel.requestRootAccess(ManagerRootTarget.MANAGER, infos) {}
        awaitUntil { viewModel.rootAccessSnapshot.value.manager.isGranted }
        assertFalse(viewModel.allRequiredValidated.value)

        viewModel.requestRootAccess(ManagerRootTarget.RUNTIME, infos) {}
        awaitUntil { viewModel.allRequiredValidated.value }
        awaitUntil { viewModel.permissionStates.value[0] == true }
        awaitUntil { !viewModel.grantingAll.value }

        // The first subject grant already ran the silent pass; the second one
        // skips the now-satisfied item instead of granting twice.
        assertEquals(1, battery.operator.silentCount)
    }

    @Test
    fun `tap grants exactly one item through the silent path`() {
        val gateway = FakePermissionGateway()
        val first = FakePermissionInfo("Battery exemption", granted = false, silentResult = true)
        val second = FakePermissionInfo("Notification", granted = false, silentResult = true)
        val infos = listOf(first, second)
        val viewModel = RequestPermissionViewModel(gateway)

        viewModel.requestPermission(first, infos) {}
        awaitUntil {
            viewModel.processingKeys.value.isEmpty() && viewModel.permissionStates.value.isNotEmpty()
        }

        assertEquals(1, first.operator.silentCount)
        assertEquals(0, second.operator.silentCount)
        assertTrue(viewModel.permissionStates.value.getValue(0))
        assertEquals(1, viewModel.grantLog.value.size)
    }

    @Test
    fun `tap falls back to the system page when the silent path is unavailable`() {
        val gateway = FakePermissionGateway()
        val info = FakePermissionInfo("Usage stats", granted = false, silentResult = false)
        val infos = listOf(info)
        val viewModel = RequestPermissionViewModel(gateway)

        viewModel.requestPermission(info, infos) {}
        awaitUntil {
            viewModel.processingKeys.value.isEmpty() && viewModel.permissionStates.value.isNotEmpty()
        }

        assertEquals(1, info.operator.silentCount)
        assertEquals(1, info.operator.requestCount)
        assertTrue(viewModel.permissionStates.value.getValue(0))
        assertEquals(1, viewModel.grantLog.value.size)
    }

    private fun awaitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("Condition was not met within ${timeoutMillis}ms")
            }
            Thread.sleep(10)
        }
    }

    private fun permissionInfo(operator: PermissionOperator): PermissionInfo = object : PermissionInfo {
        override val permissionOperator: PermissionOperator = operator
        override val permissionTitle: String = "Root"
        override val permissionDescription: String = "Root access"
    }

    private class FakePermissionInfo(
        title: String,
        granted: Boolean = false,
        silentResult: Boolean = false,
    ) : PermissionInfo {
        val operator = CountingPermissionOperator(granted, silentResult)
        override val permissionOperator: PermissionOperator = operator
        override val permissionTitle: String = title
        override val permissionDescription: String = "$title description"
    }

    private class CountingPermissionOperator(
        private var granted: Boolean,
        private val silentResult: Boolean,
    ) : PermissionOperator {
        var silentCount = 0
        var requestCount = 0

        override suspend fun isPermissionGranted(permissionGateway: ManagerPermissionGateway?): Boolean = granted

        override suspend fun requestPermissionSilently(permissionGateway: ManagerPermissionGateway?): Boolean {
            silentCount += 1
            granted = silentResult
            return silentResult
        }

        override suspend fun requestPermission(permissionGateway: ManagerPermissionGateway?) {
            requestCount += 1
            granted = true
        }
    }

    private class FakePermissionGateway : ManagerPermissionGateway {
        var rootRefreshCount = 0
        var rootRequestCount = 0
        private var rootGranted = false
        private var managerRootGranted = false

        override suspend fun getRootAccessSnapshot(refresh: Boolean): ManagerRootAccessSnapshot = snapshot()

        override suspend fun requestRootAccess(target: ManagerRootTarget): ManagerRootAccessSnapshot {
            when (target) {
                ManagerRootTarget.MANAGER -> managerRootGranted = true
                ManagerRootTarget.RUNTIME -> rootGranted = true
            }
            return snapshot()
        }

        override suspend fun hasCachedRootAccess(): Boolean = rootGranted

        override suspend fun refreshRootAccessIfGranted(): Boolean {
            rootRefreshCount += 1
            return rootGranted
        }

        override suspend fun requestRootAccess(): Boolean {
            rootRequestCount += 1
            rootGranted = true
            return true
        }

        override suspend fun repairXSpaceUserSupport(): ManagerXSpaceRepairResult = unsupportedRepair()

        override suspend fun setDualAppEnabled(enabled: Boolean): ManagerXSpaceRepairResult = unsupportedRepair()

        override suspend fun getDualAppInstallation(): ManagerDualAppInstallationResult =
            ManagerDualAppInstallationResult.NotInstalled

        override suspend fun launchAppOps(context: Context, permission: String, tips: CharSequence): Boolean = false

        override suspend fun isUsageStatsAllowedByRoot(packageName: String): Boolean = false

        override suspend fun requestIgnoreBatteryOptimizations(context: Context): Boolean = false

        override suspend fun grantNotificationPermission(context: Context): Boolean = false

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
