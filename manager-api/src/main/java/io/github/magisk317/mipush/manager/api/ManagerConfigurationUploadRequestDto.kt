package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable

data class ManagerConfigurationUploadRequestDto(
    val schemaVersion: Int = ManagerProtocol.CONFIGURATION_UPLOAD_REQUEST_SCHEMA_VERSION,
    val path: String = "",
    val contentLength: Int = 0,
    val parcelFileDescriptor: ParcelFileDescriptor? = null,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeString(path)
            writeInt(contentLength)
            writeWireNullableParcelable(parcelFileDescriptor, flags)
        }
    }

    override fun describeContents(): Int = parcelFileDescriptor?.describeContents() ?: 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerConfigurationUploadRequestDto> =
            object : Parcelable.Creator<ManagerConfigurationUploadRequestDto> {
                override fun createFromParcel(source: Parcel): ManagerConfigurationUploadRequestDto =
                    source.readWireFrame {
                        ManagerConfigurationUploadRequestDto(
                            schemaVersion = readInt(
                                ManagerProtocol.CONFIGURATION_UPLOAD_REQUEST_SCHEMA_VERSION,
                            ),
                            path = readString(
                                maxLength = ManagerProtocol.MAX_CONFIGURATION_PATH_LENGTH,
                            ).orEmpty(),
                            contentLength = readInt(),
                            parcelFileDescriptor = readNullableParcelable(ParcelFileDescriptor.CREATOR),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerConfigurationUploadRequestDto?> =
                    arrayOfNulls(size)
            }
    }
}
