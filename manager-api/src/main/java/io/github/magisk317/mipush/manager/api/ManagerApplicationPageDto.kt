package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

/** A bounded application page. [nextPageToken] is opaque to clients. */
data class ManagerApplicationPageDto(
    val schemaVersion: Int = ManagerProtocol.APPLICATION_PAGE_SCHEMA_VERSION,
    val userId: Int = 0,
    val items: List<ManagerApplicationSummaryDto> = emptyList(),
    val stats: ManagerApplicationStatsDto = ManagerApplicationStatsDto(),
    val nextPageToken: String? = null,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeInt(items.size)
            items.forEach { it.writeToParcel(this, flags) }
            stats.writeToParcel(this, flags)
            writeString(nextPageToken)
            writeInt(userId)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerApplicationPageDto> =
            object : Parcelable.Creator<ManagerApplicationPageDto> {
                override fun createFromParcel(source: Parcel): ManagerApplicationPageDto = source.readWireFrame {
                    ManagerApplicationPageDto(
                        schemaVersion = readInt(ManagerProtocol.APPLICATION_PAGE_SCHEMA_VERSION),
                        items = readParcelableList(
                            creator = ManagerApplicationSummaryDto.CREATOR,
                            maxItems = ManagerProtocol.MAX_APPLICATION_PAGE_ITEM_COUNT,
                        ),
                        stats = readParcelable(
                            creator = ManagerApplicationStatsDto.CREATOR,
                            defaultValue = ManagerApplicationStatsDto(),
                        ),
                        nextPageToken = readString(maxLength = ManagerProtocol.MAX_PAGE_TOKEN_LENGTH),
                        userId = readInt(-1),
                    )
                }

                override fun newArray(size: Int): Array<ManagerApplicationPageDto?> = arrayOfNulls(size)
            }
    }
}
