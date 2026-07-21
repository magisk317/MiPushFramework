package io.github.magisk317.mipush.manager.api

import android.app.Application
import android.os.BadParcelableException
import android.os.Parcel
import android.os.Parcelable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ManagerApplicationParcelableTest {
    @Test
    fun `application protocol parcelables round trip`() {
        val summary = applicationSummary()
        val stats = ManagerApplicationStatsDto(
            total = 7,
            usingMiPush = 4,
            notUsingMiPush = 3,
            registered = 3,
            notRegistered = 1,
        )

        assertEquals(
            ManagerApplicationQueryDto(
                query = "example",
                filterMode = 2,
                includeSystemApps = true,
                pageSize = 25,
                pageToken = "opaque-input",
            ),
            roundTrip(
                ManagerApplicationQueryDto(
                    query = "example",
                    filterMode = 2,
                    includeSystemApps = true,
                    pageSize = 25,
                    pageToken = "opaque-input",
                ),
                ManagerApplicationQueryDto.CREATOR,
            ),
        )
        assertEquals(summary, roundTrip(summary, ManagerApplicationSummaryDto.CREATOR))
        assertEquals(stats, roundTrip(stats, ManagerApplicationStatsDto.CREATOR))
        assertEquals(
            ManagerApplicationPageDto(
                items = listOf(summary),
                stats = stats,
                nextPageToken = "opaque-output",
            ),
            roundTrip(
                ManagerApplicationPageDto(
                    items = listOf(summary),
                    stats = stats,
                    nextPageToken = "opaque-output",
                ),
                ManagerApplicationPageDto.CREATOR,
            ),
        )
        assertEquals(
            applicationDetail(),
            roundTrip(applicationDetail(), ManagerApplicationDetailDto.CREATOR),
        )
        assertEquals(
            applicationDiagnostics(),
            roundTrip(applicationDiagnostics(), ManagerApplicationDiagnosticsDto.CREATOR),
        )
    }

    @Test
    fun `older application query defaults newly appended pagination fields`() {
        val parcel = Parcel.obtain()
        parcel.writeWireFrame {
            writeInt(ManagerProtocol.APPLICATION_QUERY_SCHEMA_VERSION)
            writeString("example")
            writeInt(3)
            writeWireBoolean(true)
        }
        parcel.setDataPosition(0)

        val query = ManagerApplicationQueryDto.CREATOR.createFromParcel(parcel)

        assertEquals(ManagerProtocol.DEFAULT_MAX_PAGE_SIZE, query.pageSize)
        assertNull(query.pageToken)
        parcel.recycle()
    }

    @Test
    fun `application page item count is bounded before allocation`() {
        val parcel = Parcel.obtain()
        parcel.writeWireFrame {
            writeInt(ManagerProtocol.APPLICATION_PAGE_SCHEMA_VERSION)
            writeInt(ManagerProtocol.MAX_APPLICATION_PAGE_ITEM_COUNT + 1)
        }
        parcel.setDataPosition(0)

        assertThrows(BadParcelableException::class.java) {
            ManagerApplicationPageDto.CREATOR.createFromParcel(parcel)
        }
        parcel.recycle()
    }

    @Test
    fun `application query token is bounded while unparceling`() {
        val parcel = Parcel.obtain()
        parcel.writeWireFrame {
            writeInt(ManagerProtocol.APPLICATION_QUERY_SCHEMA_VERSION)
            writeString("")
            writeInt(0)
            writeWireBoolean(false)
            writeInt(10)
            writeString("x".repeat(ManagerProtocol.MAX_PAGE_TOKEN_LENGTH + 1))
        }
        parcel.setDataPosition(0)

        assertThrows(BadParcelableException::class.java) {
            ManagerApplicationQueryDto.CREATOR.createFromParcel(parcel)
        }
        parcel.recycle()
    }

    @Test
    fun `wire frames reject declared payloads above the protocol maximum`() {
        val parcel = Parcel.obtain()
        parcel.writeInt(ManagerProtocol.MAX_WIRE_FRAME_BYTES + 1)
        parcel.setDataPosition(0)

        assertThrows(BadParcelableException::class.java) {
            ManagerApplicationQueryDto.CREATOR.createFromParcel(parcel)
        }
        parcel.recycle()
    }

    @Test
    fun `nullable fields reject a present marker without its value`() {
        val parcel = Parcel.obtain()
        parcel.writeWireFrame {
            writeInt(ManagerProtocol.APPLICATION_SUMMARY_SCHEMA_VERSION)
            writeInt(1)
        }
        parcel.setDataPosition(0)

        assertThrows(BadParcelableException::class.java) {
            ManagerApplicationSummaryDto.CREATOR.createFromParcel(parcel)
        }
        parcel.recycle()
    }

    @Test
    fun `wire booleans reject unknown encodings`() {
        val parcel = Parcel.obtain()
        parcel.writeWireFrame {
            writeInt(ManagerProtocol.APPLICATION_QUERY_SCHEMA_VERSION)
            writeString("")
            writeInt(0)
            writeInt(2)
        }
        parcel.setDataPosition(0)

        assertThrows(BadParcelableException::class.java) {
            ManagerApplicationQueryDto.CREATOR.createFromParcel(parcel)
        }
        parcel.recycle()
    }

    private fun applicationSummary() = ManagerApplicationSummaryDto(
        id = 42L,
        packageName = "com.example.app",
        type = 2,
        notificationOnRegister = true,
        islandEnabled = true,
        islandFocusNotification = true,
        registeredType = 1,
        existServices = true,
        appName = "Example",
        appNamePinYin = "example",
        lastReceiveTimeMs = 123L,
    )

    private fun applicationDetail() = ManagerApplicationDetailDto(
        id = 42L,
        packageName = "com.example.app",
        type = 2,
        notificationOnRegister = true,
        islandEnabled = true,
        islandFocusNotification = true,
        registeredType = 1,
        existServices = true,
        appName = "Example",
        appNamePinYin = "example",
        lastReceiveTimeMs = 123L,
    )

    private fun applicationDiagnostics() = ManagerApplicationDiagnosticsDto(
        hasLocalRegistration = true,
        regSecCount = 2,
        latestRegistrationEventResult = 0,
        registeredType = 1,
        inferenceReason = "local_registration",
    )

    private fun <T : Parcelable> roundTrip(value: T, creator: Parcelable.Creator<T>): T {
        val parcel = Parcel.obtain()
        value.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        return creator.createFromParcel(parcel).also { parcel.recycle() }
    }
}
