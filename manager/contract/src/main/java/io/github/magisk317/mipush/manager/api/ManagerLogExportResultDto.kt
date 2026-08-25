package io.github.magisk317.mipush.manager.api

import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable

/**
 * Result of a runtime log-bundle export. The optional [parcelFileDescriptor] is opened read-only by
 * the runtime and transferred to the caller; the runtime does not retain ownership after write.
 */
data class ManagerLogExportResultDto(
    val schemaVersion: Int = ManagerProtocol.LOG_EXPORT_RESULT_SCHEMA_VERSION,
    val success: Boolean = false,
    val details: String = "",
    val parcelFileDescriptor: ParcelFileDescriptor? = null,
) : Parcelable {
    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeWireFrame {
            writeInt(schemaVersion)
            writeWireBoolean(success)
            writeString(details)
            writeWireNullableParcelable(parcelFileDescriptor, flags)
        }
    }

    override fun describeContents(): Int =
        parcelFileDescriptor?.describeContents() ?: 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ManagerLogExportResultDto> =
            object : Parcelable.Creator<ManagerLogExportResultDto> {
                override fun createFromParcel(source: Parcel): ManagerLogExportResultDto =
                    source.readWireFrame {
                        val schemaVersion = readInt(ManagerProtocol.LOG_EXPORT_RESULT_SCHEMA_VERSION)
                        val success = readBoolean()
                        val details = readString(
                            maxLength = ManagerProtocol.MAX_LOG_EXPORT_DETAILS_LENGTH,
                        ).orEmpty()
                        ManagerLogExportResultDto(
                            schemaVersion = schemaVersion,
                            success = success,
                            details = details,
                            parcelFileDescriptor = readNullableParcelable(ParcelFileDescriptor.CREATOR),
                        )
                    }

                override fun newArray(size: Int): Array<ManagerLogExportResultDto?> = arrayOfNulls(size)
            }
    }
}
