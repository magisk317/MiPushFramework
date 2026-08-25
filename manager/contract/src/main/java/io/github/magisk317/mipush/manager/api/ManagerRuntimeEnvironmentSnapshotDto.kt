package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

/** Runtime-owned environment values used by manager diagnostics. */
data class ManagerRuntimeEnvironmentSnapshotDto(
    val schemaVersion: Int = ManagerProtocol.RUNTIME_ENVIRONMENT_SCHEMA_VERSION,
    val isMiui: Int,
    val imei: String?,
    val macAddress: String?,
    val xmppServerHost: String,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeInt(isMiui)
            writeString(imei)
            writeString(macAddress)
            writeString(xmppServerHost)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerRuntimeEnvironmentSnapshotDto> =
            object : Parcelable.Creator<ManagerRuntimeEnvironmentSnapshotDto> {
                override fun createFromParcel(source: Parcel): ManagerRuntimeEnvironmentSnapshotDto =
                    source.readWireFrame {
                        ManagerRuntimeEnvironmentSnapshotDto(
                            schemaVersion = readInt(ManagerProtocol.RUNTIME_ENVIRONMENT_SCHEMA_VERSION),
                            isMiui = readInt(),
                            imei = readString(),
                            macAddress = readString(),
                            xmppServerHost = readString().orEmpty(),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerRuntimeEnvironmentSnapshotDto?> =
                    arrayOfNulls(size)
            }
    }
}
