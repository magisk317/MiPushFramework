package io.github.magisk317.mipush.feature.main.subpage

import android.content.Context
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfDelivery
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfInstallResult
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfLatestCheck
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfRuntimeManifest
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfStatus
import io.github.magisk317.uikit.update.AppUpdateChecker
import io.github.magisk317.uikit.update.AppUpdateController
import io.github.magisk317.uikit.update.UpdateCheckResult
import io.github.magisk317.uikit.update.UpdateManifest
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionUpdateCoordinatorTest {

    @Test
    fun `both targets available install the runtime first and then open the manager dialog`() =
        runBlocking {
            val order = mutableListOf<String>()
            val delivery = FakeXmsfDelivery(
                latest = XmsfLatestCheck.Available(runtimeManifest("1.0.10")),
                onEnsureInstalled = { target, _ ->
                    order += "runtime:$target"
                    XmsfInstallResult.Installed
                },
            )
            val controller = mockk<AppUpdateController>(relaxed = true)
            val coordinator = coordinator(
                manager = UpdateCheckResult.Available(managerManifest("2.0.0"), 1900, "1.0.9", force = false),
                controller = controller,
                delivery = delivery,
            )

            coordinator.checkAndUpdate()

            assertEquals(listOf("runtime:1.0.10"), order)
            assertTrue(coordinator.runtimeHint.value is VersionRowHint.UpToDate)
            assertTrue(coordinator.managerHint.value is VersionRowHint.Available)
            verify { controller.check(userInitiated = true) }
        }

    @Test
    fun `only the runtime available installs it and leaves the manager dialog alone`() = runBlocking {
        val controller = mockk<AppUpdateController>(relaxed = true)
        val coordinator = coordinator(
            manager = UpdateCheckResult.UpToDate,
            controller = controller,
            delivery = FakeXmsfDelivery(latest = XmsfLatestCheck.Available(runtimeManifest("1.0.10"))),
        )

        coordinator.checkAndUpdate()

        assertTrue(coordinator.runtimeHint.value is VersionRowHint.UpToDate)
        assertTrue(coordinator.managerHint.value is VersionRowHint.UpToDate)
        verify(exactly = 0) { controller.check(any()) }
    }

    @Test
    fun `only the manager available skips the runtime install and opens the dialog`() = runBlocking {
        val controller = mockk<AppUpdateController>(relaxed = true)
        val delivery = FakeXmsfDelivery(latest = XmsfLatestCheck.UpToDate)
        val coordinator = coordinator(
            manager = UpdateCheckResult.Available(managerManifest("2.0.0"), 1900, "1.0.9", force = false),
            controller = controller,
            delivery = delivery,
        )

        coordinator.checkAndUpdate()

        assertEquals(emptyList<String>(), delivery.installCalls)
        assertTrue(coordinator.managerHint.value is VersionRowHint.Available)
        verify { controller.check(userInitiated = true) }
    }

    @Test
    fun `nothing available installs nothing and reports both rows up to date`() = runBlocking {
        val controller = mockk<AppUpdateController>(relaxed = true)
        val coordinator = coordinator(
            manager = UpdateCheckResult.UpToDate,
            controller = controller,
            delivery = FakeXmsfDelivery(latest = XmsfLatestCheck.UpToDate),
        )

        coordinator.checkAndUpdate()

        assertTrue(coordinator.managerHint.value is VersionRowHint.UpToDate)
        assertTrue(coordinator.runtimeHint.value is VersionRowHint.UpToDate)
        verify(exactly = 0) { controller.check(any()) }
    }

    @Test
    fun `runtime install failure keeps the manager update flowing`() = runBlocking {
        val controller = mockk<AppUpdateController>(relaxed = true)
        val coordinator = coordinator(
            manager = UpdateCheckResult.Available(managerManifest("2.0.0"), 1900, "1.0.9", force = false),
            controller = controller,
            delivery = FakeXmsfDelivery(
                latest = XmsfLatestCheck.Available(runtimeManifest("1.0.10")),
                result = XmsfInstallResult.Failed("runtime checksum mismatch"),
            ),
        )

        coordinator.checkAndUpdate()

        assertEquals(
            VersionRowHint.Failed("runtime checksum mismatch"),
            coordinator.runtimeHint.value,
        )
        verify { controller.check(userInitiated = true) }
    }

    @Test
    fun `manager check failure reports the row and skips the dialog`() = runBlocking {
        val controller = mockk<AppUpdateController>(relaxed = true)
        val coordinator = coordinator(
            manager = UpdateCheckResult.Failed("no source reachable"),
            controller = controller,
            delivery = FakeXmsfDelivery(latest = XmsfLatestCheck.UpToDate),
        )

        coordinator.checkAndUpdate()

        assertEquals(VersionRowHint.Failed("no source reachable"), coordinator.managerHint.value)
        verify(exactly = 0) { controller.check(any()) }
    }

    @Test
    fun `checkBoth never installs and never opens the dialog`() = runBlocking {
        val controller = mockk<AppUpdateController>(relaxed = true)
        val delivery = FakeXmsfDelivery(latest = XmsfLatestCheck.Available(runtimeManifest("1.0.10")))
        val coordinator = coordinator(
            manager = UpdateCheckResult.Available(managerManifest("2.0.0"), 1900, "1.0.9", force = false),
            controller = controller,
            delivery = delivery,
        )

        val report = coordinator.checkBoth()

        assertTrue(report.manager is UpdateCheckResult.Available)
        assertTrue(report.runtime is XmsfLatestCheck.Available)
        assertEquals(emptyList<String>(), delivery.installCalls)
        verify(exactly = 0) { controller.check(any()) }
    }

    @Test
    fun `play flavor reports unsupported and never installs the runtime`() = runBlocking {
        val controller = mockk<AppUpdateController>(relaxed = true)
        val delivery = FakeXmsfDelivery(latest = XmsfLatestCheck.Unsupported, installsDirectly = false)
        val coordinator = coordinator(
            manager = UpdateCheckResult.UpToDate,
            controller = controller,
            delivery = delivery,
        )

        coordinator.checkAndUpdate()

        assertEquals(VersionRowHint.Unsupported, coordinator.runtimeHint.value)
        assertEquals(emptyList<String>(), delivery.installCalls)
        assertTrue(!coordinator.canUpdateRuntimeInApp)
    }

    @Test
    fun `runtime install progress is mirrored into the row hint`() = runBlocking {
        val coordinatorRef = AtomicReference<VersionUpdateCoordinator>()
        val hintsDuringInstall = mutableListOf<VersionRowHint>()
        val delivery = FakeXmsfDelivery(
            latest = XmsfLatestCheck.Available(runtimeManifest("1.0.10")),
            onEnsureInstalled = { _, onProgress ->
                onProgress(42)
                hintsDuringInstall += coordinatorRef.get().runtimeHint.value
                XmsfInstallResult.Installed
            },
        )
        val coordinator = coordinator(
            manager = UpdateCheckResult.UpToDate,
            controller = mockk(relaxed = true),
            delivery = delivery,
        )
        coordinatorRef.set(coordinator)

        coordinator.checkAndUpdate()

        assertEquals(listOf(VersionRowHint.Updating(42)), hintsDuringInstall)
        assertTrue(coordinator.runtimeHint.value is VersionRowHint.UpToDate)
    }

    private fun coordinator(
        manager: UpdateCheckResult,
        controller: AppUpdateController,
        delivery: FakeXmsfDelivery,
    ): VersionUpdateCoordinator {
        val checker = mockk<AppUpdateChecker> {
            coEvery { check(any()) } returns manager
        }
        return VersionUpdateCoordinator(
            checker = checker,
            controller = controller,
            deliveryProvider = { delivery },
        )
    }

    private fun managerManifest(versionName: String) = UpdateManifest(
        versionCode = 2000,
        versionName = versionName,
        apkUrl = "https://example.com/mipush.apk",
    )

    private fun runtimeManifest(versionName: String) = XmsfRuntimeManifest(
        schema = XmsfRuntimeManifest.SCHEMA,
        variant = XmsfRuntimeManifest.VARIANT,
        versionName = versionName,
        versionCode = 1003003000L,
        minManagerVersionCode = 0L,
        sha256 = "0".repeat(64),
    )

    private class FakeXmsfDelivery(
        private val latest: XmsfLatestCheck,
        private val installsDirectly: Boolean = true,
        private val result: XmsfInstallResult = XmsfInstallResult.Installed,
        private val onEnsureInstalled: suspend (String?, (Int) -> Unit) -> XmsfInstallResult = { _, _ -> result },
    ) : XmsfDelivery {
        val installCalls = mutableListOf<String>()
        override val status = MutableStateFlow(XmsfStatus())
        override suspend fun refresh(): XmsfStatus = status.value
        override val canInstallDirectly: Boolean get() = installsDirectly
        override suspend fun checkLatestRuntime(): XmsfLatestCheck = latest
        override suspend fun ensureInstalled(
            targetVersionName: String?,
            onProgress: (Int) -> Unit,
        ): XmsfInstallResult {
            installCalls += targetVersionName.orEmpty()
            return onEnsureInstalled(targetVersionName, onProgress)
        }
        override fun openGuidance(context: Context) = Unit
    }
}
