package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.Parcelable

data class ManagerConfigurationUploadResultDto(
    val schemaVersion: Int = ManagerProtocol.CONFIGURATION_UPLOAD_RESULT_SCHEMA_VERSION,
    val success: Boolean = false,
    val activated: Boolean = false,
    val details: String = "",
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeWireBoolean(success)
            writeWireBoolean(activated)
            writeString(details)
        }
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerConfigurationUploadResultDto> =
            object : Parcelable.Creator<ManagerConfigurationUploadResultDto> {
                override fun createFromParcel(source: Parcel): ManagerConfigurationUploadResultDto =
                    source.readWireFrame {
                        ManagerConfigurationUploadResultDto(
                            schemaVersion = readInt(
                                ManagerProtocol.CONFIGURATION_UPLOAD_RESULT_SCHEMA_VERSION,
                            ),
                            success = readBoolean(),
                            activated = readBoolean(),
                            details = readString(
                                maxLength = ManagerProtocol.MAX_LOG_EXPORT_DETAILS_LENGTH,
                            ).orEmpty(),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerConfigurationUploadResultDto?> =
                    arrayOfNulls(size)
            }
    }
}
