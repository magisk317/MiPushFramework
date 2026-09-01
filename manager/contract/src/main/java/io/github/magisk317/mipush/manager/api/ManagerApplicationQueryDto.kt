package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

/** [pageToken] is opaque and must only be returned to the runtime that issued it. */
data class ManagerApplicationQueryDto(
    val schemaVersion: Int = ManagerProtocol.APPLICATION_QUERY_SCHEMA_VERSION,
    val query: String = "",
    val filterMode: Int = 0,
    val includeSystemApps: Boolean = false,
    val pageSize: Int = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
    val pageToken: String? = null,
    val userId: Int,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeString(query)
            writeInt(filterMode)
            writeWireBoolean(includeSystemApps)
            writeInt(pageSize)
            writeString(pageToken)
            writeInt(userId)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerApplicationQueryDto> =
            object : Parcelable.Creator<ManagerApplicationQueryDto> {
                override fun createFromParcel(source: Parcel): ManagerApplicationQueryDto = source.readWireFrame {
                    ManagerApplicationQueryDto(
                        schemaVersion = readInt(ManagerProtocol.APPLICATION_QUERY_SCHEMA_VERSION),
                        query = readString(maxLength = ManagerProtocol.MAX_APPLICATION_QUERY_LENGTH).orEmpty(),
                        filterMode = readInt(),
                        includeSystemApps = readBoolean(),
                        pageSize = readInt(ManagerProtocol.DEFAULT_MAX_PAGE_SIZE),
                        pageToken = readString(maxLength = ManagerProtocol.MAX_PAGE_TOKEN_LENGTH),
                        userId = readInt(-1),
                    )
                }

                override fun newArray(size: Int): Array<ManagerApplicationQueryDto?> = arrayOfNulls(size)
            }
    }
}
