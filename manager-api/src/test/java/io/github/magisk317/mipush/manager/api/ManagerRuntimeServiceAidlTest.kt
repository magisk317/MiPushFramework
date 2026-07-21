package io.github.magisk317.mipush.manager.api

import android.app.Application
import android.os.BadParcelableException
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ManagerRuntimeServiceAidlTest {
    @Test
    fun `transaction ids remain append only`() {
        assertEquals(IBinder.FIRST_CALL_TRANSACTION, IManagerRuntimeService.Stub.TRANSACTION_handshake)
        assertEquals(IBinder.FIRST_CALL_TRANSACTION + 1, IManagerRuntimeService.Stub.TRANSACTION_getConnectionSnapshot)
        assertEquals(IBinder.FIRST_CALL_TRANSACTION + 2, IManagerRuntimeService.Stub.TRANSACTION_getApplicationPage)
        assertEquals(IBinder.FIRST_CALL_TRANSACTION + 3, IManagerRuntimeService.Stub.TRANSACTION_getApplicationDetail)
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION + 4,
            IManagerRuntimeService.Stub.TRANSACTION_getApplicationDiagnostics,
        )
    }

    @Test
    fun `remote proxy transacts handshake and connection snapshot`() {
        val handshake = handshake()
        val snapshot = snapshot()
        val page = applicationPage()
        val detail = applicationDetail()
        val diagnostics = applicationDiagnostics()
        val stub = object : IManagerRuntimeService.Stub() {
            override fun handshake(clientMajor: Int, clientMinor: Int): ManagerHandshake = handshake

            override fun getConnectionSnapshot(): ManagerConnectionSnapshotDto = snapshot

            override fun getApplicationPage(query: ManagerApplicationQueryDto): ManagerApplicationPageDto = page

            override fun getApplicationDetail(
                packageName: String,
                ignoreNotRegistered: Boolean,
            ): ManagerApplicationDetailDto? = detail

            override fun getApplicationDiagnostics(
                packageName: String,
                registeredType: Int,
            ): ManagerApplicationDiagnosticsDto = diagnostics
        }

        val remote = IManagerRuntimeService.Stub.asInterface(RemoteBinder(stub))

        assertNotSame(stub, remote)
        assertEquals(handshake, remote.handshake(ManagerProtocol.MAJOR, ManagerProtocol.MINOR))
        assertEquals(snapshot, remote.connectionSnapshot)
        assertEquals(page, remote.getApplicationPage(ManagerApplicationQueryDto()))
        assertEquals(detail, remote.getApplicationDetail(detail.packageName, false))
        assertEquals(diagnostics, remote.getApplicationDiagnostics(detail.packageName, detail.registeredType))
    }

    @Test
    fun `new reader defaults fields missing from an older snapshot frame`() {
        val parcel = Parcel.obtain()
        parcel.writeWireFrame {
            writeInt(ManagerProtocol.CONNECTION_SNAPSHOT_SCHEMA_VERSION)
            writeString(ManagerProtocol.CONNECTION_STATE_CONNECTED)
        }
        parcel.setDataPosition(0)

        val snapshot = ManagerConnectionSnapshotDto.CREATOR.createFromParcel(parcel)

        assertEquals(ManagerProtocol.CONNECTION_STATE_CONNECTED, snapshot.connectionState)
        assertEquals(0L, snapshot.connectedAtMs)
        assertEquals(0, snapshot.pingIntervalMs)
        parcel.recycle()
    }

    @Test
    fun `old reader skips fields appended by a newer handshake frame`() {
        val parcel = Parcel.obtain()
        val expectedEnd: Int
        parcel.writeWireFrame {
            writeInt(ManagerProtocol.MAJOR)
            writeInt(ManagerProtocol.MINOR)
            writeString("0.6.3")
            writeLong(9L)
            writeStringList(listOf(ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT))
            writeInt(100)
            writeInt(512)
            writeString(null)
            writeString("future-field")
        }
        expectedEnd = parcel.dataPosition()
        parcel.setDataPosition(0)

        val handshake = ManagerHandshake.CREATOR.createFromParcel(parcel)

        assertEquals("0.6.3", handshake.runtimeVersionName)
        assertEquals(expectedEnd, parcel.dataPosition())
        parcel.recycle()
    }

    @Test
    fun `string payload cannot cross its declared frame`() {
        val parcel = Parcel.obtain()
        parcel.writeInt(Integer.BYTES * 4)
        parcel.writeInt(ManagerProtocol.MAJOR)
        parcel.writeInt(ManagerProtocol.MINOR)
        parcel.writeString("outside-frame")
        parcel.setDataPosition(0)

        assertThrows(BadParcelableException::class.java) {
            ManagerHandshake.CREATOR.createFromParcel(parcel)
        }
        parcel.recycle()
    }

    @Test
    fun `capability list is bounded before allocation`() {
        val parcel = Parcel.obtain()
        parcel.writeWireFrame {
            writeInt(ManagerProtocol.MAJOR)
            writeInt(ManagerProtocol.MINOR)
            writeString("0.6.3")
            writeLong(9L)
            writeInt(ManagerProtocol.MAX_CAPABILITY_COUNT + 1)
        }
        parcel.setDataPosition(0)

        assertThrows(BadParcelableException::class.java) {
            ManagerHandshake.CREATOR.createFromParcel(parcel)
        }
        parcel.recycle()
    }

    private fun handshake() = ManagerHandshake(
        protocolMajor = ManagerProtocol.MAJOR,
        protocolMinor = ManagerProtocol.MINOR,
        runtimeVersionName = "0.6.3",
        runtimeVersionCode = 9L,
        supportedCapabilities = listOf(ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT),
        maxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
        maxPayloadBytes = ManagerProtocol.DEFAULT_MAX_PAYLOAD_BYTES,
    )

    private fun snapshot() = ManagerConnectionSnapshotDto(
        connectionState = ManagerProtocol.CONNECTION_STATE_CONNECTED,
        connectedAtMs = 100L,
        lastDisconnectedAtMs = 0L,
        connectionSessionCount = 2L,
        serverHost = "push.example.test",
        serverIp = "192.0.2.1",
        keepAliveIntervalMs = 300_000,
        pingIntervalMs = 600_000,
        downstreamMessageCount = 8L,
        deliveredToAppCount = 7L,
        duplicateMessageCount = 1L,
        ackMessageCount = 6L,
        registeredPackageCount = 4,
        trackedChannelCount = 3,
        boundChannelCount = 2,
    )

    private fun applicationSummary() = ManagerApplicationSummaryDto(
        id = 11L,
        packageName = "com.example.app",
        type = 2,
        notificationOnRegister = true,
        blocked = false,
        islandEnabled = true,
        islandFocusNotification = true,
        registeredType = 1,
        existServices = true,
        appName = "Example",
        appNamePinYin = "example",
        lastReceiveTimeMs = 123L,
    )

    private fun applicationPage() = ManagerApplicationPageDto(
        items = listOf(applicationSummary()),
        stats = ManagerApplicationStatsDto(
            total = 2,
            usingMiPush = 1,
            notUsingMiPush = 1,
            registered = 1,
            notRegistered = 0,
        ),
        nextPageToken = "opaque-token",
    )

    private fun applicationDetail() = ManagerApplicationDetailDto(
        id = 11L,
        packageName = "com.example.app",
        type = 2,
        notificationOnRegister = true,
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

    private class RemoteBinder(private val delegate: IBinder) : IBinder by delegate {
        override fun queryLocalInterface(descriptor: String): IInterface? = null
    }
}
