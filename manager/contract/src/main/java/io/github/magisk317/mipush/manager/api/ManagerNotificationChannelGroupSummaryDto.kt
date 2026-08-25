package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

data class ManagerNotificationChannelGroupSummaryDto(
    val schemaVersion: Int = ManagerProtocol.NOTIFICATION_CHANNEL_GROUP_SUMMARY_SCHEMA_VERSION,
    val id: String = "",
    val name: String = "",
    val managedByMiPush: Boolean = false,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeString(id)
            writeString(name)
            writeWireBoolean(managedByMiPush)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerNotificationChannelGroupSummaryDto> =
            object : Parcelable.Creator<ManagerNotificationChannelGroupSummaryDto> {
                override fun createFromParcel(source: Parcel): ManagerNotificationChannelGroupSummaryDto =
                    source.readWireFrame {
                        ManagerNotificationChannelGroupSummaryDto(
                            schemaVersion = readInt(
                                ManagerProtocol.NOTIFICATION_CHANNEL_GROUP_SUMMARY_SCHEMA_VERSION,
                            ),
                            id = readString().orEmpty(),
                            name = readString().orEmpty(),
                            managedByMiPush = readBoolean(),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerNotificationChannelGroupSummaryDto?> =
                    arrayOfNulls(size)
            }
    }
}
