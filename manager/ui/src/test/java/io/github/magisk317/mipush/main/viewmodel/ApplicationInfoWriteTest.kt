package io.github.magisk317.mipush.main.viewmodel

import android.app.Application
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.manager.application.ManagerApplicationGateway
import io.github.magisk317.mipush.manager.application.ManagerRegistrationResult
import io.github.magisk317.mipush.manager.application.ManagerForceRegisterResult
import io.github.magisk317.mipush.manager.application.ManagerUnregisterResult
import io.github.magisk317.mipush.manager.application.RemoteApplicationDetailSource
import io.github.magisk317.mipush.manager.notification.NotificationChannelReadResult
import io.github.magisk317.mipush.manager.notification.NotificationChannelReadStatus
import io.github.magisk317.mipush.manager.notification.RemoteNotificationChannelSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationInfoWriteTest {
    private val gateway = mockk<ManagerApplicationGateway>(relaxed = true)
    private val context = mockk<Application>(relaxed = true)
    private val original = ManagerApplication(packageName = "test.app", registeredType = ManagerApplication.RegisteredType.REGISTERED)
    private val unregistered = original.copy(registeredType = ManagerApplication.RegisteredType.UNREGISTERED, blocked = true)
    private lateinit var vm: ApplicationInfoViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.getString(any()) } returns "Action failed"
        val source = mockk<RemoteApplicationDetailSource>(relaxed = true)
        val channels = mockk<RemoteNotificationChannelSource>()
        coEvery { channels.load(any()) } returns NotificationChannelReadResult.Unavailable(NotificationChannelReadStatus.FAILED)
        coEvery { gateway.getApplication(any(), any(), any()) } returns unregistered
        vm = ApplicationInfoViewModel(gateway, source, channels, mockk(relaxed = true), context)
        vm.setApplicationInfo(original)
    }

    @AfterEach
    fun cleanup() = Dispatchers.resetMain()

    @Test
    fun `block toggle sends deny and allow through policy commands without writing a full application row`() = runBlocking {
        val denied = original.copy(type = ManagerApplication.Type.DENY, blocked = true)
        coEvery { gateway.setRegistrationPolicy(context, "test.app", ManagerApplication.Type.DENY) } returns ManagerRegistrationResult(true, "Denied")
        coEvery { gateway.getApplication(any(), any(), any()) } returns denied
        vm.updateBlocked(true)
        withTimeout(5_000) { vm.applicationInfo.first { it == denied } }
        // Wait for the operation's finally block before starting the second toggle.
        withTimeout(5_000) { vm.registrationBusy.first { !it } }
        coEvery { gateway.setRegistrationPolicy(context, "test.app", ManagerApplication.Type.ALLOW) } returns ManagerRegistrationResult(true, "Allowed")
        coEvery { gateway.getApplication(any(), any(), any()) } returns original
        vm.updateBlocked(false)
        withTimeout(5_000) { vm.applicationInfo.first { it == original } }
        withTimeout(5_000) { vm.registrationBusy.first { !it } }
        coVerify(exactly = 1) { gateway.setRegistrationPolicy(context, "test.app", ManagerApplication.Type.DENY) }
        coVerify(exactly = 1) { gateway.setRegistrationPolicy(context, "test.app", ManagerApplication.Type.ALLOW) }
        coVerify(exactly = 0) { gateway.updateApplication(any()) }
    }

    @Test
    fun `policy selection uses its command and refreshes runtime state without a full row write`() = runBlocking {
        val ask = original.copy(type = ManagerApplication.Type.ASK, registrationPending = true)
        coEvery { gateway.setRegistrationPolicy(context, "test.app", ManagerApplication.Type.ASK) } returns ManagerRegistrationResult(true, "Ask enabled")
        coEvery { gateway.getApplication(any(), any(), any()) } returns ask
        assertEquals("Ask enabled", vm.setRegistrationPolicy("test.app", ManagerApplication.Type.ASK))
        assertEquals(ask, vm.applicationInfo.value)
        coVerify(exactly = 0) { gateway.updateApplication(any()) }
        coVerify(exactly = 0) { gateway.launchTargetAppAndForceRegister(any(), any(), any()) }
    }

    @Test
    fun `approval replays via its own command and does not invent server success`() = runBlocking {
        val pending = original.copy(type = ManagerApplication.Type.ASK, registeredType = ManagerApplication.RegisteredType.NOT_REGISTERED, registrationPending = true)
        vm.setApplicationInfo(pending)
        val replayed = pending.copy(registrationPending = false)
        coEvery { gateway.approvePendingRegistration(context, "test.app") } returns ManagerRegistrationResult(true, "Request replayed")
        coEvery { gateway.getApplication(any(), any(), any()) } returns replayed
        assertEquals("Request replayed", vm.approvePendingRegistration("test.app"))
        assertEquals(replayed, vm.applicationInfo.value)
        coVerify(exactly = 0) { gateway.launchTargetAppAndForceRegister(any(), any(), any()) }
        coVerify(exactly = 0) { gateway.updateApplication(any()) }
    }

    @Test
    fun `failed policy change keeps authoritative pending state and does not block a later approval`() = runBlocking {
        val pending = original.copy(type = ManagerApplication.Type.ASK, registrationPending = true)
        vm.setApplicationInfo(pending)
        coEvery { gateway.setRegistrationPolicy(any(), any(), any()) } throws IllegalStateException("offline")
        assertEquals("Action failed", vm.setRegistrationPolicy("test.app", ManagerApplication.Type.DENY))
        assertEquals(pending, vm.applicationInfo.value)
        assertFalse(vm.registrationBusy.value)
        coEvery { gateway.approvePendingRegistration(any(), any()) } returns ManagerRegistrationResult(false, "Still waiting")
        coEvery { gateway.getApplication(any(), any(), any()) } returns pending
        assertEquals("Still waiting", vm.approvePendingRegistration("test.app"))
        assertEquals(pending, vm.applicationInfo.value)
    }

    @Test
    fun `offline unregister returns visible failure and permits retry`() = runBlocking {
        coEvery { gateway.unregisterApplication(any(), any()) } throws IllegalStateException("binder offline")
        assertEquals("Action failed", vm.unregisterApplication("test.app"))
        assertFalse(vm.registrationBusy.value)
        assertEquals(original, vm.applicationInfo.value)
        coEvery { gateway.unregisterApplication(any(), any()) } returns ManagerUnregisterResult(true, "Accepted")
        assertEquals("Accepted", vm.unregisterApplication("test.app"))
        assertEquals(unregistered, vm.applicationInfo.value)
    }

    @Test
    fun `double confirm and force register during unregister do not dispatch another operation`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        coEvery { gateway.unregisterApplication(any(), any()) } coAnswers {
            entered.complete(Unit); finish.await(); ManagerUnregisterResult(true, "Accepted")
        }
        val first = async { vm.unregisterApplication("test.app") }
        withTimeout(5_000) { entered.await() }
        try {
            withTimeout(5_000) {
                vm.unregisterApplication("test.app")
                vm.launchTargetAppAndForceRegister("test.app")
            }
            coVerify(exactly = 1) { gateway.unregisterApplication(any(), any()) }
            coVerify(exactly = 0) { gateway.launchTargetAppAndForceRegister(any(), any(), any()) }
        } finally { finish.complete(Unit); first.await() }
    }

    @Test
    fun `settings changes during unregister cannot undo the block`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        coEvery { gateway.unregisterApplication(any(), any()) } coAnswers {
            entered.complete(Unit); finish.await(); ManagerUnregisterResult(true, "Accepted")
        }
        val action = async { vm.unregisterApplication("test.app") }
        withTimeout(5_000) { entered.await() }
        vm.updateBlocked(false)
        finish.complete(Unit)
        action.await()
        coVerify(exactly = 0) { gateway.updateApplication(any()) }
        coVerify(exactly = 0) { gateway.setRegistrationPolicy(any(), any(), any()) }
        assertEquals(unregistered, vm.applicationInfo.value)
    }

    @Test
    fun `cancelling unregister propagates cancellation and allows a later retry`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        coEvery { gateway.unregisterApplication(any(), any()) } coAnswers {
            entered.complete(Unit); CompletableDeferred<Unit>().await(); error("unreachable")
        }
        val action = async { vm.unregisterApplication("test.app") }
        withTimeout(5_000) { entered.await() }
        action.cancelAndJoin()
        assertTrue(action.isCancelled)
        assertFalse(vm.registrationBusy.value)
        coEvery { gateway.unregisterApplication(any(), any()) } returns ManagerUnregisterResult(true, "Accepted")
        assertEquals("Accepted", vm.unregisterApplication("test.app"))
    }

    @Test
    fun `force register refreshes authoritative state without inventing a registered result`() = runBlocking {
        vm.setApplicationInfo(unregistered)
        coEvery { gateway.launchTargetAppAndForceRegister(any(), any(), any()) } returns ManagerForceRegisterResult(true, "Requested")
        // The Activity's original state is stale, while the runtime still reports unregistered.
        assertEquals("Requested", vm.launchTargetAppAndForceRegister("test.app"))
        coVerify { gateway.launchTargetAppAndForceRegister(context, "test.app", ManagerApplication.RegisteredType.UNREGISTERED) }
        assertEquals(unregistered, vm.applicationInfo.value)
    }

    @Test
    fun `registration waits for an in flight settings write then publishes the runtime snapshot`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val launched = CompletableDeferred<Unit>()
        coEvery { gateway.updateApplication(any()) } coAnswers { entered.complete(Unit); finish.await() }
        coEvery { gateway.unregisterApplication(any(), any()) } coAnswers { launched.complete(Unit); ManagerUnregisterResult(true, "Accepted") }
        vm.updateClickFallbackEnabled(true)
        withTimeout(5_000) { entered.await() }
        val action = async { vm.unregisterApplication("test.app") }
        // Start the coroutine before checking that the gateway cannot overlap the write.
        kotlinx.coroutines.yield()
        assertFalse(launched.isCompleted)
        finish.complete(Unit)
        action.await()
        assertTrue(launched.isCompleted)
        assertEquals(unregistered, vm.applicationInfo.value)
    }
}
