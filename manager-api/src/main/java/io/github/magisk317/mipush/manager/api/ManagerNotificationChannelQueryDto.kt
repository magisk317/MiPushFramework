package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

data class ManagerNotificationChannelQueryDto(
    val schemaVersion: Int = ManagerProtocol.NOTIFICATION_CHANNEL_QUERY_SCHEMA_VERSION,
    val packageName: String = "",
    val pageSize: Int = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
    val pageToken: String? = null,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeString(packageName)
            writeInt(pageSize)
            writeString(pageToken)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerNotificationChannelQueryDto> =
            object : Parcelable.Creator<ManagerNotificationChannelQueryDto> {
                override fun createFromParcel(source: Parcel): ManagerNotificationChannelQueryDto =
                    source.readWireFrame {
                        ManagerNotificationChannelQueryDto(
                            schemaVersion = readInt(ManagerProtocol.NOTIFICATION_CHANNEL_QUERY_SCHEMA_VERSION),
                            packageName = readString(maxLength = ManagerProtocol.MAX_PACKAGE_NAME_LENGTH).orEmpty(),
                            pageSize = readInt(ManagerProtocol.DEFAULT_MAX_PAGE_SIZE),
                            pageToken = readString(maxLength = ManagerProtocol.MAX_PAGE_TOKEN_LENGTH),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerNotificationChannelQueryDto?> = arrayOfNulls(size)
            }
    }
}
