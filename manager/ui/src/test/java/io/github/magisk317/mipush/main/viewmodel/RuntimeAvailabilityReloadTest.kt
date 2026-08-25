package io.github.magisk317.mipush.main.viewmodel

import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeAvailabilityReloadTest {
    @Test
    fun `new availability cancels an in-flight reload`() = runBlocking {
        val availability = MutableStateFlow<ManagerRuntimeAvailability>(ManagerRuntimeAvailability.Disconnected)
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()

        val collector = launch {
            collectAvailableRuntimeReloads(
                availability = availability,
                shouldReloadWhenAvailable = { true },
            ) {
                try {
                    started.complete(Unit)
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }

        availability.value = ManagerRuntimeAvailability.Available(handshake = testHandshake())
        withTimeout(1_000) { started.await() }
        assertTrue(started.isCompleted)

        availability.value = ManagerRuntimeAvailability.Disconnected
        withTimeout(1_000) { cancelled.await() }
        assertTrue(cancelled.isCompleted)

        collector.cancel()
    }

    private fun testHandshake() = ManagerHandshake(
        protocolMajor = 1,
        protocolMinor = 0,
        runtimeVersionName = "test",
        runtimeVersionCode = 1L,
        supportedCapabilities = emptyList(),
        maxPageSize = 100,
        maxPayloadBytes = 1024,
    )
}
