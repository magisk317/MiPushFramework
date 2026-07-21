package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

/** A bounded event page ordered by descending id. */
data class ManagerEventPageDto(
    val schemaVersion: Int = ManagerProtocol.EVENT_PAGE_SCHEMA_VERSION,
    val items: List<ManagerEventSummaryDto> = emptyList(),
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeInt(items.size)
            items.forEach { it.writeToParcel(this, flags) }
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerEventPageDto> =
            object : Parcelable.Creator<ManagerEventPageDto> {
                override fun createFromParcel(source: Parcel): ManagerEventPageDto = source.readWireFrame {
                    ManagerEventPageDto(
                        schemaVersion = readInt(ManagerProtocol.EVENT_PAGE_SCHEMA_VERSION),
                        items = readParcelableList(
                            creator = ManagerEventSummaryDto.CREATOR,
                            maxItems = ManagerProtocol.MAX_EVENT_PAGE_ITEM_COUNT,
                        ),
                    )
                }

                override fun newArray(size: Int): Array<ManagerEventPageDto?> = arrayOfNulls(size)
            }
    }
}
