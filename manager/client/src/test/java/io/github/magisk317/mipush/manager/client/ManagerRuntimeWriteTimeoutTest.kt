package io.github.magisk317.mipush.manager.client

import io.github.magisk317.mipush.manager.api.IManagerRuntimeService
import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ManagerRuntimeWriteTimeoutTest {
    @Test
    fun `deny and unregister include root prompt and local cleanup time`() {
        for (request in listOf(
            write(ManagerProtocol.WRITE_OP_SET_REGISTRATION_POLICY, policy = 3),
            write(ManagerProtocol.WRITE_OP_UNREGISTER_APPLICATION),
        )) {
            val policy = ManagerRuntimeClientPolicy.writeCallPolicy(request)
            assertEquals(30_000L, policy.timeoutMillis)
            assertFalse(policy.releaseSessionOnTimeout)
        }
    }

    @Test
    fun `force registration includes root launch cleanup and real confirmation wait`() {
        val policy = ManagerRuntimeClientPolicy.writeCallPolicy(write(ManagerProtocol.WRITE_OP_LAUNCH_TARGET_FORCE_REGISTER))
        assertEquals(60_000L, policy.timeoutMillis)
        assertTrue(requireNotNull(policy.timeoutMillis) > 10_000L + 3_000L + 10_000L + 9_000L + 8_000L + 500L + 6_000L)
        assertFalse(policy.releaseSessionOnTimeout)
    }

    @Test
    fun `automatic asking approval and ordinary writes keep the default eight second budget`() {
        for (request in listOf(
            write(ManagerProtocol.WRITE_OP_SET_REGISTRATION_POLICY, policy = 2),
            write(ManagerProtocol.WRITE_OP_SET_REGISTRATION_POLICY, policy = 0),
            write(ManagerProtocol.WRITE_OP_APPROVE_REGISTRATION),
            write(ManagerProtocol.WRITE_OP_UPDATE_APPLICATION),
            write("unknown_operation"),
        )) {
            val policy = ManagerRuntimeClientPolicy.writeCallPolicy(request)
            assertEquals(USE_CLIENT_CALL_TIMEOUT_MILLIS, policy.timeoutMillis)
            assertTrue(policy.releaseSessionOnTimeout)
        }
        assertEquals(8_000L, ManagerRuntimeClient.DEFAULT_CALL_TIMEOUT_MS)
    }

    @Test
    fun `facade uses each selected budget while executing the real operation block`() = runBlocking {
        val caller = ExercisingCaller()
        val facade = ManagerRuntimeOperationFacade(caller, eventPageCallTimeoutMillis = 8_000L)
        for ((request, expectedTimeout) in listOf(
            write(ManagerProtocol.WRITE_OP_SET_REGISTRATION_POLICY, policy = 3) to 30_000L,
            write(ManagerProtocol.WRITE_OP_UNREGISTER_APPLICATION) to 30_000L,
            write(ManagerProtocol.WRITE_OP_LAUNCH_TARGET_FORCE_REGISTER) to 60_000L,
            write(ManagerProtocol.WRITE_OP_SET_REGISTRATION_POLICY, policy = 2) to 8_000L,
            write(ManagerProtocol.WRITE_OP_SET_REGISTRATION_POLICY, policy = 0) to 8_000L,
            write(ManagerProtocol.WRITE_OP_UPDATE_APPLICATION) to 8_000L,
        )) {
            val result = facade.executeWrite(request)
            assertTrue(result is ManagerRuntimeResult.Success)
            assertEquals(request, caller.lastWrite)
            assertEquals(expectedTimeout, caller.effectiveTimeout)
            assertEquals(expectedTimeout == 8_000L, caller.releaseSessionOnTimeout)
        }
    }

    @Test
    fun `ordinary detail reads still use the default eight second timeout`() = runBlocking {
        val caller = ExercisingCaller()
        val facade = ManagerRuntimeOperationFacade(caller, eventPageCallTimeoutMillis = 8_000L)
        val result = facade.getApplicationDetail("test.app")
        assertTrue(result is ManagerRuntimeResult.Success)
        assertEquals("getApplicationDetail", caller.lastMethod)
        assertEquals(8_000L, caller.effectiveTimeout)
        assertTrue(caller.releaseSessionOnTimeout)
    }

    private fun write(operation: String, policy: Int = 0) = ManagerWriteRequestDto(
        requestId = "request-$operation-$policy",
        operation = operation,
        packageName = "test.app",
        userId = 0,
        intArgument = policy,
    )

    /** Executes the facade's service lambda and both protocol validators, without replacing its policy. */
    private class ExercisingCaller : ManagerRuntimeCapabilityCaller {
        var effectiveTimeout: Long? = null
        var releaseSessionOnTimeout = true
        var lastWrite: ManagerWriteRequestDto? = null
        var lastMethod: String? = null
        private val handshake = ManagerHandshake(
            protocolMajor = ManagerProtocol.MAJOR,
            protocolMinor = ManagerProtocol.MINOR,
            runtimeVersionName = "test",
            runtimeVersionCode = 1,
            supportedCapabilities = listOf(ManagerProtocol.CAPABILITY_WRITE_COMMANDS, ManagerProtocol.CAPABILITY_APPLICATION_DETAIL),
            maxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
            maxPayloadBytes = ManagerProtocol.DEFAULT_MAX_PAYLOAD_BYTES,
        )
        private val service = Proxy.newProxyInstance(
            IManagerRuntimeService::class.java.classLoader,
            arrayOf(IManagerRuntimeService::class.java),
        ) { _, method, args ->
            lastMethod = method.name
            when (method.name) {
                "executeWrite" -> {
                    val request = args!![0] as ManagerWriteRequestDto
                    lastWrite = request
                    ManagerWriteResultDto(requestId = request.requestId, status = ManagerProtocol.WRITE_STATUS_SUCCESS, details = "completed")
                }
                "getApplicationDetail" -> null
                else -> error("Unexpected service call: ${method.name}")
            }
        } as IManagerRuntimeService

        override suspend fun <T> callCapability(
            capability: String,
            requestValidator: (ManagerHandshake) -> String?,
            callTimeoutMillis: Long?,
            releaseSessionOnTimeout: Boolean,
            validator: (T, ManagerHandshake) -> String?,
            block: (IManagerRuntimeService) -> T,
        ): ManagerRuntimeResult<T> {
            assertTrue(capability in handshake.supportedCapabilities)
            assertNull(requestValidator(handshake))
            effectiveTimeout = if (callTimeoutMillis == USE_CLIENT_CALL_TIMEOUT_MILLIS) ManagerRuntimeClient.DEFAULT_CALL_TIMEOUT_MS else callTimeoutMillis
            this.releaseSessionOnTimeout = releaseSessionOnTimeout
            val value = block(service)
            assertNull(validator(value, handshake))
            return ManagerRuntimeResult.Success(value)
        }
    }
}
