package io.github.magisk317.mipush.manager.api

import android.app.Application
import android.os.Parcel
import android.os.Parcelable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ManagerPhase4ParcelableTest {
    @Test
    fun `write request and result round trip`() {
        val request = ManagerWriteRequestDto(
            requestId = "req-42",
            operation = ManagerProtocol.WRITE_OP_DELETE_EVENT,
            packageName = "com.example",
            eventId = 9L,
            argument = "payload",
        )
        assertEquals(request, roundTrip(request, ManagerWriteRequestDto.CREATOR))
        val result = ManagerWriteResultDto(
            requestId = "req-42",
            status = ManagerProtocol.WRITE_STATUS_SUCCESS,
            details = "event_deleted",
            resultLong = 9L,
        )
        assertEquals(result, roundTrip(result, ManagerWriteResultDto.CREATOR))
    }

    @Test
    fun `protocol recognizes write capability`() {
        assertEquals(5, ManagerProtocol.MINOR)
        assertTrue(ManagerProtocol.KNOWN_CAPABILITIES.contains(ManagerProtocol.CAPABILITY_WRITE_COMMANDS))
        assertEquals(
            "invalid_write_request_id",
            ManagerProtocol.validateWriteRequest(ManagerWriteRequestDto(operation = "x")),
        )
    }

    private fun <T : Parcelable> roundTrip(value: T, creator: Parcelable.Creator<T>): T {
        val parcel = Parcel.obtain()
        try {
            value.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
