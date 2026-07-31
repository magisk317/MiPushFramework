package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

/** Fields are append-only so older peers can skip additions inside the size-delimited frame. */
data class ManagerConnectionSnapshotDto(
    val schemaVersion: Int = ManagerProtocol.CONNECTION_SNAPSHOT_SCHEMA_VERSION,
    val connectionState: String,
    val connectedAtMs: Long,
    val lastDisconnectedAtMs: Long,
    val connectionSessionCount: Long,
    val serverHost: String?,
    val serverIp: String?,
    val keepAliveIntervalMs: Int,
    val pingIntervalMs: Int,
    val downstreamMessageCount: Long,
    val deliveredToAppCount: Long,
    val duplicateMessageCount: Long,
    val ackMessageCount: Long,
    val registeredPackageCount: Int,
    val trackedChannelCount: Int,
    val boundChannelCount: Int,
    val frameworkRegistered: Boolean = false,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeString(connectionState)
            writeLong(connectedAtMs)
            writeLong(lastDisconnectedAtMs)
            writeLong(connectionSessionCount)
            writeString(serverHost)
            writeString(serverIp)
            writeInt(keepAliveIntervalMs)
            writeInt(pingIntervalMs)
            writeLong(downstreamMessageCount)
            writeLong(deliveredToAppCount)
            writeLong(duplicateMessageCount)
            writeLong(ackMessageCount)
            writeInt(registeredPackageCount)
            writeInt(trackedChannelCount)
            writeInt(boundChannelCount)
            writeBoolean(frameworkRegistered)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerConnectionSnapshotDto> =
            object : Parcelable.Creator<ManagerConnectionSnapshotDto> {
                override fun createFromParcel(source: Parcel): ManagerConnectionSnapshotDto = source.readWireFrame {
                    ManagerConnectionSnapshotDto(
                        schemaVersion = readInt(ManagerProtocol.CONNECTION_SNAPSHOT_SCHEMA_VERSION),
                        connectionState = readString() ?: ManagerProtocol.CONNECTION_STATE_UNKNOWN,
                        connectedAtMs = readLong(),
                        lastDisconnectedAtMs = readLong(),
                        connectionSessionCount = readLong(),
                        serverHost = readString(),
                        serverIp = readString(),
                        keepAliveIntervalMs = readInt(),
                        pingIntervalMs = readInt(),
                        downstreamMessageCount = readLong(),
                        deliveredToAppCount = readLong(),
                        duplicateMessageCount = readLong(),
                        ackMessageCount = readLong(),
                        registeredPackageCount = readInt(),
                        trackedChannelCount = readInt(),
                        boundChannelCount = readInt(),
                        frameworkRegistered = readBoolean(false),
                    )
                }

                override fun newArray(size: Int): Array<ManagerConnectionSnapshotDto?> = arrayOfNulls(size)
            }
    }
}
