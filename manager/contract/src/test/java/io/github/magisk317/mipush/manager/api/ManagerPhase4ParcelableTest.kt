package io.github.magisk317.mipush.manager.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerPhase4ParcelableTest {
    @Test
    fun `protocol recognizes write capability`() {
        assertEquals(8, ManagerProtocol.MINOR)
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
    fun `application row update requires an explicit user`() {
        val request = ManagerWriteRequestDto(
            requestId = "application-update",
            operation = ManagerProtocol.WRITE_OP_UPDATE_APPLICATION,
            packageName = "com.example.app",
            argument = "2,false,true,false,true,false,false,false",
        )
        assertEquals("invalid_write_user_id", ManagerProtocol.validateWriteRequest(request))
        assertEquals(
            null,
            ManagerProtocol.validateWriteRequest(request.copy(userId = 10)),
        )
    }

    @Test
    fun `application summary DTO carries per app native live update state`() {
        assertTrue(
            ManagerApplicationSummaryDto(
                packageName = "com.example.app",
                nativeLiveUpdateEnabled = true,
            ).nativeLiveUpdateEnabled,
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
    }

    @Test
    fun `native live update toggle is a user scoped application write`() {
        val base = ManagerWriteRequestDto(
            requestId = "native-live-update",
            operation = ManagerProtocol.WRITE_OP_SET_NATIVE_LIVE_UPDATE,
            packageName = "com.example.app",
            userId = 7,
            booleanArgument = true,
        )
        assertEquals(null, ManagerProtocol.validateWriteRequest(base))
        assertEquals(
            "invalid_write_user_id",
            ManagerProtocol.validateWriteRequest(base.copy(userId = -1)),
        )
        assertEquals(
            "write_package_name_required",
            ManagerProtocol.validateWriteRequest(base.copy(packageName = "")),
        )
    }

    @Test
    fun `native live update write requires target package and user scope`() {
        val base = ManagerWriteRequestDto(
            requestId = "live-update",
            operation = ManagerProtocol.WRITE_OP_SET_NATIVE_LIVE_UPDATE,
            packageName = "com.example.app",
            userId = 999,
            booleanArgument = true,
        )
        assertEquals(null, ManagerProtocol.validateWriteRequest(base))
        assertEquals(
            "invalid_write_user_id",
            ManagerProtocol.validateWriteRequest(base.copy(userId = -1)),
        )
        assertEquals(
            "write_package_name_required",
            ManagerProtocol.validateWriteRequest(base.copy(packageName = "")),
        )
    }

}
