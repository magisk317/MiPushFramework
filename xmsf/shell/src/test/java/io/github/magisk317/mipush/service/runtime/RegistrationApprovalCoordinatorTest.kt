package io.github.magisk317.mipush.service

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.xiaomi.push.service.PushConstants
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.push.bridge.PushShellBridgeHolder
import io.github.magisk317.mipush.push.bridge.PushShellRegistrationBridge
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppType
import io.github.magisk317.mipush.runtime.store.kmp.RuntimePendingRegistrationRow
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.github.magisk317.xposed.logging.XLog
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RegistrationApprovalCoordinatorTest {
    private val context = mockk<Context>(relaxed = true)
    private val bridge = mockk<PushShellRegistrationBridge>(relaxed = true)
    private var row = app(RegisteredAppType.ASK)
    private var pending: RuntimePendingRegistrationRow? = null
    private val bytes = byteArrayOf(1, 9, 3)
    private var originalLogLevel = 0

    @BeforeEach
    fun setUp() {
        mockkStatic(Utils::class, RegisteredApplicationDb::class)
        mockkObject(PushShellBridgeHolder)
        originalLogLevel = XLog.getLogLevel()
        XLog.setLogLevel(Int.MAX_VALUE)
        every { Utils.myUserId() } returns 7
        every { Utils.requireValidUserId(7) } returns 7
        every { context.getString(any()) } answers { "resource-${firstArg<Int>()}" }
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)
        every { PushShellBridgeHolder.registration() } returns bridge
        every { bridge.flushAppAbsentBeforeRegister(context, "target.app") } returns true
        every { RegisteredApplicationDb.getRegisteredApplication("target.app") } answers { row }
        every { RegisteredApplicationDb.getPendingRegistration("target.app", 7) } answers { pending }
        every { RegisteredApplicationDb.savePendingRegistration(any()) } answers {
            pending = firstArg()
        }
        every { RegisteredApplicationDb.clearPendingRegistration("target.app", 7) } answers {
            val removed = if (pending == null) 0 else 1
            pending = null
            removed
        }
        every { RegisteredApplicationDb.setRegistrationPolicy("target.app", any(), 7, any()) } answers {
            val type = secondArg<Int>()
            row = row.copy(type = type, blocked = type == RegisteredAppType.DENY)
            if (type != RegisteredAppType.ASK && arg<Boolean>(3)) pending = null
            true
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Utils::class, RegisteredApplicationDb::class)
        unmockkObject(PushShellBridgeHolder)
        unmockkConstructor(Intent::class)
        XLog.setLogLevel(originalLogLevel)
    }

    @Test
    fun `ask persists the real packet before stopping its dispatch`() {
        // Existing pending skips notification construction; this also proves repeated requests replace the packet.
        pending = packet(byteArrayOf(0))
        assertTrue(RegistrationApprovalCoordinator.intercept(context, "target.app", bytes, true, 2))
        assertArrayEquals(bytes, pending!!.payload)
        assertEquals(7, pending!!.userId)
        assertEquals("target.app", pending!!.packageName)
        assertTrue(pending!!.envChanged)
        assertEquals(2, pending!!.envType)
        verify(exactly = 0) { context.startService(any()) }
    }

    @Test
    fun `first request remains pending even when notification service is unavailable`() {
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns null
        assertTrue(RegistrationApprovalCoordinator.intercept(context, "target.app", bytes, true, 2))
        assertArrayEquals(bytes, pending!!.payload)
        verify(exactly = 0) { context.startService(any()) }
    }

    @Test
    fun `automatic admission passes and removes a previously approved packet`() {
        row = app(RegisteredAppType.ALLOW)
        pending = packet()
        assertFalse(RegistrationApprovalCoordinator.intercept(context, "target.app", bytes, false, 1))
        assertNull(pending)
    }

    @Test
    fun `denied admission does not save or dispatch the packet`() {
        row = app(RegisteredAppType.DENY).copy(blocked = true)
        assertTrue(RegistrationApprovalCoordinator.intercept(context, "target.app", bytes, false, 1))
        assertNull(pending)
        verify(exactly = 0) { RegisteredApplicationDb.savePendingRegistration(any()) }
        verify(exactly = 0) { context.startService(any()) }
    }

    @Test
    fun `approval with missing start component restores ask and keeps the exact packet`() {
        pending = packet()
        prepareIntent()
        every { context.startService(any()) } returns null
        val result = RegistrationApprovalCoordinator.approve(context, "target.app")
        assertFalse(result.succeeded)
        assertEquals(RegisteredAppType.ASK, row.type)
        assertArrayEquals(bytes, pending!!.payload)
    }

    @Test
    fun `successful approval replays original payload without clearing persistence before admission`() {
        pending = packet()
        val extras = prepareIntent()
        every { context.startService(any()) } answers {
            assertArrayEquals(bytes, pending!!.payload)
            mockk<ComponentName>()
        }
        val result = RegistrationApprovalCoordinator.approve(context, "target.app")
        assertTrue(result.succeeded)
        assertEquals(RegisteredAppType.ALLOW, row.type)
        assertArrayEquals(bytes, extras[PushConstants.MIPUSH_EXTRA_PAYLOAD] as ByteArray)
        assertEquals("target.app", extras[PushConstants.MIPUSH_EXTRA_APP_PACKAGE])
        assertEquals(true, extras[PushConstants.MIPUSH_EXTRA_ENV_CHANAGE])
        assertEquals(2, extras[PushConstants.MIPUSH_EXTRA_ENV_TYPE])
        assertArrayEquals(bytes, pending!!.payload)
    }

    @Test
    fun `selecting auto for pending request follows the same replay path`() {
        pending = packet()
        prepareIntent()
        every { context.startService(any()) } returns mockk<ComponentName>()
        assertTrue(RegistrationApprovalCoordinator.setPolicy(context, "target.app", RegisteredAppType.ALLOW).succeeded)
        verify(exactly = 1) { context.startService(any()) }
        assertEquals(RegisteredAppType.ALLOW, row.type)
        assertNotNull(pending)
    }

    @Test
    fun `storage failure stops ask before any dispatch`() {
        pending = packet()
        every { RegisteredApplicationDb.savePendingRegistration(any()) } throws IllegalStateException("disk unavailable")
        assertThrows(IllegalStateException::class.java) {
            RegistrationApprovalCoordinator.intercept(context, "target.app", bytes, false, 1)
        }
        verify(exactly = 0) { context.startService(any()) }
    }

    @Test
    fun `failed app absent flush leaves policy and pending packet unchanged`() {
        pending = packet()
        every { bridge.flushAppAbsentBeforeRegister(context, "target.app") } returns false
        assertFalse(RegistrationApprovalCoordinator.approve(context, "target.app").succeeded)
        assertEquals(RegisteredAppType.ASK, row.type)
        assertArrayEquals(bytes, pending!!.payload)
        verify(exactly = 0) { context.startService(any()) }
    }

    private fun prepareIntent(): MutableMap<String, Any> {
        mockkConstructor(Intent::class)
        val extras = mutableMapOf<String, Any>()
        every { anyConstructed<Intent>().setAction(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any(), any<String>()) } answers {
            extras[firstArg()] = secondArg<String>()
            self as Intent
        }
        every { anyConstructed<Intent>().putExtra(any(), any<ByteArray>()) } answers {
            extras[firstArg()] = secondArg<ByteArray>()
            self as Intent
        }
        every { anyConstructed<Intent>().putExtra(any(), any<Boolean>()) } answers {
            extras[firstArg()] = secondArg<Boolean>()
            self as Intent
        }
        every { anyConstructed<Intent>().putExtra(any(), any<Int>()) } answers {
            extras[firstArg()] = secondArg<Int>()
            self as Intent
        }
        return extras
    }

    private fun packet(payload: ByteArray = bytes) = RuntimePendingRegistrationRow(7, "target.app", payload, true, 2, 100)

    private fun app(type: Int) = RuntimeRegisteredApplicationRow(
        id = 1, packageName = "target.app", userId = 7, type = type,
        notificationOnRegister = false, registeredType = 0, appName = "Target",
    )
}
