package io.github.magisk317.mipush.manager.runtime

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerRuntimeCallerPolicyTest {
    @Test
    fun `runtime process may call its own binder`() {
        assertTrue(
            ManagerRuntimeCallerPolicy.isAllowed(
                callingUid = 10_001,
                runtimeUid = 10_001,
                callerPackages = emptyList(),
                signaturesMatch = false,
            ),
        )
    }

    @Test
    fun `signed manager package is allowed`() {
        assertTrue(
            ManagerRuntimeCallerPolicy.isAllowed(
                callingUid = 10_002,
                runtimeUid = 10_001,
                callerPackages = listOf(ManagerProtocol.MANAGER_PACKAGE),
                signaturesMatch = true,
            ),
        )
    }

    @Test
    fun `matching signature without manager package is rejected`() {
        assertFalse(
            ManagerRuntimeCallerPolicy.isAllowed(
                callingUid = 10_002,
                runtimeUid = 10_001,
                callerPackages = listOf("example.untrusted"),
                signaturesMatch = true,
            ),
        )
    }

    @Test
    fun `manager package with a different signature is rejected`() {
        assertFalse(
            ManagerRuntimeCallerPolicy.isAllowed(
                callingUid = 10_002,
                runtimeUid = 10_001,
                callerPackages = listOf(ManagerProtocol.MANAGER_PACKAGE),
                signaturesMatch = false,
            ),
        )
    }
}
