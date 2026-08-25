package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

/** Keyset page request for runtime events. [lastId] is exclusive upper bound when present. */
data class ManagerEventQueryDto(
    val schemaVersion: Int = ManagerProtocol.EVENT_QUERY_SCHEMA_VERSION,
    val lastId: Long? = null,
    val pageSize: Int = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
    val packageName: String = "",
    val query: String = "",
    val userId: Int = 0,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeWireNullableLong(lastId)
            writeInt(pageSize)
            writeString(packageName)
            writeString(query)
            writeInt(userId)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerEventQueryDto> =
            object : Parcelable.Creator<ManagerEventQueryDto> {
                override fun createFromParcel(source: Parcel): ManagerEventQueryDto = source.readWireFrame {
                    ManagerEventQueryDto(
                        schemaVersion = readInt(ManagerProtocol.EVENT_QUERY_SCHEMA_VERSION),
                        lastId = readNullableLong(),
                        pageSize = readInt(ManagerProtocol.DEFAULT_MAX_PAGE_SIZE),
                        packageName = readString(maxLength = ManagerProtocol.MAX_PACKAGE_NAME_LENGTH).orEmpty(),
                        query = readString(maxLength = ManagerProtocol.MAX_APPLICATION_QUERY_LENGTH).orEmpty(),
                        userId = readInt(-1),
                    )
                }

                override fun newArray(size: Int): Array<ManagerEventQueryDto?> = arrayOfNulls(size)
            }
    }
}
