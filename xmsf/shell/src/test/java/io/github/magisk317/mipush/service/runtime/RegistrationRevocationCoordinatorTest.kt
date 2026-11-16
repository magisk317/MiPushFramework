package io.github.magisk317.mipush.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.app.di.XmsfManagerApplicationGateway
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.platform.support.PermissionUtils
import io.github.magisk317.mipush.push.bridge.PushShellBridge
import io.github.magisk317.mipush.push.bridge.PushShellBridgeHolder
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.PushRuntimePendingPacketStore
import io.github.magisk317.mipush.runtime.PushRuntimeRegistrationTaskStore
import io.github.magisk317.mipush.runtime.android.PackageRegistrationGuard
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppType
import io.github.magisk317.mipush.runtime.store.kmp.RuntimePendingRegistrationRow
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.github.magisk317.mipush.testing.diskPreferencesContext
import io.github.magisk317.mipush.utils.RegistrationHelper
import io.github.magisk317.xposed.logging.XLog
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RegistrationRevocationCoordinatorTest {
    @TempDir lateinit var directory: Path
    private val packageName = "target.app"
    private val userId = 7
    private lateinit var context: Context
    private val bridge = mockk<PushShellBridge>(relaxed = true)
    private var row = application()
    private var appId: String? = "registered-app-id"
    private var pending: RuntimePendingRegistrationRow? = null
    private var originalLogLevel = 0

    @BeforeEach
    fun setUp() {
        context = diskPreferencesContext(directory)
        every { context.getString(any()) } answers { "resource-${firstArg<Int>()}" }
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)
        mockkStatic(Utils::class, RegisteredApplicationDb::class, PermissionUtils::class)
        mockkObject(PushShellBridgeHolder)
        mockkConstructor(RegistrationHelper::class)
        originalLogLevel = XLog.getLogLevel()
        XLog.setLogLevel(Int.MAX_VALUE)
        every { Utils.myUserId() } returns userId
        every { Utils.requireValidUserId(userId) } returns userId
        every { Utils.removeRegSec(packageName, userId) } just Runs
        every { Utils.removeLastReceiveTime(packageName, userId) } just Runs
        every { PermissionUtils.requestRootAccess() } returns true
        every { anyConstructed<RegistrationHelper>().removeMiPushData(userId) } returns true
        every { PushShellBridgeHolder.registration() } returns bridge
        every { PushShellBridgeHolder.packageState() } returns bridge
        every { bridge.rememberedAppId(any(), packageName) } answers { appId }
        every { bridge.forgetRegisteredPackage(any(), packageName) } answers { appId = null }
        every { bridge.reportAppAbsent(any(), packageName, any(), any()) } returns true
        every { bridge.flushAppAbsentBeforeRegister(any(), packageName) } returns true
        every { RegisteredApplicationDb.getRegisteredApplication(packageName) } answers { row }
        every { RegisteredApplicationDb.update(any()) } answers { row = firstArg(); 1L }
        every { RegisteredApplicationDb.setRegistrationPolicy(packageName, any(), userId, any()) } answers {
            val type = secondArg<Int>()
            row = row.copy(type = type, blocked = type == RegisteredAppType.DENY)
            if (type != RegisteredAppType.ASK && arg<Boolean>(3)) pending = null
            PackageRegistrationGuard.recordPolicyMutation(packageName, userId)
            true
        }
        every { RegisteredApplicationDb.getPendingRegistration(packageName, userId) } answers { pending }
        every { RegisteredApplicationDb.savePendingRegistration(any()) } answers { pending = firstArg() }
        every { RegisteredApplicationDb.clearPendingRegistration(packageName, userId) } answers { pending = null; 1 }
        PushRuntime.clearStateForTests()
        PushRuntimePendingPacketStore.clearForTests()
        PushRuntimeRegistrationTaskStore.clearForTests()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        PushRuntime.clearStateForTests()
        PushRuntimePendingPacketStore.clearForTests()
        PushRuntimeRegistrationTaskStore.clearForTests()
        XLog.setLogLevel(originalLogLevel)
    }

    @Test
    fun `denying an already registered app revokes and clears it before reporting success`() = runBlocking {
        pending = RuntimePendingRegistrationRow(userId, packageName, byteArrayOf(1), false, 1, 100)
        PushRuntimePendingPacketStore.cacheRegistrationRequest(packageName, byteArrayOf(2), userId)
        PushRuntimeRegistrationTaskStore.cache(packageName, Intent("register"), "test", androidUserId = userId)
        every { bridge.reportAppAbsent(context, packageName, "registered-app-id", "manager_deny") } answers {
            assertEquals(RegisteredAppType.DENY, row.type)
            assertTrue(row.blocked)
            assertNull(pending)
            assertEquals(0, PushRuntimePendingPacketStore.pendingRegistrationCount(userId))
            assertEquals(0, PushRuntimeRegistrationTaskStore.pendingCount(userId))
            assertTrue(RegistrationRevocationJournal.hasPending(context, packageName, userId))
            true
        }

        val result = XmsfManagerApplicationGateway().setRegistrationPolicy(context, packageName, RegisteredAppType.DENY)

        assertTrue(result.succeeded)
        assertEquals(RegisteredAppRegisteredType.Unregistered, row.registeredType)
        assertNull(appId)
        assertFalse(RegistrationRevocationJournal.hasPending(diskPreferencesContext(directory), packageName, userId))
        verify(exactly = 1) { PermissionUtils.requestRootAccess() }
        verify(exactly = 1) { anyConstructed<RegistrationHelper>().removeMiPushData(userId) }
    }

    @Test
    fun `denying an app that never registered only blocks future requests without root cleanup`() {
        row = application().copy(registeredType = RegisteredAppRegisteredType.NotRegistered)
        appId = null

        val result = RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.DENY)

        assertTrue(result.succeeded)
        assertEquals("resource-${R.string.registration_denied}", result.message)
        assertEquals(RegisteredAppType.DENY, row.type)
        assertTrue(row.blocked)
        assertEquals(RegisteredAppRegisteredType.NotRegistered, row.registeredType)
        verify(exactly = 0) { PermissionUtils.requestRootAccess() }
        verify(exactly = 0) { anyConstructed<RegistrationHelper>().removeMiPushData(any()) }
        verify(exactly = 0) { bridge.reportAppAbsent(any(), any(), any(), any()) }
    }

    @Test
    fun `known app id without confirmed registration does not trigger revocation or root`() {
        row = application().copy(registeredType = RegisteredAppRegisteredType.NotRegistered)
        appId = "known-but-unconfirmed"

        assertTrue(RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.DENY).succeeded)
        assertTrue(row.blocked)
        assertEquals(RegisteredAppRegisteredType.NotRegistered, row.registeredType)
        assertFalse(RegistrationRevocationJournal.hasPending(context, packageName, userId))
        verify(exactly = 0) { PermissionUtils.requestRootAccess() }
        verify(exactly = 0) { anyConstructed<RegistrationHelper>().removeMiPushData(any()) }
        verify(exactly = 0) { bridge.reportAppAbsent(any(), any(), any(), any()) }
    }

    @Test
    fun `denying then allowing preserves all user notification preferences`() {
        row = application().copy(
            islandEnabled = true,
            islandFocusNotification = true,
            clickFallbackEnabled = true,
            suppressWhenDisabled = true,
            countLimitExempt = true,
        )
        val preferences = row

        assertTrue(RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.DENY).succeeded)
        assertTrue(row.blocked)
        assertTrue(RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.ALLOW).succeeded)

        assertFalse(row.blocked)
        assertEquals(preferences.islandEnabled, row.islandEnabled)
        assertEquals(preferences.islandFocusNotification, row.islandFocusNotification)
        assertEquals(preferences.clickFallbackEnabled, row.clickFallbackEnabled)
        assertEquals(preferences.suppressWhenDisabled, row.suppressWhenDisabled)
        assertEquals(preferences.countLimitExempt, row.countLimitExempt)
    }

    @Test
    fun `failed submission keeps denial and registration evidence for a later retry`() {
        every { bridge.reportAppAbsent(any(), packageName, any(), any()) } returns false

        assertFalse(RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.DENY).succeeded)
        assertTrue(row.blocked)
        assertEquals(RegisteredAppRegisteredType.Registered, row.registeredType)
        assertEquals("registered-app-id", RegistrationRevocationJournal.pendingAppId(diskPreferencesContext(directory), packageName, userId))
        verify(exactly = 0) { PermissionUtils.requestRootAccess() }
        every { bridge.reportAppAbsent(any(), packageName, any(), any()) } returns true

        assertTrue(RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.DENY).succeeded)
        assertEquals(RegisteredAppRegisteredType.Unregistered, row.registeredType)
    }

    @Test
    fun `root denial leaves app denied and prevents allowing before revocation is completed`() {
        every { PermissionUtils.requestRootAccess() } returns false
        val denied = RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.DENY)
        assertFalse(denied.succeeded)
        assertTrue(row.blocked)
        assertEquals(RegisteredAppRegisteredType.Registered, row.registeredType)
        assertFalse(RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.ALLOW).succeeded)
        assertFalse(RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.ASK).succeeded)
        verify(exactly = 0) { anyConstructed<RegistrationHelper>().removeMiPushData(any()) }
    }

    @Test
    fun `partial app cleanup remains retryable after reopening preferences`() {
        every { anyConstructed<RegistrationHelper>().removeMiPushData(userId) } returns false
        assertFalse(RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.DENY).succeeded)
        assertEquals(RegisteredAppRegisteredType.Registered, row.registeredType)
        assertNotNull(appId)
        val reopened = diskPreferencesContext(directory)
        every { reopened.getString(any()) } answers { "resource-${firstArg<Int>()}" }
        every { reopened.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)
        every { anyConstructed<RegistrationHelper>().removeMiPushData(userId) } returns true

        assertTrue(RegistrationApprovalCoordinator.setPolicy(reopened, packageName, RegisteredAppType.DENY).succeeded)
        assertFalse(RegistrationRevocationJournal.hasPending(diskPreferencesContext(directory), packageName, userId))
    }

    @Test
    fun `offline revocation reports queued and admission waits for the older request`() {
        every { bridge.hasPendingAppAbsent(context, packageName) } returns true
        val result = RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.DENY)
        assertTrue(result.succeeded)
        assertEquals("resource-${R.string.unregister_queued}", result.message)
        every { bridge.flushAppAbsentBeforeRegister(context, packageName) } returns false
        assertFalse(RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.ALLOW).succeeded)
        assertTrue(row.blocked)
        every { bridge.flushAppAbsentBeforeRegister(context, packageName) } returns true
        assertTrue(RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.ALLOW).succeeded)
        assertFalse(row.blocked)
        assertEquals(RegisteredAppRegisteredType.Unregistered, row.registeredType)
    }

    @Test
    fun `ask and pending approval remain available after completed revocation without root`() {
        assertTrue(RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.DENY).succeeded)
        assertTrue(RegistrationApprovalCoordinator.setPolicy(context, packageName, RegisteredAppType.ASK).succeeded)
        pending = RuntimePendingRegistrationRow(userId, packageName, byteArrayOf(3), false, 1, 200)
        every { PermissionUtils.requestRootAccess() } answers { error("approval must not request root") }
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setAction(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any(), any<String>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any(), any<ByteArray>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any(), any<Boolean>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any(), any<Int>()) } answers { self as Intent }
        every { context.startService(any()) } returns mockk()

        assertTrue(RegistrationApprovalCoordinator.approve(context, packageName).succeeded)
        assertEquals(RegisteredAppType.ALLOW, row.type)
    }

    @Test
    fun `legacy unregister operation shares the deny revocation path`() = runBlocking {
        assertTrue(XmsfManagerApplicationGateway().unregisterApplication(context, packageName).succeeded)
        assertEquals(RegisteredAppType.DENY, row.type)
        assertEquals(RegisteredAppRegisteredType.Unregistered, row.registeredType)
        verify(exactly = 1) { bridge.reportAppAbsent(context, packageName, "registered-app-id", "manager_deny") }
    }

    private fun application() = RuntimeRegisteredApplicationRow(
        id = 1, packageName = packageName, userId = userId, type = RegisteredAppType.ALLOW,
        notificationOnRegister = false, registeredType = RegisteredAppRegisteredType.Registered, appName = "Target",
    )
}
