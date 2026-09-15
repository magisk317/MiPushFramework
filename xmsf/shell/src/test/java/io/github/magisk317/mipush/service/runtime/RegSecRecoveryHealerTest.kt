package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegSecRecoveryHealerTest {

    @Test
    fun `recovered regSec heals rows that are not registered yet`() {
        assertTrue(RegSecRecoveryHealer.shouldMarkRegistered(row(RegisteredAppRegisteredType.NotRegistered)))
        assertTrue(RegSecRecoveryHealer.shouldMarkRegistered(row(RegisteredAppRegisteredType.Unregistered)))
        assertFalse(RegSecRecoveryHealer.shouldMarkRegistered(row(RegisteredAppRegisteredType.Registered)))
    }

    @Test
    fun `blocked applications are never healed`() {
        assertFalse(
            RegSecRecoveryHealer.shouldMarkRegistered(
                row(RegisteredAppRegisteredType.NotRegistered).copy(blocked = true),
            ),
        )
    }

    private fun row(registeredType: Int) = RuntimeRegisteredApplicationRow(
        id = null,
        packageName = "com.example.target",
        userId = 0,
        type = 0,
        notificationOnRegister = true,
        registeredType = registeredType,
        appName = "Example",
    )
}
