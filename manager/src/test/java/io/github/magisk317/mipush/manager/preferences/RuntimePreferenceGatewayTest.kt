package io.github.magisk317.mipush.manager.preferences

import io.github.magisk317.mipush.common.ENABLE_ANALYTICS_KEY
import io.github.magisk317.mipush.data.OwnedPreferenceValue
import io.github.magisk317.mipush.manager.api.ManagerPreferenceEntryDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerRuntimePreferencesDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimePreferenceGatewayTest {
    @Test
    fun `successful remote write updates local mirror afterwards`() = runBlocking {
        val events = mutableListOf<String>()
        val imported = mutableListOf<OwnedPreferenceValue>()
        val gateway = gateway(
            executeRemote = {
                events += "remote"
                successResult()
            },
            importLocal = {
                events += "local"
                imported += it
            },
        )

        assertTrue(gateway.setBoolean(ENABLE_ANALYTICS_KEY, false))
        assertEquals(listOf("remote", "local"), events)
        assertEquals(ENABLE_ANALYTICS_KEY, imported.single().key)
        assertEquals("false", imported.single().value)
    }

    @Test
    fun `failed remote write leaves local mirror unchanged`() = runBlocking {
        val imported = mutableListOf<OwnedPreferenceValue>()
        val gateway = gateway(
            executeRemote = { null },
            importLocal = { imported += it },
        )

        assertFalse(gateway.setBoolean("debug_mode", true))
        assertTrue(imported.isEmpty())
    }

    @Test
    fun `runtime availability transition refreshes complete mirror`() = runBlocking {
        val available = MutableStateFlow(false)
        val synced = CompletableDeferred<List<OwnedPreferenceValue>>()
        val gateway = gateway(
            runtimeAvailable = available,
            readRemote = {
                ManagerRuntimeResult.Success(
                    ManagerRuntimePreferencesDto(
                        entries = listOf(
                            ManagerPreferenceEntryDto(
                                key = "debug_mode",
                                type = "boolean",
                                value = "true",
                                owner = "runtime",
                            ),
                            ManagerPreferenceEntryDto(
                                key = "event_retention_days",
                                type = "int",
                                value = "3",
                                owner = "runtime",
                            ),
                        ),
                    ),
                )
            },
            importLocal = { synced.complete(it) },
        )
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val syncJob = gateway.startReconnectSync(scope)
        yield()

        available.value = true

        val imported = withTimeout(1_000L) { synced.await() }
        assertEquals(listOf("debug_mode", "event_retention_days"), imported.map { it.key })
        syncJob.cancel()
    }

    private fun gateway(
        runtimeAvailable: MutableStateFlow<Boolean>? = null,
        executeRemote: suspend (RuntimePreferenceWrite) -> ManagerWriteResultDto? = { successResult() },
        readRemote: suspend () -> ManagerRuntimeResult<ManagerRuntimePreferencesDto> = {
            ManagerRuntimeResult.Success(ManagerRuntimePreferencesDto())
        },
        importLocal: suspend (List<OwnedPreferenceValue>) -> Unit = {},
    ): RuntimePreferenceGateway = RuntimePreferenceGateway(
        runtimeAvailable = runtimeAvailable ?: flowOf(false),
        executeRemote = executeRemote,
        readRemote = readRemote,
        importLocal = importLocal,
    )

    private fun successResult() = ManagerWriteResultDto(
        requestId = "request",
        status = ManagerProtocol.WRITE_STATUS_SUCCESS,
        details = "ok",
    )
}
