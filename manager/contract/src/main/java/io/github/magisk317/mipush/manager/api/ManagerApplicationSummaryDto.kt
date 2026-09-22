package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

/** A bounded, platform-independent application row used by the list endpoint. */
data class ManagerApplicationSummaryDto(
    val schemaVersion: Int = ManagerProtocol.APPLICATION_SUMMARY_SCHEMA_VERSION,
    val id: Long? = null,
    val packageName: String = "",
    val type: Int = 0,
    val notificationOnRegister: Boolean = false,
    val blocked: Boolean = false,
    val islandEnabled: Boolean = true,
    val islandFocusNotification: Boolean = false,
    val clickFallbackEnabled: Boolean = false,
    val registeredType: Int = 0,
    val existServices: Boolean = false,
    val appName: String = "",
    val appNamePinYin: String = "",
    val lastReceiveTimeMs: Long = 0L,
    val userId: Int = 0,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeWireNullableLong(id)
            writeString(packageName)
            writeInt(type)
            writeWireBoolean(notificationOnRegister)
            writeWireBoolean(blocked)
            writeWireBoolean(islandEnabled)
            writeWireBoolean(islandFocusNotification)
            writeInt(registeredType)
            writeWireBoolean(existServices)
            writeString(appName)
            writeString(appNamePinYin)
            writeLong(lastReceiveTimeMs)
            writeInt(userId)
            // Appended at the frame tail: readWireFrame defaults keep older frames (for example a
            // not-yet-reloaded module dex on the runtime side) readable without the new field.
            writeWireBoolean(clickFallbackEnabled)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerApplicationSummaryDto> =
            object : Parcelable.Creator<ManagerApplicationSummaryDto> {
                override fun createFromParcel(source: Parcel): ManagerApplicationSummaryDto = source.readWireFrame {
                    ManagerApplicationSummaryDto(
                        schemaVersion = readInt(ManagerProtocol.APPLICATION_SUMMARY_SCHEMA_VERSION),
                        id = readNullableLong(),
                        packageName = readString().orEmpty(),
                        type = readInt(),
                        notificationOnRegister = readBoolean(),
                        blocked = readBoolean(),
                        islandEnabled = readBoolean(true),
                        islandFocusNotification = readBoolean(),
                        registeredType = readInt(),
                        existServices = readBoolean(),
                        appName = readString().orEmpty(),
                        appNamePinYin = readString().orEmpty(),
                        lastReceiveTimeMs = readLong(),
                        userId = readInt(-1),
                        clickFallbackEnabled = readBoolean(),
                    )
                }

                override fun newArray(size: Int): Array<ManagerApplicationSummaryDto?> = arrayOfNulls(size)
            }
    }
}
