package com.xiaomi.push.service

import android.app.Application
import android.content.Context
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.slim.SlimConnection
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

// Keep Robolectric: this test relies on Android framework implementations indirectly;
// android.jar unit-test stubs throw "Method ... not mocked" without the extension.
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class XMPushServiceStockLifecycleTest {
    @Test
    fun `account change requeues the same stock prepare job`() {
        val fixture = fixture(pushEnabled = true)
        fixture.lifecycle.postOnCreate()
        val prepareJob = fixture.jobs.single()

        fixture.dependencies.registeredAccountChangeListener?.onChange()

        assertEquals(2, fixture.jobs.size)
        assertSame(prepareJob, fixture.jobs.last())

        prepareJob.process()

        assertEquals(1, fixture.dependencies.prepareAccountCalls)
        verify(exactly = 1) { fixture.service.scheduleConnect(true) }
    }

    @Test
    fun `stock listener replaces prior listeners then disconnects empty clients with reason 12`() {
        val fixture = fixture()
        val manager = mockk<PushClientsManager>(relaxed = true)
        val listener = slot<PushClientsManager.ClientChangeListener>()
        every { manager.getActiveClientCount() } returns 0
        every { manager.addClientChangeListener(capture(listener)) } just Runs

        fixture.lifecycle.configureClientChangeListener(manager)
        listener.captured.onChange()

        verify(exactly = 1) { manager.removeAllClientChangeListeners() }
        verify(exactly = 1) { fixture.service.updateAlarmTimer() }
        val disconnect = fixture.jobs.single() as DisconnectJob
        assertEquals(12, disconnect.reason)
    }

    @Test
    fun `connected network checks alive and refreshes alarm`() {
        val fixture = fixture(hasNetwork = true, connected = true, shouldCheckAlive = true)

        fixture.lifecycle.networkChanged()

        verify(exactly = 1) { fixture.slimConnection.clearCachedStatus() }
        verify(exactly = 1) { fixture.service.checkAlive(false) }
        verify(exactly = 0) { fixture.service.scheduleConnect(any()) }
        verify(exactly = 1) { fixture.service.updateAlarmTimer() }
        assertEquals(emptyList<XMPushServiceJob>(), fixture.jobs)
    }

    @Test
    fun `disconnected network removes stale connect job and schedules connect`() {
        val fixture = fixture(hasNetwork = true, connected = false, connecting = false)

        fixture.lifecycle.networkChanged()

        verify(exactly = 1) {
            fixture.jobScheduler.removeJobs(XMPushServiceJob.TYPE_CONNECT)
        }
        assertEquals(XMPushServiceJob.TYPE_CONNECT, fixture.jobs.single().type)
        verify(exactly = 1) { fixture.service.updateAlarmTimer() }
    }

    @Test
    fun `unavailable network disconnects with stock reason 2`() {
        val fixture = fixture(hasNetwork = false)

        fixture.lifecycle.networkChanged()

        val disconnect = fixture.jobs.single() as DisconnectJob
        assertEquals(2, disconnect.reason)
        verify(exactly = 1) { fixture.service.updateAlarmTimer() }
    }

    @Test
    fun `wifi switch on api 35 resets a stale non-wifi connection when cfg 149 is on`() {
        val fixture = fixture(
            hasNetwork = true,
            connected = true,
            shouldCheckAlive = true,
            sdkInt = 35,
            wifiConnected = true,
            resetConnectionSwitchEnabled = true,
            connectionPoint = "mobile",
        )

        fixture.lifecycle.networkChanged()

        verify(exactly = 1) { fixture.jobScheduler.removeJobs(XMPushServiceJob.TYPE_CONNECT) }
        assertEquals(XMPushServiceJob.TYPE_RESET_CONNECT, fixture.jobs.single().type)
        // Reset supersedes the check-alive branch on the same transition.
        verify(exactly = 0) { fixture.service.checkAlive(any()) }
        verify(exactly = 1) { fixture.service.updateAlarmTimer() }
    }

    @Test
    fun `no reset when cfg 149 off even on wifi with stale non-wifi connection`() {
        val fixture = fixture(
            hasNetwork = true,
            connected = true,
            shouldCheckAlive = true,
            sdkInt = 35,
            wifiConnected = true,
            resetConnectionSwitchEnabled = false,
            connectionPoint = "mobile",
        )

        fixture.lifecycle.networkChanged()

        assertEquals(emptyList<XMPushServiceJob>(), fixture.jobs)
        verify(exactly = 1) { fixture.service.checkAlive(false) }
    }

    @Test
    fun `no reset on api 34 or below`() {
        val fixture = fixture(
            hasNetwork = true,
            connected = true,
            sdkInt = 34,
            wifiConnected = true,
            resetConnectionSwitchEnabled = true,
            connectionPoint = "mobile",
        )

        fixture.lifecycle.networkChanged()

        assertEquals(emptyList<XMPushServiceJob>(), fixture.jobs)
    }

    @Test
    fun `no reset when connection already on wifi`() {
        val fixture = fixture(
            hasNetwork = true,
            connected = true,
            sdkInt = 35,
            wifiConnected = true,
            resetConnectionSwitchEnabled = true,
            connectionPoint = "wifi",
        )

        fixture.lifecycle.networkChanged()

        assertEquals(emptyList<XMPushServiceJob>(), fixture.jobs)
    }

    @Test
    fun `no reset when device is not on wifi`() {
        val fixture = fixture(
            hasNetwork = true,
            connected = true,
            sdkInt = 35,
            wifiConnected = false,
            resetConnectionSwitchEnabled = true,
            connectionPoint = "mobile",
        )

        fixture.lifecycle.networkChanged()

        assertEquals(
            emptyList<XMPushServiceJob>(),
            fixture.jobs.filter { it.type == XMPushServiceJob.TYPE_RESET_CONNECT },
        )
    }

    @Test
    fun `no reset when no live connection`() {
        val fixture = fixture(
            hasNetwork = true,
            connected = false,
            connecting = false,
            sdkInt = 35,
            wifiConnected = true,
            resetConnectionSwitchEnabled = true,
            connectionPoint = "mobile",
        )

        fixture.lifecycle.networkChanged()

        verify(exactly = 0) {
            fixture.service.executeJob(match { it.type == XMPushServiceJob.TYPE_RESET_CONNECT })
        }
    }

    @Test
    fun `suspended or unknown network state defers all transport work`() {
        val fixture = fixture(hasNetwork = true, networkStateDeferred = true)

        fixture.lifecycle.networkChanged()

        verify(exactly = 0) { fixture.slimConnection.clearCachedStatus() }
        verify(exactly = 0) { fixture.service.checkAlive(any()) }
        verify(exactly = 0) { fixture.jobScheduler.removeJobs(any()) }
        verify(exactly = 0) { fixture.service.updateAlarmTimer() }
        assertEquals(emptyList<XMPushServiceJob>(), fixture.jobs)
    }

    private fun fixture(
        pushEnabled: Boolean = false,
        hasNetwork: Boolean = true,
        networkStateDeferred: Boolean = false,
        connected: Boolean = false,
        connecting: Boolean = false,
        shouldCheckAlive: Boolean = false,
        sdkInt: Int = 28,
        wifiConnected: Boolean = false,
        resetConnectionSwitchEnabled: Boolean = false,
        connectionPoint: String? = null,
    ): Fixture {
        val service = mockk<XMPushServiceCore>(relaxed = true)
        val slimConnection = mockk<SlimConnection>(relaxed = true)
        val jobScheduler = mockk<JobScheduler>(relaxed = true)
        val connectionConfiguration = mockk<ConnectionConfiguration>(relaxed = true)
        val dependencies = RecordingDependencies(
            hasNetwork = hasNetwork,
            networkStateDeferred = networkStateDeferred,
            sdkInt = sdkInt,
            wifiConnected = wifiConnected,
            resetConnectionSwitchEnabled = resetConnectionSwitchEnabled,
        )
        val jobs = mutableListOf<XMPushServiceJob>()
        every { service.slimConnection } returns slimConnection
        every { service.jobController } returns jobScheduler
        every { service.isPushEnabled() } returns pushEnabled
        every { service.isConnected } returns connected
        every { service.isConnecting } returns connecting
        every { service.shouldCheckAlive() } returns shouldCheckAlive
        every { service.connectionConfiguration } returns connectionConfiguration
        every { connectionConfiguration.connectionPoint } returns connectionPoint
        every { service.executeJob(capture(jobs)) } just Runs
        return Fixture(
            service = service,
            slimConnection = slimConnection,
            jobScheduler = jobScheduler,
            dependencies = dependencies,
            jobs = jobs,
            lifecycle = XMPushServiceStockLifecycle(service, dependencies),
        )
    }

    private data class Fixture(
        val service: XMPushServiceCore,
        val slimConnection: SlimConnection,
        val jobScheduler: JobScheduler,
        val dependencies: RecordingDependencies,
        val jobs: MutableList<XMPushServiceJob>,
        val lifecycle: XMPushServiceStockLifecycle,
    )

    private class RecordingDependencies(
        var hasNetwork: Boolean,
        var networkStateDeferred: Boolean,
        var sdkInt: Int = 28,
        var wifiConnected: Boolean = false,
        var resetConnectionSwitchEnabled: Boolean = false,
    ) : XMPushServiceStockLifecycle.Dependencies {
        var prepareAccountCalls = 0
        var registeredAccountChangeListener: MIPushAccountUtils.PushAccountChangeListener? = null

        override fun activeNetworkName(context: Context): String = "mobile"

        override fun hasNetwork(context: Context): Boolean = hasNetwork

        override fun isNetworkStateDeferred(context: Context): Boolean = networkStateDeferred

        override fun initializeRegion(service: XMPushServiceCore) = Unit

        override fun isBootCompleted(): Boolean = false

        override fun notifyServiceStarted(service: XMPushServiceCore) = Unit

        override fun prepareAccount(service: XMPushServiceCore) {
            prepareAccountCalls++
        }

        override fun setAccountChangeListener(
            listener: MIPushAccountUtils.PushAccountChangeListener,
        ) {
            registeredAccountChangeListener = listener
        }

        override fun sdkInt(): Int = sdkInt

        override fun isWifiConnected(context: Context): Boolean = wifiConnected

        override fun isResetConnectionSwitchEnabled(context: Context): Boolean =
            resetConnectionSwitchEnabled
    }
}
