package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

data class ManagerNotificationChannelPageDto(
    val schemaVersion: Int = ManagerProtocol.NOTIFICATION_CHANNEL_PAGE_SCHEMA_VERSION,
    val packageName: String = "",
    val isHooked: Boolean = false,
    val items: List<ManagerNotificationChannelSummaryDto> = emptyList(),
    val groups: List<ManagerNotificationChannelGroupSummaryDto> = emptyList(),
    val nextPageToken: String? = null,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeString(packageName)
            writeWireBoolean(isHooked)
            writeInt(items.size)
            items.forEach { it.writeToParcel(this, flags) }
            writeInt(groups.size)
            groups.forEach { it.writeToParcel(this, flags) }
            writeString(nextPageToken)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerNotificationChannelPageDto> =
            object : Parcelable.Creator<ManagerNotificationChannelPageDto> {
                override fun createFromParcel(source: Parcel): ManagerNotificationChannelPageDto =
                    source.readWireFrame {
                        ManagerNotificationChannelPageDto(
                            schemaVersion = readInt(ManagerProtocol.NOTIFICATION_CHANNEL_PAGE_SCHEMA_VERSION),
                            packageName = readString(maxLength = ManagerProtocol.MAX_PACKAGE_NAME_LENGTH)
                                .orEmpty(),
                            isHooked = readBoolean(),
                            items = readParcelableList(
                                creator = ManagerNotificationChannelSummaryDto.CREATOR,
                                maxItems = ManagerProtocol.MAX_NOTIFICATION_CHANNEL_PAGE_ITEM_COUNT,
                            ),
                            groups = readParcelableList(
                                creator = ManagerNotificationChannelGroupSummaryDto.CREATOR,
                                maxItems = ManagerProtocol.MAX_NOTIFICATION_CHANNEL_GROUP_COUNT,
                            ),
                            nextPageToken = readString(maxLength = ManagerProtocol.MAX_PAGE_TOKEN_LENGTH),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerNotificationChannelPageDto?> = arrayOfNulls(size)
            }
    }
}
