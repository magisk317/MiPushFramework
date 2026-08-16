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
    val timerClassName: String? = null,
    val exactAlarmAvailable: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = false,
    val deviceIdle: Boolean = false,
    val lastHealthCycleAtMs: Long = 0L,
    val lastHealthCycleAction: String? = null,
    val alarmAlive: Boolean = false,
    val alarmMode: String? = null,
    val alarmFallbackReason: String? = null,
    val alarmRegisteredAtMs: Long = 0L,
    val nextTimerAtMs: Long = 0L,
    val lastTimerCallbackAtMs: Long = 0L,
    val lastTimerCallbackDelayMs: Long = 0L,
    val deviceIdleWhitelistXmsf: Boolean = false,
    val checkedPackageName: String = "com.xiaomi.xmsf",
    val lastPingSentAtMs: Long = 0L,
    val lastReadAliveAtMs: Long = 0L,
    val lastPingTimeoutAtMs: Long = 0L,
    val lastDisconnectReason: Int? = null,
    val lastReconnectStartedAtMs: Long = 0L,
    val lastReconnectConnectedAtMs: Long = 0L,
    val lastReconnectLatencyMs: Long = 0L,
    val lastDisconnectToReconnectLatencyMs: Long = 0L,
    val lastReconnectToConnectedLatencyMs: Long = 0L,
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
            writeString(timerClassName)
            writeBoolean(exactAlarmAvailable)
            writeBoolean(ignoringBatteryOptimizations)
            writeBoolean(deviceIdle)
            writeLong(lastHealthCycleAtMs)
            writeString(lastHealthCycleAction)
            writeBoolean(alarmAlive)
            writeString(alarmMode)
            writeString(alarmFallbackReason)
            writeLong(alarmRegisteredAtMs)
            writeLong(nextTimerAtMs)
            writeLong(lastTimerCallbackAtMs)
            writeLong(lastTimerCallbackDelayMs)
            writeBoolean(deviceIdleWhitelistXmsf)
            writeString(checkedPackageName)
            writeLong(lastPingSentAtMs)
            writeLong(lastReadAliveAtMs)
            writeLong(lastPingTimeoutAtMs)
            writeInt(lastDisconnectReason ?: Int.MIN_VALUE)
            writeLong(lastReconnectStartedAtMs)
            writeLong(lastReconnectConnectedAtMs)
            writeLong(lastReconnectLatencyMs)
            writeLong(lastDisconnectToReconnectLatencyMs)
            writeLong(lastReconnectToConnectedLatencyMs)
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
                        timerClassName = readString(),
                        exactAlarmAvailable = readBoolean(false),
                        ignoringBatteryOptimizations = readBoolean(false),
                        deviceIdle = readBoolean(false),
                        lastHealthCycleAtMs = readLong(),
                        lastHealthCycleAction = readString(),
                        alarmAlive = readBoolean(false),
                        alarmMode = readString(),
                        alarmFallbackReason = readString(),
                        alarmRegisteredAtMs = readLong(),
                        nextTimerAtMs = readLong(),
                        lastTimerCallbackAtMs = readLong(),
                        lastTimerCallbackDelayMs = readLong(),
                        deviceIdleWhitelistXmsf = readBoolean(false),
                        checkedPackageName = readString("com.xiaomi.xmsf") ?: "com.xiaomi.xmsf",
                        lastPingSentAtMs = readLong(),
                        lastReadAliveAtMs = readLong(),
                        lastPingTimeoutAtMs = readLong(),
                        lastDisconnectReason = readInt(Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                        lastReconnectStartedAtMs = readLong(),
                        lastReconnectConnectedAtMs = readLong(),
                        lastReconnectLatencyMs = readLong(),
                        lastDisconnectToReconnectLatencyMs = readLong(),
                        lastReconnectToConnectedLatencyMs = readLong(),
                    )
                }

                override fun newArray(size: Int): Array<ManagerConnectionSnapshotDto?> = arrayOfNulls(size)
            }
    }
}
