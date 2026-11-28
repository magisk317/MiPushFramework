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
import io.mockk.every
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
                    XmsfInstallResult.Installed()
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
    fun `paired check exposes one transaction and start adopts the checked manager`() = runBlocking {
        val controller = mockk<AppUpdateController>(relaxed = true)
        val journal = InMemoryPairedOtaJournal()
        val coordinator = coordinator(
            manager = UpdateCheckResult.Available(managerManifest("2.0.0"), 1900, "1.0.9", force = true),
            controller = controller,
            delivery = FakeXmsfDelivery(latest = XmsfLatestCheck.Available(runtimeManifest("1.0.10"))),
            journal = journal,
        )

        coordinator.checkAndShowPaired()
        coordinator.startPairedUpdate()

        assertTrue(coordinator.pairedVisible.value)
        assertTrue(coordinator.pairedStarted.value)
        assertEquals("2.0.0", journal.read()?.managerVersionName)
        verify { controller.adoptAvailable(any()) }
        verify { controller.download() }
    }

    @Test
    fun `pending pair resumes runtime after manager restart and allows runtime retry`() = runBlocking {
        val journal = InMemoryPairedOtaJournal().apply {
            begin(managerManifest("2.0.0"), runtimeManifest("1.0.10"))
        }
        var installAttempt = 0
        val delivery = FakeXmsfDelivery(
            latest = XmsfLatestCheck.UpToDate,
            onEnsureInstalled = { _, _ ->
                installAttempt++
                if (installAttempt == 1) {
                    XmsfInstallResult.Failed("temporary download failure")
                } else {
                    XmsfInstallResult.Installed()
                }
            },
        )
        val coordinator = coordinator(
            manager = UpdateCheckResult.UpToDate,
            controller = mockk(relaxed = true),
            delivery = delivery,
            journal = journal,
            installedVersionCode = 2000,
        )

        coordinator.checkOnStartup()

        assertEquals(VersionRowHint.Failed("temporary download failure"), coordinator.runtimeHint.value)
        assertTrue(coordinator.canRetryPairedRuntime)
        assertEquals(listOf("1.0.10"), delivery.installCalls)

        coordinator.retryRuntime()

        assertEquals(null, journal.read())
        assertEquals(listOf("1.0.10", "1.0.10"), delivery.installCalls)
        assertEquals(VersionRowHint.Installed, coordinator.runtimeHint.value)
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
    fun `runtime install failure blocks the manager update`() = runBlocking {
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
        verify(exactly = 0) { controller.check(any()) }
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
    fun `unsupported delivery reports unsupported and never installs the runtime`() = runBlocking {
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
                XmsfInstallResult.Installed()
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
        journal: InMemoryPairedOtaJournal = InMemoryPairedOtaJournal(),
        installedVersionCode: Int = 0,
    ): VersionUpdateCoordinator {
        val checker = mockk<AppUpdateChecker> {
            coEvery { check(any()) } returns manager
            every { currentVersionCode() } returns installedVersionCode
        }
        return VersionUpdateCoordinator(
            checker = checker,
            controller = controller,
            pairedOtaJournal = journal,
            deliveryProvider = { delivery },
        )
    }

    private class InMemoryPairedOtaJournal : PairedOtaJournal {
        private var pending: PairedOtaJournal.Pending? = null
        override fun read(): PairedOtaJournal.Pending? = pending
        override fun begin(manager: UpdateManifest, runtime: XmsfRuntimeManifest) {
            pending = PairedOtaJournal.Pending(
                managerVersionCode = manager.versionCode.toLong(),
                managerVersionName = manager.versionName,
                runtimeVersionName = runtime.versionName,
            )
        }
        override fun clear() {
            pending = null
        }
    }

    private fun managerManifest(versionName: String, force: Boolean = false) = UpdateManifest(
        versionCode = 2000,
        versionName = versionName,
        apkUrl = "https://example.com/mipush.apk",
        force = force,
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
        private val result: XmsfInstallResult = XmsfInstallResult.Installed(),
        private val onEnsureInstalled: suspend (String?, (Int) -> Unit) -> XmsfInstallResult = { _, _ -> result },
    ) : XmsfDelivery {
        val installCalls = mutableListOf<String>()
        override val status = MutableStateFlow(XmsfStatus())
        override suspend fun refresh(): XmsfStatus = status.value
        override val canInstallDirectly: Boolean get() = installsDirectly
        override suspend fun checkLatestRuntime(targetManagerVersionCode: Long?): XmsfLatestCheck = latest
        override suspend fun ensureInstalled(
            targetVersionName: String?,
            onProgress: (Int) -> Unit,
            onWaitingForSystemConfirmation: () -> Unit,
            targetManagerVersionCode: Long?,
        ): XmsfInstallResult {
            installCalls += targetVersionName.orEmpty()
            return onEnsureInstalled(targetVersionName, onProgress)
        }
        override fun openGuidance(context: Context) = Unit
    }
}
