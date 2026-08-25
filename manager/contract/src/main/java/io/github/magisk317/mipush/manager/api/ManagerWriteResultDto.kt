package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

data class ManagerWriteResultDto(
    val schemaVersion: Int = ManagerProtocol.WRITE_RESULT_SCHEMA_VERSION,
    val requestId: String = "",
    val status: String = ManagerProtocol.WRITE_STATUS_FAILED,
    val details: String = "",
    val resultLong: Long = 0L,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeString(requestId)
            writeString(status)
            writeString(details)
            writeLong(resultLong)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerWriteResultDto> =
            object : Parcelable.Creator<ManagerWriteResultDto> {
                override fun createFromParcel(source: Parcel): ManagerWriteResultDto =
                    source.readWireFrame {
                        ManagerWriteResultDto(
                            schemaVersion = readInt(ManagerProtocol.WRITE_RESULT_SCHEMA_VERSION),
                            requestId = readString(
                                maxLength = ManagerProtocol.MAX_WRITE_REQUEST_ID_LENGTH,
                            ).orEmpty(),
                            status = readString(maxLength = 32).orEmpty(),
                            details = readString(
                                maxLength = ManagerProtocol.MAX_LOG_EXPORT_DETAILS_LENGTH,
                            ).orEmpty(),
                            resultLong = readLong(),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerWriteResultDto?> = arrayOfNulls(size)
            }
    }
}
