package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

data class ManagerApplicationStatsDto(
    val schemaVersion: Int = ManagerProtocol.APPLICATION_STATS_SCHEMA_VERSION,
    val total: Int = 0,
    val usingMiPush: Int = 0,
    val notUsingMiPush: Int = 0,
    val registered: Int = 0,
    val notRegistered: Int = 0,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeInt(total)
            writeInt(usingMiPush)
            writeInt(notUsingMiPush)
            writeInt(registered)
            writeInt(notRegistered)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerApplicationStatsDto> =
            object : Parcelable.Creator<ManagerApplicationStatsDto> {
                override fun createFromParcel(source: Parcel): ManagerApplicationStatsDto = source.readWireFrame {
                    ManagerApplicationStatsDto(
                        schemaVersion = readInt(ManagerProtocol.APPLICATION_STATS_SCHEMA_VERSION),
                        total = readInt(),
                        usingMiPush = readInt(),
                        notUsingMiPush = readInt(),
                        registered = readInt(),
                        notRegistered = readInt(),
                    )
                }

                override fun newArray(size: Int): Array<ManagerApplicationStatsDto?> = arrayOfNulls(size)
            }
    }
}
