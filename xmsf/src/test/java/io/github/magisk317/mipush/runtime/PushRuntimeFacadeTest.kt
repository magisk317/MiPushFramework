package io.github.magisk317.mipush.runtime

import io.github.magisk317.mipush.runtime.android.AndroidPushRuntime
import io.github.magisk317.mipush.runtime.core.PushRegistrationState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards that the public [PushRuntime] facade keeps delegating to the
 * [AndroidPushRuntime] implementation singleton, so the two cannot silently
 * diverge as methods are added or changed.
 */
class PushRuntimeFacadeTest {

    @Test
    fun `facade registration observations land on the AndroidPushRuntime singleton`() {
        AndroidPushRuntime.clearStateForTests()

        PushRuntime.observeRegistrationRequest("com.example.facade", "facade-test")
        PushRuntime.observeRegistrationResult("com.example.facade", success = true, source = "facade-test")

        val implSnapshot = AndroidPushRuntime.snapshot()
        assertEquals(1, implSnapshot.trackedRegistrationCount)
        assertEquals(1, implSnapshot.registeredPackageCount)
        assertEquals("com.example.facade", implSnapshot.lastRegistrationPackage)
        assertEquals(PushRegistrationState.Registered, implSnapshot.lastRegistrationState)
    }

    @Test
    fun `facade snapshot mirrors the implementation snapshot`() {
        AndroidPushRuntime.clearStateForTests()

        AndroidPushRuntime.observeRegistrationRequest("com.example.app", "test")
        AndroidPushRuntime.observeRegistrationResult("com.example.app", success = true, source = "test")

        val facadeSnapshot = PushRuntime.snapshot()
        val implSnapshot = AndroidPushRuntime.snapshot()

        assertEquals(implSnapshot.trackedRegistrationCount, facadeSnapshot.trackedRegistrationCount)
        assertEquals(implSnapshot.registeredPackageCount, facadeSnapshot.registeredPackageCount)
        assertEquals(implSnapshot.lastRegistrationPackage, facadeSnapshot.lastRegistrationPackage)
        assertEquals(implSnapshot.lastRegistrationState, facadeSnapshot.lastRegistrationState)
    }
}
