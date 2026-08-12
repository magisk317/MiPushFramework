package io.github.magisk317.mipush.hook.keepalive

import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeepAlivePolicyTest {
    private val enabled = KeepAliveFlags(
        ready = true,
        oomAdj = true,
        antiKill = true,
        standbyBypass = true,
    )

    @Test
    fun `oom adjustment preserves zero and privileged values`() {
        assertEquals(0, KeepAlivePolicy.desiredOomAdj(enabled, XMSF_PACKAGE_NAME, 900))
        assertNull(KeepAlivePolicy.desiredOomAdj(enabled, XMSF_PACKAGE_NAME, 0))
        assertNull(KeepAlivePolicy.desiredOomAdj(enabled, XMSF_PACKAGE_NAME, -900))
        assertNull(KeepAlivePolicy.desiredOomAdj(enabled, "other.package", 900))
        assertNull(KeepAlivePolicy.desiredOomAdj(enabled.copy(ready = false), XMSF_PACKAGE_NAME, 900))
    }

    @Test
    fun `standby and forced idle policies only change target restrictions`() {
        assertEquals(10, KeepAlivePolicy.desiredStandbyBucket(enabled, XMSF_PACKAGE_NAME, 45))
        assertNull(KeepAlivePolicy.desiredStandbyBucket(enabled, XMSF_PACKAGE_NAME, 10))
        assertNull(KeepAlivePolicy.desiredStandbyBucket(enabled, "other.package", 45))
        assertEquals(false, KeepAlivePolicy.desiredIdleState(enabled, XMSF_PACKAGE_NAME, true))
        assertNull(KeepAlivePolicy.desiredIdleState(enabled, XMSF_PACKAGE_NAME, false))
    }

    @Test
    fun `kill policy requires both current process mappings`() {
        listOf(2, 3, 4, 6, 15, 18).forEach { subReason ->
            assertTrue(
                KeepAlivePolicy.shouldSuppressKill(
                    enabled,
                    XMSF_PACKAGE_NAME,
                    reason = 13,
                    subReason = subReason,
                    currentNameMapping = true,
                    currentPidMapping = true,
                )
            )
        }
        assertFalse(
            KeepAlivePolicy.shouldSuppressKill(
                enabled,
                XMSF_PACKAGE_NAME,
                reason = 13,
                subReason = 0,
                currentNameMapping = true,
                currentPidMapping = true,
            )
        )
        assertFalse(
            KeepAlivePolicy.shouldSuppressKill(
                enabled,
                XMSF_PACKAGE_NAME,
                reason = 13,
                subReason = 2,
                currentNameMapping = true,
                currentPidMapping = false,
            )
        )
    }

    @Test
    fun `package kill policy only suppresses automatic cleanup before removal`() {
        assertTrue(
            KeepAlivePolicy.shouldSuppressPackageKill(
                flags = enabled,
                packageName = XMSF_PACKAGE_NAME,
                reason = 13,
                subReason = 6,
                callerWillRestart = false,
                doit = true,
                evenPersistent = false,
                setRemoved = false,
                uninstalling = false,
            ),
        )
        assertFalse(
            KeepAlivePolicy.shouldSuppressPackageKill(
                flags = enabled,
                packageName = XMSF_PACKAGE_NAME,
                reason = 13,
                subReason = 6,
                callerWillRestart = false,
                doit = true,
                evenPersistent = false,
                setRemoved = true,
                uninstalling = false,
            ),
        )
        assertFalse(
            KeepAlivePolicy.shouldSuppressPackageKill(
                flags = enabled,
                packageName = XMSF_PACKAGE_NAME,
                reason = 13,
                subReason = 0,
                callerWillRestart = false,
                doit = true,
                evenPersistent = false,
                setRemoved = false,
                uninstalling = false,
            ),
        )
    }
}
