package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

data class ManagerNotificationChannelSummaryDto(
    val schemaVersion: Int = ManagerProtocol.NOTIFICATION_CHANNEL_SUMMARY_SCHEMA_VERSION,
    val id: String = "",
    val name: String = "",
    val importance: Int = 0,
    val groupId: String? = null,
    val description: String? = null,
    val enabled: Boolean = true,
    val managedByMiPush: Boolean = false,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeString(id)
            writeString(name)
            writeInt(importance)
            writeString(groupId)
            writeString(description)
            writeWireBoolean(enabled)
            writeWireBoolean(managedByMiPush)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerNotificationChannelSummaryDto> =
            object : Parcelable.Creator<ManagerNotificationChannelSummaryDto> {
                override fun createFromParcel(source: Parcel): ManagerNotificationChannelSummaryDto =
                    source.readWireFrame {
                        ManagerNotificationChannelSummaryDto(
                            schemaVersion = readInt(ManagerProtocol.NOTIFICATION_CHANNEL_SUMMARY_SCHEMA_VERSION),
                            id = readString().orEmpty(),
                            name = readString().orEmpty(),
                            importance = readInt(),
                            groupId = readString(),
                            description = readString(),
                            enabled = readBoolean(true),
                            managedByMiPush = readBoolean(),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerNotificationChannelSummaryDto?> = arrayOfNulls(size)
            }
    }
}
