package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

/** Full application state returned by the detail endpoint. */
data class ManagerApplicationDetailDto(
    val schemaVersion: Int = ManagerProtocol.APPLICATION_DETAIL_SCHEMA_VERSION,
    val id: Long? = null,
    val packageName: String = "",
    val type: Int = 0,
    val notificationOnRegister: Boolean = false,
    val blocked: Boolean = false,
    val islandEnabled: Boolean = true,
    val islandFocusNotification: Boolean = false,
    val registeredType: Int = 0,
    val existServices: Boolean = false,
    val appName: String = "",
    val appNamePinYin: String = "",
    val lastReceiveTimeMs: Long = 0L,
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
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerApplicationDetailDto> =
            object : Parcelable.Creator<ManagerApplicationDetailDto> {
                override fun createFromParcel(source: Parcel): ManagerApplicationDetailDto = source.readWireFrame {
                    ManagerApplicationDetailDto(
                        schemaVersion = readInt(ManagerProtocol.APPLICATION_DETAIL_SCHEMA_VERSION),
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
                    )
                }

                override fun newArray(size: Int): Array<ManagerApplicationDetailDto?> = arrayOfNulls(size)
            }
    }
}
