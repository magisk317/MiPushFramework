package io.github.magisk317.mipush.app.di

import io.github.magisk317.mipush.runtime.android.PackageRegistrationGuard
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppType
import io.github.magisk317.mipush.runtime.store.kmp.RuntimePendingRegistrationRow
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ForceRegistrationAdmissionTest {
    private val packageName = "force.registration.test"
    private val userId = 7
    private var row = application()
    private var pending: RuntimePendingRegistrationRow? = null

    @BeforeEach
    fun setUp() {
        mockkStatic(RegisteredApplicationDb::class)
        every { RegisteredApplicationDb.getRegisteredApplication(packageName) } answers { row }
        every { RegisteredApplicationDb.getPendingRegistration(packageName, userId) } answers { pending }
        every { RegisteredApplicationDb.update(any()) } answers { row = firstArg(); 1L }
        every { RegisteredApplicationDb.savePendingRegistration(any()) } answers { pending = firstArg() }
        every { RegisteredApplicationDb.setRegistrationPolicy(packageName, any(), userId, any()) } answers {
            val type = secondArg<Int>()
            row = row.copy(type = type, blocked = type == RegisteredAppType.DENY)
            if (type != RegisteredAppType.ASK && arg<Boolean>(3)) pending = null
            PackageRegistrationGuard.recordPolicyMutation(packageName, userId)
            true
        }
    }

    @AfterEach
    fun tearDown() = unmockkStatic(RegisteredApplicationDb::class)

    @Test
    fun `existing registered state cannot confirm a new force registration`() {
        row = application().copy(registeredType = RegisteredAppRegisteredType.Registered)
        val admission = requireNotNull(prepareForceRegistrationAdmission(packageName, userId))

        assertEquals(RegisteredAppRegisteredType.Unregistered, row.registeredType)
        assertEquals(RegisteredAppRegisteredType.Registered, admission.previous.registeredType)

        restoreForceRegistrationAdmission(admission)
        assertEquals(RegisteredAppRegisteredType.Registered, row.registeredType)
    }

    @Test
    fun `failed force registration restores asking policy and its original packet`() {
        row = application().copy(type = RegisteredAppType.ASK)
        val original = RuntimePendingRegistrationRow(userId, packageName, byteArrayOf(1, 7), true, 2, 100)
        pending = original
        val admission = requireNotNull(prepareForceRegistrationAdmission(packageName, userId))
        assertNull(pending)

        restoreForceRegistrationAdmission(admission)

        assertEquals(RegisteredAppType.ASK, row.type)
        assertSame(original, pending)
    }

    @Test
    fun `new explicit automatic choice survives failure of an older temporary admission`() {
        row = application().copy(type = RegisteredAppType.DENY, blocked = true)
        val admission = requireNotNull(prepareForceRegistrationAdmission(packageName, userId))
        RegisteredApplicationDb.setRegistrationPolicy(packageName, RegisteredAppType.ALLOW, userId)

        restoreForceRegistrationAdmission(admission)

        assertEquals(RegisteredAppType.ALLOW, row.type)
        assertFalse(row.blocked)
    }

    @Test
    fun `new denial survives failure of an older temporary admission`() {
        val admission = requireNotNull(prepareForceRegistrationAdmission(packageName, userId))
        RegisteredApplicationDb.setRegistrationPolicy(packageName, RegisteredAppType.DENY, userId)

        restoreForceRegistrationAdmission(admission)

        assertEquals(RegisteredAppType.DENY, row.type)
        assertTrue(row.blocked)
    }

    @Test
    fun `server confirmation keeps the allowed policy when the caller is cancelled`() {
        row = application().copy(type = RegisteredAppType.ASK)
        val admission = requireNotNull(prepareForceRegistrationAdmission(packageName, userId))
        row = row.copy(registeredType = RegisteredAppRegisteredType.Registered)

        restoreForceRegistrationAdmission(admission)

        assertEquals(RegisteredAppType.ALLOW, row.type)
        assertEquals(RegisteredAppRegisteredType.Registered, row.registeredType)
    }

    @Test
    fun `another user policy change does not invalidate this user rollback`() {
        row = application().copy(type = RegisteredAppType.ASK)
        val admission = requireNotNull(prepareForceRegistrationAdmission(packageName, userId))
        PackageRegistrationGuard.recordPolicyMutation(packageName, 999)

        restoreForceRegistrationAdmission(admission)

        assertEquals(RegisteredAppType.ASK, row.type)
    }

    private fun application() = RuntimeRegisteredApplicationRow(
        id = 1, packageName = packageName, userId = userId, type = RegisteredAppType.ALLOW,
        notificationOnRegister = false, registeredType = RegisteredAppRegisteredType.NotRegistered, appName = "Target",
    )
}
