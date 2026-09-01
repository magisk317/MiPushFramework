package io.github.magisk317.mipush.manager.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerPhase4ParcelableTest {
    @Test
    fun `protocol recognizes write capability`() {
        assertEquals(7, ManagerProtocol.MINOR)
        assertTrue(ManagerProtocol.KNOWN_CAPABILITIES.contains(ManagerProtocol.CAPABILITY_WRITE_COMMANDS))
        assertEquals(
            "invalid_write_request_id",
            ManagerProtocol.validateWriteRequest(ManagerWriteRequestDto(operation = "x")),
        )
        assertEquals(
            "invalid_write_user_id",
            ManagerProtocol.validateWriteRequest(
                ManagerWriteRequestDto(
                    requestId = "request",
                    operation = ManagerProtocol.WRITE_OP_DELETE_EVENT,
                    packageName = "com.example.app",
                    eventId = 1L,
                ),
            ),
        )
    }

    @Test
    fun `grant silent permissions accepts only supported user scopes`() {
        val base = ManagerWriteRequestDto(
            requestId = "req-grant",
            operation = ManagerProtocol.WRITE_OP_GRANT_SILENT_PERMISSIONS,
            userId = ManagerProtocol.GRANT_USER_PRIMARY,
        )
        assertEquals(
            null,
            ManagerProtocol.validateWriteRequest(base.copy(intArgument = ManagerProtocol.GRANT_USER_PRIMARY)),
        )
        assertEquals(
            null,
            ManagerProtocol.validateWriteRequest(base.copy(intArgument = ManagerProtocol.GRANT_USER_XSPACE)),
        )
        assertEquals(
            null,
            ManagerProtocol.validateWriteRequest(base.copy(intArgument = ManagerProtocol.GRANT_USER_AUTO)),
        )
        assertEquals(
            "invalid_grant_user_id",
            ManagerProtocol.validateWriteRequest(base.copy(intArgument = -2)),
        )
        assertEquals(
            "invalid_grant_user_id",
            ManagerProtocol.validateWriteRequest(base.copy(intArgument = 42)),
        )
    }

    @Test
    fun `write validation requires target package and event id for scoped operations`() {
        val base = ManagerWriteRequestDto(
            requestId = "req-42",
            operation = ManagerProtocol.WRITE_OP_DELETE_EVENT,
            userId = 0,
        )
        assertEquals("write_package_name_required", ManagerProtocol.validateWriteRequest(base))
        assertEquals(
            "invalid_write_event_id",
            ManagerProtocol.validateWriteRequest(base.copy(packageName = "com.example")),
        )
        assertEquals(
            null,
            ManagerProtocol.validateWriteRequest(base.copy(packageName = "com.example", eventId = 9L)),
        )
        assertEquals(
            "invalid_write_user_id",
            ManagerProtocol.validateWriteRequest(
                base.copy(packageName = "com.example", eventId = 9L, userId = -1),
            ),
        )
        assertEquals(
            "write_package_name_required",
            ManagerProtocol.validateWriteRequest(
                base.copy(operation = ManagerProtocol.WRITE_OP_ZYGISK_FORCE_STOP),
            ),
        )
    }
}
