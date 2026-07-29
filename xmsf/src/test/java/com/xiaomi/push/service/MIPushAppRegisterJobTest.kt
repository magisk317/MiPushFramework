package com.xiaomi.push.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MIPushAppRegisterJobTest {
    @Test
    fun `registration payload is cached only until chid 5 can send directly`() {
        assertTrue(
            shouldCacheRegistrationPayload(
                connected = false,
                clientStatus = PushClientsManager.ClientStatus.binded,
            ),
        )
        assertTrue(
            shouldCacheRegistrationPayload(
                connected = true,
                clientStatus = PushClientsManager.ClientStatus.unbind,
            ),
        )
        assertFalse(
            shouldCacheRegistrationPayload(
                connected = true,
                clientStatus = PushClientsManager.ClientStatus.binded,
            ),
        )
        assertFalse(
            shouldCacheRegistrationPayload(
                connected = true,
                clientStatus = PushClientsManager.ClientStatus.binding,
            ),
        )
    }

    private fun shouldCacheRegistrationPayload(
        connected: Boolean,
        clientStatus: PushClientsManager.ClientStatus,
    ): Boolean {
        // This is a vendor-module implementation detail. Reflect in the xmsf test instead of
        // widening production visibility solely to cross the Gradle test-module boundary.
        val companion = MIPushAppRegisterJob::class.java.getField("Companion").get(null)
        val method = companion.javaClass.methods.single {
            it.name.startsWith("shouldCacheRegistrationPayload\$")
        }
        return method.invoke(companion, connected, clientStatus) as Boolean
    }
}
